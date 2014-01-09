(defproject clojure-ejml "0.18.0.1-SNAPSHOT"  ;; works with core.matrix 0.18.0
  :description "Efficient Java Matrix Library now for Clojure"
  :url "http://github.com/astanin/clojure-ejml"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.googlecode.efficient-java-matrix-library/ejml "0.24"]  ;; Apache 2.0
                 [net.mikera/core.matrix "0.18.0"]  ;; EPL 1.0
                 ]
  :profiles {:dev {:plugins [[codox "0.6.6"]  ; lein with-profile dev doc
                             ]
                   :dependencies [[org.clojure/tools.nrepl "0.2.3"]]}})
