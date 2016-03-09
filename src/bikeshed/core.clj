(ns bikeshed.core
  "Define all the functionalities of bikeshed"
  (:require [clojure.string :refer [blank?]]
            [clojure.java.io :as io]
            [clojure.java.shell]
            [clojure.tools.namespace.file :as ns-file]
            [clojure.tools.namespace.find :as ns-find])
  (:import (java.io BufferedReader StringReader)))

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

(defn- get-all
  "Returns all the values found for the LOOKED-UP-KEY passed as an argument
  recursively walking the MAP-TO-TRAVERSE provided as argument"
  [map-to-traverse looked-up-key]
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

(def filename-regex
  "Gnu `find' regex for files that should be checked"
  "'*.clj*'")

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

(defn find-all-files [dirs]
  (->> (str "find " dirs " -name " filename-regex)
       (clojure.java.shell/sh "bash" "-c")
       (:out)
       (#(clojure.string/split % #"\n"))))

(defn find-git-checked-files []
  (-> (clojure.java.shell/sh "git" "ls-files" "--full-name")
      (:out)
      (clojure.string/split #"\n")))

(defn find-clojure-files [dirs only-git-checked?]
  (let [all-files (find-all-files dirs)]
    (if only-git-checked?
      (let [git-files (set (find-git-checked-files))]
        ; All files vector contains absolute file paths, git file vector
        ; contains paths relative to the git root.
        (->> (filter (fn [f] (some #(.endsWith f %) git-files)) all-files)
             (clojure.string/join " ")))
      (clojure.string/join " " all-files))))

(defn long-lines
  "Complain about lines longer than <max-line-length> characters.
  max-line-length defaults to 80."
  [all-files & {:keys [max-line-length] :or {max-line-length 80}}]
  (printf "\nChecking for lines longer than %s characters.\n" max-line-length)
  (let [max-line-length (inc max-line-length)
        cmd (str "echo " all-files
                 " | xargs egrep -H -n '^.{" max-line-length ",}$'")
        out (:out (clojure.java.shell/sh "bash" "-c" cmd))
        your-code-is-formatted-wrong (not (blank? out))]
    (if your-code-is-formatted-wrong
      (do (println "Badly formatted files:")
          (println (.trim out))
          true)
      (println "No lines found."))))

(defn trailing-whitespace
  "Complain about lines with trailing whitespace."
  [all-files]
  (println "\nChecking for lines with trailing whitespace.")
  (let [cmd (str "echo " all-files " | xargs grep -H -n '[ \t]$'")
        out (:out (clojure.java.shell/sh "bash" "-c" cmd))
        your-code-is-formatted-wrong (not (blank? out))]
    (if your-code-is-formatted-wrong
      (do (println "Badly formatted files:")
          (println (.trim out))
          true)
      (println "No lines found."))))

(defn trailing-blank-lines
  "Complain about files ending with blank lines."
  [all-files]
  (println "\nChecking for files ending in blank lines.")
  (let [cmd (str "files=" all-files "; "
                 "find $files -exec tail -1 \\{\\} \\; -print "
                 "| egrep -A 1 '^\\s*$' | egrep 'clj|sql'")
        out (:out (clojure.java.shell/sh "bash" "-c" cmd))
        your-code-is-formatted-wrong (not (blank? out))]
    (if your-code-is-formatted-wrong
      (do (println "Badly formatted files:")
          (println (.trim out))
          true)
      (println "No files found."))))

(defn bad-roots
  "Complain about the use of with-redefs."
  [source-files]
  (println "\nChecking for redefined var roots in source directories.")
  (let [cmd (str "echo " source-files " | "
                 "xargs egrep -H -n '(\\(with-redefs)'")
        out (:out (clojure.java.shell/sh "bash" "-c" cmd))
        lines (line-seq (BufferedReader. (StringReader. out)))]
    (if (and lines (not= 0 (count lines)))
      (do (println "with-redefs found in source directory:")
          (doseq [line lines]
            (println line))
          true)
      (println "No with-redefs found."))))

(defn find-clojure-sources-in-dirs
  [dirs only-git-checked?]
  (let [sources (mapcat #(-> % io/file ns-find/find-clojure-sources-in-dir)
                        dirs)]
    (if only-git-checked?
      (let [git-files (set (find-git-checked-files))]
        (filter (fn [f] (some #(.endsWith (.getPath f) %) git-files)) sources))
      sources)))

(defn missing-doc-strings
  "Report the percentage of missing doc strings."
  [project verbose only-git-checked?]
  (println "\nChecking whether you keep up with your docstrings.")
  (try
    (let [source-files (find-clojure-sources-in-dirs
                         (flatten (get-all project :source-paths))
                         only-git-checked?)
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
          (println ns-name)))
      (when verbose
        (println "\nMethods without docstrings:")
        (doseq [[method _] (sort no-docstrings)]
          (println method)))
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
  [project only-git-checked?]
  (println "\nChecking for arguments colliding with clojure.core functions.")
  (let [core-functions (-> 'clojure.core ns-publics keys)
        source-files   (find-clojure-sources-in-dirs
                         (flatten (get-all project :source-paths))
                         only-git-checked?)
        all-publics    (mapcat read-namespace source-files)]
    (->> all-publics
         (map (fn [function]
                (let [args (wrong-arguments function core-functions)]
                  (when (seq args)
                    (if (= 1 (count args))
                      (println (str function ": '" (first args) "'")
                               "is colliding with a core function")
                      (println (str function ": '"
                                    (clojure.string/join "', '" args) "'")
                               "are colliding with core functions")))
                  (count args))))
         (apply +)
         (pos?))))

(defn bikeshed
  "Bikesheds your project with totally arbitrary criteria. Returns true if the
  code has been bikeshedded and found wanting."
  [project & opts]
  (let [options (first opts)
        source-files (find-clojure-files
                       (clojure.string/join " "
                                            (flatten
                                              (get-all project :source-paths)))
                       (:only-git-checked? options))
        test-files (find-clojure-files
                     (clojure.string/join " " (:test-paths project))
                     (:only-git-checked? options))
        all-files (str source-files " " test-files)
        long-lines (if (nil? (:max-line-length options))
                     (long-lines all-files)
                     (long-lines all-files
                                 :max-line-length
                                 (:max-line-length options)))
        trailing-whitespace (trailing-whitespace all-files)
        trailing-blank-lines (trailing-blank-lines all-files)
        bad-roots (bad-roots source-files)
        bad-methods (missing-doc-strings project
                                         (:verbose options)
                                         (:only-git-checked? options))
        bad-arguments (check-all-arguments project
                                           (:only-git-checked? options))]
    (or bad-arguments
        long-lines
        trailing-whitespace
        trailing-blank-lines
        bad-roots)))
