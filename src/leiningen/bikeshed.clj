(ns leiningen.bikeshed
  (:require [leiningen.core.eval :as lein]))

(defn bikeshed [project & [arg]]
  "Bikesheds your project with totally arbitrary criteria."
  (lein/eval-in-project
   (-> project
       (update-in [:dependencies] conj ['lein-bikeshed "0.1.2-SNAPSHOT"]))
   `(if (bikeshed.core/bikeshed '~project (= "-v" '~arg))
      (System/exit -1)
      (System/exit 0))
   '(require 'bikeshed.core)))
