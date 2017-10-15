(ns leiningen.bikeshed
  (:require [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [leiningen.core.eval :as lein]
            [leiningen.core.project :as project]))

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
          :parse-fn #(Integer/parseInt %)]
         ["-l" "--long-lines"
          "If true, check for trailing blank lines"
          :parse-fn #(Boolean/valueOf %)]
         ["-w" "--trailing-whitespace"
          "If true, check for trailing whitespace"
          :parse-fn #(Boolean/valueOf %)]
         ["-b" "--trailing-blank-lines"
          "If true, check for trailing blank lines"
          :parse-fn #(Boolean/valueOf %)]
         ["-r" "--var-redefs"
          "If true, check for redefined var roots in source directories"
          :parse-fn #(Boolean/valueOf %)]
         ["-d" "--docstrings"
          "If true, generate a report of docstring coverage"
          :parse-fn #(Boolean/valueOf %)]
         ["-n" "--name-collisions"
          "If true, check for function arg names that collide with clojure.core"
          :parse-fn #(Boolean/valueOf %)]
         ["-x" "--exclude-profiles" "Comma-separated profile exclusions"
          :default nil
          :parse-fn #(mapv keyword (str/split % #","))])
        lein-opts (:bikeshed project)
        project (if-let [exclusions (seq (:exclude-profiles opts))]
                  (-> project
                      (project/unmerge-profiles exclusions)
                      (update-in [:profiles] #(apply dissoc % exclusions)))
                  project)]
    (if (:help-me opts)
      (println banner)
      (lein/eval-in-project
       (-> project
           (update-in [:dependencies]
                      conj
                      ['lein-bikeshed "0.5.0"]))
       `(if (bikeshed.core/bikeshed
             '~project
             {:max-line-length (or (:max-line-length ~opts)
                                   (:max-line-length ~lein-opts))
              :verbose         (:verbose ~opts)
              :check?          #(get (merge ~lein-opts ~opts) % true)})
          (System/exit -1)
          (System/exit 0))
       '(require 'bikeshed.core)))))
