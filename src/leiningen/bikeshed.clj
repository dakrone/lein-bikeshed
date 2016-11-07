(ns leiningen.bikeshed
  (:require [clojure.tools.cli :as cli]
            [leiningen.core.eval :as lein]))

(defn help
  "Help text displayed from the command line"
  []
  "Bikesheds your project with totally arbitrary criteria.")

(defn bikeshed
  "Main function called from Leiningen"
  [project & args]
  (let [[opts args banner]
        (cli/cli
         args
         ["-H" "--help-me" "Show help"
          :flag true :default false]
         ["-v" "--verbose" "Display missing doc strings"
          :flag true :default false]
         ["-m" "--max-line-length" "Max line length"
          :default nil
          :parse-fn #(Integer/parseInt %)])]
    (if (:help-me opts)
      (println banner)
      (lein/eval-in-project
       (-> project
           (update-in [:dependencies]
                      conj
                      ['lein-bikeshed "0.3.1-SNAPSHOT"]))
       `(if (bikeshed.core/bikeshed
             '~project
             (select-keys ~opts [:max-line-length :verbose]))
          (System/exit -1)
          (System/exit 0))
       '(require 'bikeshed.core)))))
