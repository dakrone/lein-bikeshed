(ns bikeshed.core
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
  [name]
  {(str name) (and (boolean (:doc (meta name)))
                   (not= "" (:doc (meta name))))})

(defn long-lines
  "Complain about lines longer than <max-line-length> characters.
  max-line-length defaults to 80."
  [all-dirs & {:keys [max-line-length] :or {max-line-length 80}}]
  (printf "\nChecking for lines longer than %s characters." max-line-length)
  (let [max-line-length (inc max-line-length)
        cmd (str "find " all-dirs " -name "
                 "'*.clj' | xargs egrep -H -n '^.{" max-line-length ",}$'")
        out (:out (clojure.java.shell/sh "bash" "-c" cmd))
        your-code-is-formatted-wrong (not (blank? out))]
    (if your-code-is-formatted-wrong
      (do (println "Badly formatted files:")
          (println (.trim out))
          true)
      (println "No lines found."))))

(defn trailing-whitespace
  "Complain about lines with trailing whitespace."
  [all-dirs]
  (println "\nChecking for lines with trailing whitespace.")
  (let [cmd (str "find " all-dirs " -name "
                 "'*.clj' | xargs grep -H -n '[ \t]$'")
        out (:out (clojure.java.shell/sh "bash" "-c" cmd))
        your-code-is-formatted-wrong (not (blank? out))]
    (if your-code-is-formatted-wrong
      (do (println "Badly formatted files:")
          (println (.trim out))
          true)
      (println "No lines found."))))

(defn trailing-blank-lines
  "Complain about files ending with blank lines."
  [all-dirs]
  (println "\nChecking for files ending in blank lines.")
  (let [cmd (str "find " all-dirs " -name '*.clj' "
                 "-exec tail -1 \\{\\} \\; -print "
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
  [source-dirs]
  (println "\nChecking for redefined var roots in source directories.")
  (let [cmd (str "find " source-dirs " -name \\*.clj | "
                 "xargs egrep -H -n '(\\(with-redefs)'")
        out (:out (clojure.java.shell/sh "bash" "-c" cmd))
        lines (line-seq (BufferedReader. (StringReader. out)))]
    (if (and lines (not= 0 (count lines)))
      (do (println "with-redefs found in source directory:")
          (doseq [line lines]
            (println line))
          true)
      (println "No with-redefs found."))))

(defn missing-doc-strings
  "Report the percentage of missing doc strings."
  [project verbose]
  (println "\nChecking whether you keep up with your docstrings.")
  (try
    (let [source-files (mapcat #(-> % io/file
                                    ns-find/find-clojure-sources-in-dir)
                              (:source-paths project))
         all-publics (mapcat read-namespace source-files)
         no-docstrings (->> all-publics
                            (mapcat has-doc)
                            (filter #(= (val %) false)))]
     (printf
      (str "%d/%d [%.2f%%] functions have docstrings.\n"
           (when (not verbose)
             "Use -v to list functions without docstrings\n"))
      (- (count all-publics) (count no-docstrings))
      (count all-publics)
      (double
       (* 100 (/ (- (count all-publics)
                    (count no-docstrings))
                 (count all-publics)))))
     (flush)
     (when verbose
       (println "\nMethods without docstrings:")
       (doseq [[method _] (sort no-docstrings)]
         (println method)))
     (-> no-docstrings count pos?))
    (catch Throwable t
      (println "Sorry, I wasn't able to read your source files -" t))))

(defn bikeshed
  "Bikesheds your project with totally arbitrary criteria. Returns true if the
  code has been bikeshedded and found wanting."
  [project & opts]
  (let [options (first opts)
        source-dirs (clojure.string/join " " (:source-paths project))
        test-dirs (clojure.string/join " " (:test-paths project))
        all-dirs (str source-dirs " " test-dirs)
        long-lines (if (nil? (:max-line-length options))
                     (long-lines all-dirs)
                     (long-lines all-dirs
                                 :max-line-length
                                 (Integer/parseInt (str (:max-line-length options)))))
        trailing-whitespace (trailing-whitespace all-dirs)
        trailing-blank-lines (trailing-blank-lines all-dirs)
        bad-roots (bad-roots source-dirs)
        bad-methods (missing-doc-strings project (:verbose options))]
    (or long-lines trailing-whitespace trailing-blank-lines bad-roots)))
