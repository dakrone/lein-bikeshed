(ns leiningen.bikeshed
  (:use [clojure.tools.cli :only (cli)])
  (:require [leiningen.core.eval :as lein]))

; (defn bikeshed [project & args]
;   "Bikesheds your project with totally arbitrary criteria."
;   (lein/eval-in-project
;    (-> project
;        (update-in [:dependencies] conj ['lein-bikeshed "0.1.4-SNAPSHOT"]))
;    `(do 
;       (println ~@args)
;       (if (bikeshed.core/bikeshed '~project (= "-v" '~args))
;         (System/exit -1)
;         (System/exit 0)))
;    '(require 'bikeshed.core)))

(defn bikeshed [project & args]
  "Bikesheds your project with totally arbitrary criteria."
  (lein/eval-in-project
   (-> project
       (update-in [:dependencies] conj ['lein-bikeshed "0.1.4-SNAPSHOT"]))
   `(let [[opts# args# banner#]
      (cli '~args
        ; standard 'h' and 'help' arguments seems to be used before coming here.
        ; so using another couple for displaying help.
        ["-H" "--help-me" "Show help" :flag true :default false]
        ["-v" "--verbose" "Display missing doc strings" :flag true :default false]
        ["-m" "--max-line-length" "Max line length" :default nil])]
      '~project
      (when (:help-me opts#)
        (println banner#)
        (System/exit 0))
        (if (bikeshed.core/bikeshed '~project {:max-line-length (:max-line-length opts#) :verbose (:verbose opts#)})
          (System/exit -1)
          (System/exit 0)))
   '(require 'bikeshed.core)))