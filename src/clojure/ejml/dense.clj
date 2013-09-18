(ns clojure.ejml.dense
  "Implementation of core.matrix and linear algebra API for EJML DenseMatrix64F."
  (:require [clojure.core.matrix :as api]
            [clojure.core.matrix.protocols :as mp]
            [clojure.core.matrix.implementations :as mpi])
  (:import [org.ejml.data Matrix64F DenseMatrix64F MatrixIterator D1Submatrix64F]
           [org.ejml.ops CommonOps]))


;;; TODO: implement matrix-api for D1Submatrix64F and SimpleMatrix too

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


(defn ejml-submatrix-seq
  "Returns a row-major sequence of submatrix' elements of m."
  [m row-or-range column-or-range]
  (let [row-range         (if (sequential? row-or-range) row-or-range [row-or-range])
        [min-row max-row] (apply (juxt min max) row-range)
        column-range      (if (sequential? column-or-range) column-or-range [column-or-range])
        [min-col max-col] (apply (juxt min max) column-range)
        m-iter            (MatrixIterator. m true min-row min-col max-row max-col)
        n                 (* (count row-range) (count column-range))]
    (for [_ row-range _ column-range]
      (.next m-iter))))


(defmacro arg-error
  [& message-parts]
  `(throw (IllegalArgumentException. (str ~@message-parts))))


(defmacro unsupported-error
  [& message-parts]
  `(throw (UnsupportedOperationException. (str ~@message-parts))))


