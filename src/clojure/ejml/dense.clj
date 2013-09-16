(ns clojure.ejml.dense
  "Implementation of core.matrix and linear algebra API for EJML DenseMatrix64F."
  (:require [clojure.core.matrix :as m-api]
            [clojure.core.matrix.protocols :as mp]
            [clojure.core.matrix.implementations :as mpi])
  (:import [org.ejml.data Matrix64F DenseMatrix64F]
           [org.ejml.ops CommonOps]))


(defn- alloc-DenseMatrix64F
  ([^DenseMatrix64F m]
     (DenseMatrix64F. (.getNumRows m) (.getNumCols m)))
  ([rows cols]
     (DenseMatrix64F. rows cols)))


(defn ejml-shape
  [^DenseMatrix64F m]
  [(.getNumRows m) (.getNumCols m)])


(defn ejml-1d?
  "Returns true if M is a one-dimensional row (column) vector."
  [^DenseMatrix64F m]
  (or (= 1 (.numRows m)) (= 1 (.numCols m))))


(defn new-ejml-matrix
  "Creates a new EJML DenseMatrix64F."
  [num-rows num-cols]
  (DenseMatrix64F. num-rows num-cols))


(defn to-ejml-matrix
  "Converts a sequences of sequences to a new EJML DenseMatrix64F."
  ([seq-of-seqs]
     (if (instance? DenseMatrix64F seq-of-seqs)  ; safe to call (to-matrix (to-matrix ..))
       seq-of-seqs
       (let [^"[[D" arr2d (into-array (map double-array seq-of-seqs))]
        (DenseMatrix64F. arr2d)))))


(defn from-ejml-matrix
  "Converts an EJML DenseMatrix64F to a Clojure vector or vectors."
  [^DenseMatrix64F m]
  (if (ejml-1d? m)
    (into [] (.getData m))  ; 1D matrix to a simple vector
    (mapv vec (partition (.numCols m) (.getData m))) ; 2D matrix to vector of vectors
    ))


(extend-type DenseMatrix64F

  mp/PImplementation
  (implementation-key [m] :ejml-dense-64bit)
  (meta-info [m] {:doc "org.ejml.data.DenseMatrix64F"})
  (construct-matrix [m data] (to-ejml-matrix data))
  (new-vector [m length] (new-ejml-matrix length 1))
  (new-matrix [m rows cols] (new-ejml-matrix rows cols))
  (new-matrix-nd [m shape] (let [[rows cols] shape] (new-ejml-matrix rows cols)))
  (supports-dimensionality? [m dimensions] (<= 1 dimensions 2))

  mp/PDimensionInfo
  (dimensionality [m] 2)
  (get-shape [m] (ejml-shape m))
  (is-scalar? [m] false)
  (is-vector? [m] false)
  (dimension-count [m dimension-number] (nth (ejml-shape m) dimension-number))

  mp/PIndexedAccess
  (get-1d [m index] (nth (.data m) (int index)))
  (get-2d [m row col] (.get m (int row) (int col)))
  (get-nd [m indices] (let [[row col] indices] (.get m (int row) (int col))))

  mp/PIndexedSetting
  (set-1d [m index val]
    (let [mcopy (.copy m)]
      (aset-double (.data mcopy) (int index) (double val))
      mcopy))
  (set-2d [m row col val]
    (let [mcopy (.copy m)]
      (.set mcopy (int row) (int col) (double val))))
  (set-nd [m indices val]
    (let [[row col] indices
          mcopy (.copy m)]
      (.set mcopy (int row) (int col) (double val))))
  (is-mutable? [m] true)

  mp/PIndexedSettingMutable
  (set-1d! [m index val]
    (aset-double (.data m) (int index) (double val)))
  (set-2d! [m row col val]
    (.set m (int row) (int col) (double val)))
  (set-nd! [m indices val]
    (let [[row col] indices]
      (.set m (int row) (int col) (double val))))

  mp/PMatrixCloning
  (clone [m] (.copy m))

  ;;; Optional protocols

  mp/PTypeInfo
  (element-type [m] (Double/TYPE))

  mp/PMutableMatrixConstruction
  (mutable-matrix [m] (.copy m))

  ;; not implementing PZeroDimensionConstruction, PZeroDimensionAccess, PZeroDimensionSet

  mp/PSpecialisedConstructors
  (identity-matrix [m dims]
    (let [[^int rows ^int cols] dims]
      (CommonOps/identity rows cols)))
  (diagonal-matrix [m diagonal-values]
    (CommonOps/diag (double-array diagonal-values)))

  )


(mpi/register-implementation (new-ejml-matrix 2 2))
