(ns ejml-dense-test
  (:use clojure.test
        clojure.core.matrix)
  (:require [clojure.core.matrix.impl.ejml :as ejml]
            [clojure.core.matrix.compliance-tester :as matrix-api-tests]))


(deftest test-core-matrix-compliance
   (let [m (matrix :ejml [[1 2] [3 4]])]
     (matrix-api-tests/compliance-test m)))