(defmacro do-multiply-or-scale
  "Wraps different multiplication procedures with a fallback to
  scalar scaling if the second argument is a scalar, and throwing
  an exception when the operation is not possible.

  Usage: (do-multiply-or-scale m a
           :when (compatible-shapes? m a)
           ;;; do something
           :else \"error message\")
  "
  [mvar avar when-keyword doable? & body]
  (let [m# mvar a# avar doable?# doable? body# body
        [mult-body# err-body#] (split-with (fn [form] (not= :else form)) body#)]
    (assert (= :when when-keyword))
    (concat
     `(cond
       (api/scalar? ~a#) (api/scale ~m# ~a#)
       ~doable?#         (do ~@mult-body#))
     (when (seq err-body#)
       `(:else           (do ~@(drop 1 err-body#)))))))


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

  ;; Unfortunately, it's impossible to implement clojure.lang.Seqable
  ;; for a Java class without a wrapper.

  ;;;
  ;;; Optional protocols
  ;;;

  mp/PTypeInfo
  (element-type [m] (Double/TYPE))

  mp/PMutableMatrixConstruction
  (mutable-matrix [m] (.copy m))

  ;; Not implementing zero-dimension (scalar as a matrix).

  ;; mp/PZeroDimensionConstruction
  ;; (new-scalar-array [m] [m value])

  ;; mp/PZeroDimensionAccess
  ;; (get-0d [m])
  ;; (set-0d! [m v])

  ;; mp/PZeroDimensionSet
  ;; (set-0d [m v])

  mp/PSpecialisedConstructors
  (identity-matrix [m dims]
    (let [[^int rows ^int cols] dims]
      (CommonOps/identity rows cols)))
  (diagonal-matrix [m diagonal-values]
    (CommonOps/diag (double-array diagonal-values)))

  mp/PCoercion
  (coerce-param [m p]
    (if (api/scalar? p)
      (double p)
      (condp = (api/dimensionality p)
        ;; create a column-vector from 1D params
        1 (->> p api/to-nested-vectors (mapv vector) to-ejml-matrix)
        ;; create a normal matrix otherwise
        2 (->> p api/to-nested-vectors to-ejml-matrix)
        (arg-error "EJML supports only 2D matrices, but params' shape is " (api/shape p)))))

  ;; TODO: implement broadcasting
  ;; mp/PBroadcast
  ;; (broadcast [m target-shape])

  ;; mp/BroadcastLike
  ;; (broadcast-like [m a])

  mp/PConversion
  (convert-to-nested-vectors [m]
    (let [[rows cols] (map range (api/shape m))]
      (vec (for [r rows]
             (vec (for [c cols]
                    (api/mget m r c)))))))

  mp/PReshaping
  (reshape [m new-shape]
    ;; EJML can pad with zeros, but PReshaping/reshape should throw an exception
    (when (> (apply * new-shape) (apply * (api/shape m)))
      (arg-error "new shape " new-shape
                 " requires more elements than the original shape " (api/shape m)))
    ;; or should return a new mutable copy
    (let [[rows cols] new-shape
          mcopy (api/clone m)]
      (.reshape mcopy rows cols true)
      mcopy))

  ;; TODO: using D1Submatrix64F might help to avoid constructing new
  ;; sequences/vectors, but we need to implement matrix-api for
  ;; D1Submatrix64F first
  ;;
  ;; For future references:
  ;;
  ;;   (D1Submatrix. ...)  ; refers to the original matrix data
  ;;   (.extract (D1Submatrix. ...))  ; copies data into SimpleMatrix
  ;;
  mp/PMatrixSlices
  (get-row [m i]
    (let [[_ cols] (api/shape m)]
      (ejml-submatrix-seq m i (range cols))))
  (get-column [m i]
    (let [[rows _] (api/shape m)]
      (ejml-submatrix-seq m (range rows) i)))
  (get-major-slice [m i]
    (mp/get-row m i))
  (get-slice [m dimension i]
    (condp = dimension
      0 (mp/get-row m i)
      1 (mp/get-column m i)
      (unsupported-error "EJML supports only 2D matrices")))

  ;; Specs: "Must return a mutable slice view". Mutating the original matrix?
  ;; TODO: return a D1Submatrix64F?
  mp/PSubVector
  (subvector [m start length]
    (let [[rows cols] (api/shape m)
          is-column?  (= 1 cols)
          is-row?     (= 1 rows)]
      (cond
       is-column? (ejml-submatrix-seq m (range start (+ start length)) 0)
       is-row?    (ejml-submatrix-seq m 0 (range start (+ start length)))
       :else      (arg-error "subvector of a matrix is undefined"))))


  ;; Specs: "Must return a mutable slice view". Mutating the original matrix?
  ;; TODO: return a D1Submatrix64F?
  mp/PSliceView
  (get-major-slice-view [m i]
    (mp/get-row m i))

  mp/PSliceSeq
  (get-major-slice-seq [m]
    (for [row (range (second (api/shape m)))]
      (api/get-row m row)))


  ;; TODO: suggest a variadic (join) in matrix-api to avoid multiple allocations
  mp/PSliceJoin
  (join [m a]
    (let [m-shape (api/shape m)
          a-shape (api/shape a)]
      (cond
       (= (rest m-shape) a-shape)  ;; joining with a 1D row vector
       (let [m2 (api/clone m)
             arr2 (double-array (concat (.data m2) (api/eseq a)))
             [mrows mcols] m-shape]
         (.setData m2 arr2)
         (.reshape m2 (+ 1 mrows) mcols true)
         m2)
       (= (rest m-shape) (rest a-shape))  ;; joining rows
       (let [m2 (api/clone m)
             arr2 (double-array (concat (.data m2) (api/eseq a)))
             [mrows mcols] m-shape
             [arows _]     a-shape]
         (.setData m2 arr2)
         (.reshape m2 (+ mrows arows) mcols true)
         m2)
       :else (arg-error "joining matrices of incompatible shape: "
                        m-shape " and " a-shape))))

  mp/PMatrixSubComponents
  (main-diagonal [m]
    (let [[r c] (api/shape m)
          d (new-ejml-matrix (min r c) 1)]
      (CommonOps/extractDiag m d)
      d))

  ;; TODO: understand what assign-array! should do and implement it too
  mp/PAssignment
  (assign! [m v]
    (if (api/scalar? v)
      (mp/fill! m v)
      (unsupported-error "non-scalar assignment")))

  mp/PMutableFill
  (fill! [m v]
    (CommonOps/fill m (double v))
    m)

  mp/PDoubleArrayOutput
  (to-double-array [m]
    (.data m))
  (as-double-array [m]
    (.data m))

  mp/PMatrixEquality
  (matrix-equals [a b]
    (let [b       (api/coerce a b)
          a-shape (api/shape a)
          b-shape (api/shape b)
          a-seq   (ejml-submatrix-seq a (range (first a-shape)) (range (second a-shape)))
          b-seq   (ejml-submatrix-seq b (range (first b-shape)) (range (second b-shape)))]
      (and (= a-shape b-shape)
           (every? #(apply == %) (map list a-seq b-seq)))))


  mp/PMatrixMultiply
  (matrix-multiply [m a]
    (let [a (api/coerce m a)
          msh (api/shape m)
          ash (api/shape a)
          compatible-dims? (and (= 2 (count msh) (count ash))
                                (= (last msh) (first ash)))]
      (do-multiply-or-scale
       m a
       :when compatible-dims? (let [r (new-ejml-matrix (first msh) (last ash))]
                                (CommonOps/mult m a r) r)
       :else (arg-error "incompatible dimensions for matrix-multiply: " msh " x " ash))))
  (element-multiply [m a]
    (let [a (api/coerce m a)
          msh (api/shape m)
          ash (api/shape a)
          same-dims? (= msh ash)]
      (do-multiply-or-scale
       m a
       :when same-dims? (let [r (apply new-ejml-matrix msh)]
                          (CommonOps/elementMult m a r) r)
       :else (arg-error "incompatible dimensions for element-multiply: " msh " x " ash))))

  mp/PMatrixProducts
  (inner-product [m a]
    (let [a (api/coerce m a)
          msh (api/shape m)
          ash (api/shape a)
          compatible-dims? (and (= 2 (count msh) (count ash))
                                (= (first msh) (first ash)))]
      (do-multiply-or-scale
       m a
       :when compatible-dims? (let [r (new-ejml-matrix (last msh) (last ash))]
                                (CommonOps/multTransA m a r) r)
       :else (arg-error "incompatible dimensions for inner-product: " msh " x " ash))))
  (outer-product [m a]
    (let [a (api/coerce m a)
          compatible-shapes? (= 1 (last (api/shape m)) (first (api/shape a)))]
      (do-multiply-or-scale
       m a
       :when compatible-shapes? (let [[rows _] (api/shape m)
                                      [_ cols] (api/shape a)
                                      r     (new-ejml-matrix rows cols)]
                                  (doseq [i (range rows)
                                          j (range cols)]
                                    (.set r i j (* (api/mget m i 0) (api/mget a 0 j))))
                                  r)
       :else (arg-error "incompatible dimensions for outer-product: "
                        (api/shape m) " x " (api/shape a))))))

(mpi/register-implementation (new-ejml-matrix 2 2))
