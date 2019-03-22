(defproject lein-bikeshed "0.5.3-SNAPSHOT"
  :description (str "A Leiningen plugin designed to tell you your code is bad, "
                    "and that you should feel bad")
  :url "https://github.com/dakrone/lein-bikeshed"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :eval-in :leiningen
  :dependencies [[org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.namespace "0.2.6"]])
