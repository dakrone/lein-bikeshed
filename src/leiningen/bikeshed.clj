(ns leiningen.bikeshed
  (:require [leiningen.core.eval :as lein]))

(defn help
  "Help text displayed from the command line"
  []
  "Bikesheds your project with totally arbitrary criteria.")

(defn bikeshed
  "Main function called from Leiningen"
  [project & args]
  (lein/eval-in-project
   (-> project
       (update-in [:dependencies] conj ['lein-bikeshed "0.3.1-SNAPSHOT"]))
   `(let [[opts# args# banner#]
          (clojure.tools.cli/cli
           '~args
           ["-H" "--help-me" "Show help"
            :flag true :default false]
           ["-v" "--verbose" "Display missing doc strings"
            :flag true :default false]
           ["-m" "--max-line-length" "Max line length"
            :default nil
            :parse-fn #(Integer/parseInt %)])
          lein-opts# (:bikeshed '~project)]
      '~project
      (when (:help-me opts#)
        (println banner#)
        (System/exit 0))
      (if (bikeshed.core/bikeshed
           '~project {:max-line-length (or (:max-line-length opts#)
                                           (:max-line-length lein-opts#))
                      :verbose (:verbose opts#)})
        (System/exit -1)
        (System/exit 0)))
   '(do
      (require 'bikeshed.core)
      (require 'clojure.tools.cli))))
