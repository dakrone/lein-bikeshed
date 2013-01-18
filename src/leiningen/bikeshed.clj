(ns leiningen.bikeshed
  (:require [clojure.string :refer [blank?]]
            [clojure.java.shell]
            [leiningen.core.eval :as lein])
  (:import (java.io BufferedReader StringReader)))

(defn bikeshed
  "Bikesheds your project with totally arbitrary criteria."
  [project]
  (let [source-dirs (clojure.string/join " " (:source-paths project))
        test-dirs (clojure.string/join " " (:test-paths project))
        all-dirs (str source-dirs " " test-dirs)
        _ (println "Checking for lines longer than 80 characters.")
        bad-lines (let [cmd (str "find " all-dirs " -name "
                                 "'*.clj' | xargs egrep -H -n '^.{81,}$'")
                        out (:out (clojure.java.shell/sh "bash" "-c" cmd))
                        your-code-is-formatted-wrong (not (blank? out))]
                    (if your-code-is-formatted-wrong
                      (do (println "Badly formatted files:")
                          (println (.trim out))
                          true)
                      (println "No lines found.")))
        _ (println "\nChecking for files ending in blank lines.")
        bad-files (let [cmd (str "find " all-dirs " -name '*.clj' "
                                 "-exec tail -1 \\{\\} \\; -print "
                                 "| egrep -A 1 '^\\s*$' | egrep 'clj|sql'")
                        out (:out (clojure.java.shell/sh "bash" "-c" cmd))
                        your-code-is-formatted-wrong (not (blank? out))]
                    (if your-code-is-formatted-wrong
                      (do (println "Badly formatted files:")
                          (println (.trim out))
                          true)
                      (println "No files found.")))
        _ (println "\nChecking for redefined var roots in source directories.")
        bad-roots (let [cmd (str "find " source-dirs " -name \\*.clj | "
                                 "xargs egrep -H -n '(\\(with-redefs)'")
                        out (:out (clojure.java.shell/sh "bash" "-c" cmd))
                        lines (line-seq (BufferedReader. (StringReader. out)))]
                    (if (and lines (not= 0 (count lines)))
                      (do (println "with-redefs found in source directory:")
                          (doseq [line lines]
                            (println line))
                          true)
                      (println "No with-redefs found.")))]
    (when (or bad-lines bad-files bad-roots)
      (leiningen.core.main/abort))))
