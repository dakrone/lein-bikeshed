(ns bikeshed.core
  "Define all the functionalities of bikeshed"
  (:require [clojure.string :refer [blank? starts-with? trim join]]
            [clojure.java.io :as io]
            [clojure.tools.namespace.file :as ns-file]
            [clojure.tools.namespace.find :as ns-find]
            [clojure.string :as str])
  (:import (java.io BufferedReader StringReader File)))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(defn bad-fn
  "this is a bad function."
  []
  (with-redefs [+ -]
    (+ 2 2)))

(defn no-docstring
  []
  nil)

(defn empty-docstring
  "" ;; hah! take that lein-bikeshed
  []
  nil)

(defn colliding-arguments
  "Arguments will be colliding"
  ([map])
  ([map first]))

(defn add-issue [details issue]
  (swap! details conj issue))

(defn file-with-extension?
  "Returns true if the java.io.File represents a file whose name ends
  with one of the Strings in extensions."
  [^java.io.File file extensions]
  (and (.isFile file)
       (let [name (.getName file)]
         (some #(.endsWith name %) extensions))))

(defn- sort-files-breadth-first
  [files]
  (sort-by #(.getAbsolutePath ^File %) files))

(defn find-sources-in-dir
  "Searches recursively under dir for source files. Returns a sequence
  of File objects, in breadth-first sort order.
  Optional second argument is either clj (default) or cljs, both
  defined in clojure.tools.namespace.find."
  ([dir]
   (find-sources-in-dir dir nil))
  ([^File dir extensions]
   (let [extensions (or extensions [".clj" ".cljc"])]
     (->> (file-seq dir)
          (filter #(file-with-extension? % extensions))
          sort-files-breadth-first))))

(defn- get-all
  "Returns all the values found for the LOOKED-UP-KEY passed as an argument
  recursively walking the MAP-TO-TRAVERSE provided as argument"
  ([map-to-traverse looked-up-key]
   (let [result (atom [])]
     (doseq [[k v] map-to-traverse]
       (when (= looked-up-key k)
         (swap! result conj v))
       (when (map? v)
         (let [sub-map (get-all v looked-up-key)]
           (when-not (empty? sub-map)
             (reset! result
                     (apply conj @result sub-map))))))
     @result))
  ([map-to-traverse k & ks]
   (mapcat (partial get-all map-to-traverse) (cons k ks))))

(defn load-namespace
  "Reads a file, returning the namespace name"
  [f]
  (try
    (let [ns-dec (ns-file/read-file-ns-decl f)
          ns-name (second ns-dec)]
      (require ns-name)
      ns-name)
    (catch Exception e
      (println (str "Unable to parse " f ": " e))
      nil)))

(defn read-namespace
  "Reads a file, returning a map of the namespace to a vector of maps with
  information about each var in the namespace."
  [f]
  (try
    (let [ns-dec (ns-file/read-file-ns-decl f)
          ns-name (second ns-dec)]
      (require ns-name)
      (->> ns-name
           ns-interns
           vals))
    (catch Exception e
      (println (str "Unable to parse " f ": " e))
      [])))

(defn has-doc
  "Returns a map of method name to true/false depending on docstring occurance."
  [function-name]
  {(str function-name) (and (boolean (:doc (meta function-name)))
                            (not= "" (:doc (meta function-name))))})

(defn has-ns-doc
  "Returns a map of namespace to true/false depending on docstring occurance."
  [namespace-name]
  (let [doc (:doc (meta (the-ns (symbol (str namespace-name)))))]
    {(str namespace-name) (and (boolean doc)
                               (not= "" doc))}))

(defn- format-issue [issue]
  (trim (str (:file issue) ":" (:line issue) ":" (:content issue))))

(defn long-lines
  "Complain about lines longer than <max-line-length> characters.
  max-line-length defaults to 80."
  [details source-files & {:keys [max-line-length] :or {max-line-length 80}}]
  (printf "\nChecking for lines longer than %s characters.\n" max-line-length)
  (let [indexed-lines (fn [f]
                        (with-open [r (io/reader f)]
                          (doall
                           (keep-indexed
                            (fn [idx line]
                              (when (> (count line) max-line-length)
                                {:file (.getAbsolutePath f)
                                 :line (inc idx)
                                 :content line}))
                            (line-seq r)))))
        all-long-lines (flatten (map indexed-lines source-files))]
    (if (empty? all-long-lines)
      (println "No lines found.")
      (do
        (println "Badly formatted files:")
        (println (join "\n" (mapv format-issue all-long-lines)))
        (add-issue details {:description (str "Line length exceeds "
                                               max-line-length)
                            :type :long-lines
                            :issues all-long-lines})
        true))))

(defn trailing-whitespace
  "Complain about lines with trailing whitespace."
  [details source-files]
  (println "\nChecking for lines with trailing whitespace.")
  (let [indexed-lines (fn [f]
                        (with-open [r (io/reader f)]
                          (doall
                           (keep-indexed
                            (fn [idx line]
                              (when (re-seq #"\s+$" line)
                                {:file (.getAbsolutePath f)
                                 :line (inc idx)
                                 :content line}))
                            (line-seq r)))))
        trailing-whitespace-lines (flatten (map indexed-lines source-files))]
    (if (empty? trailing-whitespace-lines)
      (println "No lines found.")
      (do (println "Badly formatted files:")
          (println (join "\n" (mapv format-issue trailing-whitespace-lines)))
          (add-issue details {:description "Remove trailing whitespace lines"
                              :type :trailing-whitespace
                              :issues trailing-whitespace-lines})
          true))))

(defn trailing-blank-lines
  "Complain about files ending with blank lines."
  [details source-files]
  (println "\nChecking for files ending in blank lines.")
  (let [get-last-line (fn [f]
                        (with-open [r (io/reader f)]
                          (when (re-matches #"^\s*$" (last (line-seq r)))
                            {:file (.getAbsolutePath f)})))
        bad-files (filter some? (map get-last-line source-files))]
    (if (empty? bad-files)
      (println "No files found.")
      (do (println "Badly formatted files:")
          (println (join "\n" (mapv #(trim (:file %)) bad-files)))
          (add-issue details {:description "Remove blank line at the end"
                              :type :trailing-blank-lines
                              :issues bad-files})
          true))))

(defn bad-roots
  "Complain about the use of with-redefs."
  [details source-files]
  (println "\nChecking for redefined var roots in source directories.")
  (let [indexed-lines (fn [f]
                        (with-open [r (io/reader f)]
                          (doall
                           (keep-indexed
                            (fn [idx line]
                              (when (re-seq #"\(with-redefs" line)
                                {:file (.getAbsolutePath f)
                                 :line (inc idx)
                                 :content line}))
                            (line-seq r)))))
        bad-lines (flatten (map indexed-lines source-files))]
    (if (empty? bad-lines)
      (println "No with-redefs found.")
      (do (println "with-redefs found in source directory:")
          (println (join "\n" (mapv format-issue bad-lines)))
          (add-issue details {:description "Found with-redefs calls"
                              :type :var-redefs
                              :issues bad-lines})
          true))))

(defn missing-doc-strings
  "Report the percentage of missing doc strings."
  [details project verbose]
  (println "\nChecking whether you keep up with your docstrings.")
  (try
    (let [issues (atom [])
          source-files (mapcat #(-> % io/file
                                    ns-find/find-clojure-sources-in-dir)
                               (flatten (get-all project :source-paths)))
          all-namespaces (->> source-files
                              (map load-namespace)
                              (remove nil?))
          all-publics (mapcat read-namespace source-files)
          no-docstrings (->> all-publics
                             (mapcat has-doc)
                             (filter #(= (val %) false)))
          no-ns-doc (->> all-namespaces
                         (mapcat has-ns-doc)
                         (filter #(= (val %) false)))]
      (printf
       "%d/%d [%.2f%%] namespaces have docstrings.\n"
       (- (count all-namespaces) (count no-ns-doc))
       (count all-namespaces)
       (try
         (double
          (* 100 (/ (- (count all-namespaces)
                       (count no-ns-doc))
                    (count all-namespaces))))
         (catch ArithmeticException _ Double/NaN)))
      (printf
       (str "%d/%d [%.2f%%] functions have docstrings.\n"
            (when (not verbose)
              "Use -v to list namespaces/functions without docstrings\n"))
       (- (count all-publics) (count no-docstrings))
       (count all-publics)
       (try
         (double
          (* 100 (/ (- (count all-publics)
                       (count no-docstrings))
                    (count all-publics))))
         (catch ArithmeticException _ Double/NaN)))
      (flush)
      (when verbose
        (println "\nNamespaces without docstrings:")
        (doseq [[ns-name _] (sort no-ns-doc)]
          (println ns-name)
          (swap! issues conj {:file " "
                              :content (str "Namespace without docstrings: "
                                             ns-name)})))
      (when verbose
        (println "\nMethods without docstrings:")
        (doseq [[method _] (sort no-docstrings)]
          (println method)
          (swap! issues conj {:file " "
                              :content (str "Method without docstrings: "
                                             method)})))
      (add-issue details {:description "No docstrings"
                          :type :docstrings
                          :issues @issues})
      (or (-> no-docstrings count pos?)
          (-> no-ns-doc count pos?)))
    (catch Throwable t
      (println "Sorry, I wasn't able to read your source files -" t))))

(defn- wrong-arguments
  "Return the list of wrong arguments for the provided function name"
  [function-name list-of-forbidden-arguments]
  (let [arguments (-> function-name meta :arglists)]
    (distinct (flatten (map (fn [args]
                              (filter #(some (set [%])
                                             list-of-forbidden-arguments)
                                      args))
                            arguments)))))

(defn- check-all-arguments
  "Check if the arguments for functions collide
  with function from clojure/core"
  [collisions project]
  (println "\nChecking for arguments colliding with clojure.core functions.")
  (let [core-functions (-> 'clojure.core ns-publics keys)
        source-files   (mapcat #(-> % io/file
                                    ns-find/find-clojure-sources-in-dir)
                               (flatten (get-all project :source-paths)))
        all-publics   (mapcat read-namespace source-files)
        add-collision (fn [func args]
                        (let [join-args #(str (clojure.string/join "' '" %) "'")]
                          (swap! collisions conj {:file func
                                                  :content (join-args args)})))]
    (->> all-publics
         (map (fn [function]
                (let [args (wrong-arguments function core-functions)]
                  (when (seq args)
                    (do
                      (if (= 1 (count args))
                        (println (str function ": '" (first args) "'")
                                 "is colliding with a core function")
                        (println (str function ": '"
                                      (clojure.string/join "', '" args) "'")
                                 "are colliding with core functions"))
                      (add-collision function args)))
                  (count args))))
         (apply +)
         (pos?))))

(defn check-name-collisions [details project]
  (let [collisions (atom [])
        result (check-all-arguments collisions project)]
    (add-issue details {:description "Arguments colliding with core functions"
                        :type :name-collisions
                        :issues @collisions})
    result))

(defn visible-project-files
  "Given a project and list of keys (such as `:source-paths` or `:test-paths`,
  return all source files underneath those directories."
  [project & paths-keys]
  (remove
   #(starts-with? (.getName %) ".")
   (mapcat
    #(-> % io/file
         (find-sources-in-dir [".clj" ".cljs" ".cljc" ".cljx"]))
    (flatten (apply get-all project paths-keys)))))

(defn bikeshed
  "Bikesheds your project with totally arbitrary criteria. Returns true if the
  code has been bikeshedded and found wanting."
  [project {:keys [check? verbose max-line-length]}]
  (let [details (atom [])
        all-files (visible-project-files project :source-paths :test-paths)
        source-files (visible-project-files project :source-paths)
        results {:long-lines     (when (check? :long-lines)
                                   (if max-line-length
                                     (long-lines details all-files
                                                 :max-line-length max-line-length)
                                     (long-lines details all-files)))
                 :trailing-whitespace  (when (check? :trailing-whitespace)
                                         (trailing-whitespace details all-files))
                 :trailing-blank-lines (when (check? :trailing-blank-lines)
                                         (trailing-blank-lines details all-files))
                 :var-redefs      (when (check? :var-redefs)
                                    (bad-roots details source-files))
                 :bad-methods     (when (check? :docstrings)
                                    (missing-doc-strings details project verbose))
                 :name-collisions (when (check? :name-collisions)
                                    (check-name-collisions details project))}
        failures (->> results
                      (filter second)
                      (map first)
                      (remove #{:bad-methods})
                      (map name))]
    (if (empty? failures)
      (println "\nSuccess")
      (println "\nThe following checks failed:\n *"
               (str/join "\n * " failures)
               "\n"))
    {:failures failures :details @details}))
