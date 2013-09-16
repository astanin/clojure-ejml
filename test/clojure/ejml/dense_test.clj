(ns clojure.ejml.dense-test
  (:use clojure.test
        clojure.core.matrix)
  (:require [clojure.ejml.dense :as dense]
            [clojure.core.matrix.compliance-tester :as matrix-api-tests]))


(deftest test-core-matrix-compliance
   (let [m (matrix :ejml-dense-64bit [[1 2] [3 4]])]
     (matrix-api-tests/compliance-test m)))
