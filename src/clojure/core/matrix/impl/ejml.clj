(ns clojure.core.matrix.impl.ejml
  "Implementation of core.matrix and linear algebra API for EJML DenseMatrix64F."
  (:require [clojure.core.matrix :as api]
            [clojure.core.matrix.protocols :as mp]
            [clojure.core.matrix.implementations :as mpi]
            [clojure.core.matrix.linalg :as linalg])
  (:import [org.ejml.data Matrix64F DenseMatrix64F MatrixIterator D1Submatrix64F]
           [org.ejml.ops CommonOps]))


(defmacro arg-error
  [& message-parts]
  `(throw (IllegalArgumentException. (str ~@message-parts))))


(defmacro unsupported-error
  [& message-parts]
  `(throw (UnsupportedOperationException. (str ~@message-parts))))


;;; TODO: implement matrix-api for D1Submatrix64F and SimpleMatrix too

(defn- alloc-DenseMatrix64F
  ([^DenseMatrix64F m]
     (DenseMatrix64F. (.getNumRows m) (.getNumCols m)))
  ([rows cols]
     (DenseMatrix64F. rows cols)))


(defn- ejml-shape
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
  "Converts input to a new EJML DenseMatrix64F.

  Input can be:

    - a sequence of sequences of numbers or other 2D-shaped matrix data
    - a sequence of numbers
    - a DenseMatrix64F"
  ([input]
     (cond

      (instance? DenseMatrix64F input) ; safe to call (to-matrix (to-matrix ..))
      input

      (= 2 (api/dimensionality input)) ; create a 2D matrix
      (let [^"[[D" arr2d (into-array (map double-array input))]
        (DenseMatrix64F. arr2d))

      (= 1 (api/dimensionality input)) ; create a column vector
      (let [^"[[D" arr2d (into-array (mapv (comp double-array vector) input))]
        (DenseMatrix64F. arr2d))

      (= 0 (api/dimensionality input)) ; create a 1x1 matrix
      (let [^"[[D" arr2d (into-array [(double-array [input])])]
        (DenseMatrix64F. arr2d))

      :otherwise
      (arg-error "to-ejml-matrix: cannot create a matrix from " input))))


(defn from-ejml-matrix
  "Converts an EJML DenseMatrix64F to a Clojure vector or a vector or vectors."
  [^DenseMatrix64F m]
  (if (ejml-1d? m)
    (into [] (.getData m))  ; 1D matrix to a simple vector
    (mapv vec (partition (.numCols m) (.getData m))) ; 2D matrix to vector of vectors
    ))


(defn ejml-submatrix-seq
  "Returns a row-major sequence of submatrix' elements of m.

  Arguments row-or-range and column-or-range can be:

    - nil        to select all rows or all columns respectively
    - a number   to select just one row or just one column respectively
    - a sequence to select a contiguous range of rows or columns,
                 e.g. [1 4] to select 2nd, 3rd, 4th, and 5th rows (columns).
  "
  [m row-or-range column-or-range]
  (let [[rows cols]       (api/shape m)
        row-range         (if row-or-range
                            (if (sequential? row-or-range) row-or-range [row-or-range])
                            ;; all rows otherwise
                            [0 (- rows 1)])
        [min-row max-row] (apply (juxt min max) row-range)
        min-row           (max min-row 0)
        max-row           (min max-row (- rows 1))
        column-range      (if column-or-range
                            (if (sequential? column-or-range) column-or-range [column-or-range])
                            ;; all columns otherwise
                            [0 (- cols 1)]
                            )
        [min-col max-col] (apply (juxt min max) column-range)
        min-col           (max min-col 0)
        max-col           (min max-col (- cols 1))
        m-iter            (MatrixIterator. m true min-row min-col max-row max-col)
        n                 (* (+ 1 (- max-row min-row)) (+ 1 (- max-col min-col)))]
    (for [_ (range n)]
      (.next m-iter))))


(defn ejml-matrix-seq
  "Returns a row-major sequence of all matrix elements of m."
  [m]
  (let [[rows cols] (api/shape m)
        m-iter      (MatrixIterator. m true 0 0 (- rows 1) (- cols 1))]
    (for [_ (range (* rows cols))]
      (.next m-iter))))


(defn ejml-rows-seq
  "Returns a sequence of rows for a matrix m or its submatrix.

  Arguments row-or-range and column-or-range can be:

    - nil        to select all rows or all columns respectively
    - a number   to select just one row or just one column respectively
    - a sequence to select a contiguous range of rows or columns,
                 e.g. [1 4] to select 2nd, 3rd, 4th, and 5th rows (columns).
  "
  ([m]
     (let [elements-seq (ejml-matrix-seq m)
           [rows cols]  (api/shape m)]
       (partition-all cols elements-seq)))
  ([m row-or-range columns-or-range]
     (let [elements-seq (ejml-submatrix-seq m row-or-range columns-or-range)
           cols         (if (sequential? columns-or-range) (count columns-or-range) 1)]
       (partition-all cols elements-seq))))


(defn ejml-index-range
  "Returns a sequence of all indicies of a matrix in row-major order."
  [m]
  (let [[rows cols] (api/shape m)]
    (for [i (range rows) j (range cols)]
      [i j])))


(defn do-op
  "A wrapper for binary matrix-anything operations with shape checks and conversions.

  Usage:

  (do-op m a
    :with-scalar (fn [m scalar-a] ...)
    :with-vector (fn [m vector-a] ...)
    :with-matrix (fn [m matrix-a] ...)
    :shape-constraint (fn [m-shape a-shape] ...)
    :interop-1d true))

  If :with-scalar and :with-vector operations are not
  defined, :with-matrix operation is used for scalar and vector
  arguments. Scalars are coerced to double, vectors are converted to
  column-vectors.

  :with-vector clause can take a special value :interop-1d, which will
  apply matrix-matrix operation modified by interop-1d.

  If :shape-constraint is missing, the same-shape constraint is assumed.

  If :interop-1d is given and is true, input and result are both 1D,
  then the returned value is converted to 1D shape for
  interoperability with vector implementation.
  "
  [m a & ops]
  (let [ops-map (apply hash-map ops)
        shape-constraint (:shape-constraint ops-map (fn [m-shape a-shape] (= m-shape a-shape)))
        result-shape (:result-shape ops-map (fn [m-shape _] m-shape))
        result (cond
                ;; matrix-scalar operation
                (and (contains? ops-map :with-scalar)
                     (= 0 (api/dimensionality a)) ;; true for ScalarWrappers too
                     )
                (let [scalar-op (:with-scalar ops-map)]
                  (scalar-op m (api/coerce m a)))
                ;; matrix-vector operation
                (and (contains? ops-map :with-vector)
                     (api/vec? a))
                (let [vector-op (:with-vector ops-map)]
                  (vector-op m (api/coerce m a)))
                ;; default matrix-matrix operation
                (contains? ops-map :with-matrix)
                (let [matrix-op (:with-matrix ops-map)
                      a-2d-or-0d (if (not (api/scalar? a)) (to-ejml-matrix a) (api/coerce m a))
                      compatible-dims? (shape-constraint (api/shape m) (api/shape a-2d-or-0d))]
                  (if (or compatible-dims? (api/scalar? a))
                    (matrix-op m a-2d-or-0d)
                    ;; otherwise
                    (arg-error "incompatible dimensions: " (api/shape m) " & " (api/shape a))))
                :otherwise
                (arg-error ":with-matrix clause not found"))]
    (if (and (:interop-1d ops-map) (api/vec? a) (ejml-1d? result))
      (from-ejml-matrix result)
      result)))


(extend-type DenseMatrix64F

  mp/PImplementation
  (implementation-key [m] :ejml)
  (meta-info [m] {:doc "org.ejml.data.DenseMatrix64F"})
  (construct-matrix [m data] (to-ejml-matrix data))
  (new-vector [m length] (new-ejml-matrix length 1))
  (new-matrix [m rows cols] (new-ejml-matrix rows cols))
  (new-matrix-nd [m shape]
    (condp = (count shape)
      ;; create a 2D matrix (ND not supported by EJML)
      2     (let [[rows cols] shape] (new-ejml-matrix rows cols))
      ;; create a row-vector
      1     (let [[cols] shape] (new-ejml-matrix 1 cols))
      ;; throw an exception otherwise
      (arg-error "unsupported shape for an EJML matrix: ")))
  (supports-dimensionality? [m dimensions] (= dimensions 2))

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

  ;; Not implementing PArrayMetrics yet (nonzero-count).
  ;; EJML doesn't have an optimized implementation of the method.

  ;; Not implementing PValidateShape (validate-shape).
  ;; Assuming EJML takes care of the valid matrix state.

  mp/PRowColMatrix
  (column-matrix [m data]
    (if (= 1 (api/dimensionality data))
      (to-ejml-matrix (mapv vector data))
                                        ; "should throw an error if the data is not a 1D vector
      (arg-error "not a 1D vector: shape = " (api/shape data))))
  (row-matrix [m data]
    (if (= 1 (api/dimensionality data))
      (to-ejml-matrix [data])
      (arg-error "not a 1D vector: shape = " (api/shape data))))

  mp/PMutableMatrixConstruction
  (mutable-matrix [m] (.copy m))

  ;; ;; EJML doesn't support sparse representation yet. Return nil from
  ;; ;; PSparse methods to indicate that sparse conversion is not available.
  ;; ;; TODO: Uncomment when the new version of core.matrix is released.
  ;; mp/PSparse
  ;; (sparse-coerce [m data])
  ;; (sparse [m])

  ;; ;; TODO: Uncomment when the new version of core.matrix is released.
  ;; mp/PDense
  ;; (dense-coerse [m data] (mp/coerse m data))
  ;; (dense [m] m)

  ;; Not implementing PImmutableMatrixConstruction because EJML
  ;; doesn't support immutable matrices.

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
    (let [[^int rows ^int cols]
          (if (seq? dims)
            dims  ;; dims is a sequance, create a possibly non-square matrix
            [dims dims]  ;; dims is a scalar (like in compliance-test), create a square matrix
            )]
      (CommonOps/identity rows cols)))
  (diagonal-matrix [m diagonal-values]
    (CommonOps/diag (double-array diagonal-values)))

  ;; Not implementing PPermutationMatrix, using the default implementation.

  mp/PCoercion
  (coerce-param [m p]
    (condp = (api/dimensionality p)
      ;; create a double
      0 (->> p api/scalar double)
      ;; create an array of doubles from 1D params
      1 (->> p api/to-nested-vectors double-array)
      ;; create a normal EJML matrix otherwise
      2 (->> p api/to-nested-vectors to-ejml-matrix)
      (arg-error "EJML supports only 2D matrices, but params' shape is " (api/shape p))))

  ;; EJML seems to be unable to build matrices from blocks, so we
  ;; convert to intermediate vector-based representation to handle
  ;; arbitrary broadcasting.
  mp/PBroadcast
  (broadcast [m target-shape]
    (let [vm (-> m
                 api/to-nested-vectors
                 (api/broadcast target-shape))]
      (if (<= 0 (api/dimensionality vm) 2)
        (to-ejml-matrix vm)
        ;; EJML supports only 2D matrices, fall back to vector
        ;; representation for higher dimensionality
        vm)))

  ;; Not implementing PBroadcastLike and PBroadcastCoerce, using the
  ;; default implementation

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

  ;; Don't implement PPack because only DenseMatrix64F is supported now.

  ;; TODO: implement mutable slices to pass the tests

  ;; ;; TODO: using D1Submatrix64F might help to avoid constructing new
  ;; ;; sequences/vectors, but we need to implement matrix-api for
  ;; ;; D1Submatrix64F first
  ;; ;;
  ;; ;; For future references:
  ;; ;;
  ;; ;;   (D1Submatrix. ...)  ; refers to the original matrix data
  ;; ;;   (.extract (D1Submatrix. ...))  ; copies data into SimpleMatrix
  ;; ;;
  ;; mp/PMatrixSlices
  ;; (get-row [m i]
  ;;   (let [[_ cols] (api/shape m)]
  ;;     (ejml-submatrix-seq m i (range cols))))
  ;; (get-column [m i]
  ;;   (let [[rows _] (api/shape m)]
  ;;     (ejml-submatrix-seq m (range rows) i)))
  ;; (get-major-slice [m i]
  ;;   (mp/get-row m i))
  ;; (get-slice [m dimension i]
  ;;   (condp = dimension
  ;;     0 (mp/get-row m i)
  ;;     1 (mp/get-column m i)
  ;;     (unsupported-error "EJML supports only 2D matrices")))

  ;; ;; Specs: "Must return a mutable slice view". Mutating the original matrix?
  ;; ;; TODO: return a D1Submatrix64F?
  ;; mp/PSubVector
  ;; (subvector [m start length]
  ;;   (let [[rows cols] (api/shape m)
  ;;         is-column?  (= 1 cols)
  ;;         is-row?     (= 1 rows)]
  ;;     (cond
  ;;      is-column? (ejml-submatrix-seq m (range start (+ start length)) 0)
  ;;      is-row?    (ejml-submatrix-seq m 0 (range start (+ start length)))
  ;;      :else      (arg-error "subvector of a matrix is undefined"))))


  ;; ;; Specs: "Must return a mutable slice view". Mutating the original matrix?
  ;; ;; TODO: return a D1Submatrix64F?
  ;; mp/PSliceView
  ;; (get-major-slice-view [m i]
  ;;   (mp/get-row m i))

  ;; mp/PSliceSeq
  ;; (get-major-slice-seq [m]
  ;;   (for [row (range (second (api/shape m)))]
  ;;     (api/get-row m row)))


  ;; ;; TODO: suggest a variadic (join) in matrix-api to avoid multiple allocations
  ;; mp/PSliceJoin
  ;; (join [m a]
  ;;   (let [m-shape (api/shape m)
  ;;         a-shape (api/shape a)]
  ;;     (cond
  ;;      (= (rest m-shape) a-shape)  ;; joining with a 1D row vector
  ;;      (let [m2 (api/clone m)
  ;;            arr2 (double-array (concat (.data m2) (api/eseq a)))
  ;;            [mrows mcols] m-shape]
  ;;        (.setData m2 arr2)
  ;;        (.reshape m2 (+ 1 mrows) mcols true)
  ;;        m2)
  ;;      (= (rest m-shape) (rest a-shape))  ;; joining rows
  ;;      (let [m2 (api/clone m)
  ;;            arr2 (double-array (concat (.data m2) (api/eseq a)))
  ;;            [mrows mcols] m-shape
  ;;            [arows _]     a-shape]
  ;;        (.setData m2 arr2)
  ;;        (.reshape m2 (+ mrows arows) mcols true)
  ;;        m2)
  ;;      :else (arg-error "joining matrices of incompatible shape: "
  ;;                       m-shape " and " a-shape))))

  mp/PMatrixSubComponents
  (main-diagonal [m]
    (let [[r c] (api/shape m)
          d (new-ejml-matrix (min r c) 1)]
      (CommonOps/extractDiag m d)
      (.data d)))

  mp/PAssignment
  (assign! [m v]
    (if (= 0 (api/dimensionality v))
      (mp/fill! m v)
      ;; else
      (let [vm (api/broadcast-like m v)]
        (api/emap! (fn [_ rhs] rhs) m vm)
        m)))
  (assign-array!
    ([m arr]
       (let [^doubles arr (api/coerce m arr)]
         (if (= (api/ecount m) (alength arr))
           (do
             (set! (.data m) arr)
             m)
           ;; else
           (arg-error "array is not the same size as the matrix: "
                      "matrix shape=" (api/shape m) " "
                      "array length=" (alength arr)))))
    ([m arr start length]
       (let [^doubles arr (api/coerce m arr)]
         (cond
          (or (< start 0)
              (> (+ start length) (alength arr)))
          (arg-error "array slice is out of bounds: "
                     "array length=" (alength arr) " "
                     "start=" start " "
                     "length=" length)
          ;;
          (not= (api/ecount m) length)
          (arg-error "array slice is not the same size as the matrix: "
                     "matrix shape=" (api/shape m) " "
                     "slice length=" length)
          ;;
          :normally
          (let [mdata (.data m)]
            (doseq [i (range 0 length)]
              (aset mdata (int i) (double (aget arr (+ start i)))))
            m)))))

  mp/PMutableFill
  (fill! [m v]
    (CommonOps/fill m (api/coerce m v))
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
          a-seq   (when (seq a-shape)
                    (ejml-submatrix-seq a (range (first a-shape)) (range (second a-shape))))
          b-seq   (when (seq b-shape)
                    (ejml-submatrix-seq b (range (first b-shape)) (range (second b-shape))))]
      (and (= a-shape b-shape)
           (every? #(apply == %) (map list a-seq b-seq)))))


  mp/PMatrixMultiply
  (matrix-multiply [m a]
    (do-op m a
      :with-scalar
      (fn [m a]
        (let [r (apply new-ejml-matrix (api/shape m))]
          (CommonOps/scale a m r)
          r))
      :with-matrix
      (fn [m a]
        (let [r (new-ejml-matrix (first (api/shape m)) (second (api/shape a)))]
          (CommonOps/mult m a r)
          r))
      :shape-constraint
      (fn [msh ash]
        (= (second msh) (first ash)))
      :interop-1d
      true))
  ;;
  (element-multiply [m a]
    (let [r (apply new-ejml-matrix (api/shape m))]
      (do-op m a
        :with-scalar
        (fn [m a]
          (CommonOps/scale a m r)
          r)
        :with-vector
        (fn [m v]
          (let [vm (api/broadcast-like m v)]
            (CommonOps/elementMult m vm r)
            r))
        :with-matrix
        (fn [m a]
          (CommonOps/elementMult m a r)
          r))))

  mp/PMatrixProducts
  (inner-product [m a]  ;; FIXME: decide on the meaning of the matrix inner product
    (do-op m a
      :with-scalar
      (fn [m a]
        (let [am (api/broadcast-like m a)]
          (api/inner-product m am)))
      :with-vector
      (fn [m a]
        (let [am (api/broadcast-like m a)]
          (api/inner-product m am)))
      :with-matrix
      (fn [m a]
        (let [[_ mcols] (api/shape m)
              [_ acols] (api/shape a)
              r   (new-ejml-matrix mcols acols)]
          (CommonOps/multTransA m a r)
          r))
      :shape-constraint
      (fn [[mrows _] [arows _]]
        (= mrows arows))))
  ;;
  (outer-product [m a]
    (do-op m a
      :with-matrix
      (fn [m a]
        (let [[mrows _] (api/shape m)
              [_ acols] (api/shape a)
              r  (new-ejml-matrix mrows acols)]
          (doseq [i (range mrows)
                  j (range acols)]
            (.set r i j (* (api/mget m i 0) (api/mget a 0 j))))
          r))
      :shape-constraint
      (fn [[_ mcols] [arows _]]
        (= 1 mcols arows))))

  mp/PMatrixAdd
  (matrix-add [m a]
    (let [r (apply new-ejml-matrix (api/shape m))]
      (do-op m a
        :with-matrix
        (fn [m a]
          (CommonOps/add m a r)
          r)
        :with-vector
        (fn [m ^doubles a]
          (let [am (->> a (api/broadcast-like m) to-ejml-matrix)]
            (CommonOps/add m am r)
            r))
        :with-scalar
        (fn [m ^double a]
          (CommonOps/add m a r)
          r))))
  ;;
  (matrix-sub [m a]
    (let [r (apply new-ejml-matrix (api/shape m))]
      (do-op m a
        :with-matrix
        (fn [m a]
          (CommonOps/sub m a r)
          r)
        :with-vector
        (fn [m ^doubles a]
          (let [am (->> a (api/broadcast-like m) to-ejml-matrix)]
            (CommonOps/sub m am r)
            r))
       :with-scalar
       (fn [m a]
         (CommonOps/add m (* -1 a) r)
         r))))

  mp/PMatrixScaling
  (scale [m a]
    (let [r (apply new-ejml-matrix (api/shape m))
          as (api/coerce m a)]
      (CommonOps/scale as m r)
      r))
  ;;
  (pre-scale [m a]
    (api/scale m a))

  mp/PMatrixDivide
  (element-divide [m a]
    (let [r (apply new-ejml-matrix (api/shape m))]
      (do-op m a
        :with-scalar
        (fn [m a]
          (CommonOps/divide a m r)
          r)
        :with-vector
        (fn [m v]
          (let [vm (->> v (api/broadcast-like m) to-ejml-matrix)]
            (CommonOps/elementDiv m vm r)
            r))
        :with-matrix
        (fn [m a]
          (CommonOps/elementDiv m a r)
          r))))

  mp/PExponent
  (element-pow [m exponent]
    (let [e (api/coerce m exponent)]
      (api/emap #(Math/pow % e) m)))

  mp/PFunctionalOperations
  (element-seq [m]
    (ejml-matrix-seq m))
  ;;
  (element-map
    ([m f]
       (let [r (apply new-ejml-matrix (api/shape m))]
         (doseq [[i j] (ejml-index-range m)]
           (let [^double x (mp/get-2d m i j)]
             (mp/set-2d! r i j (f x))))
         r))
    ([m f a]
       (let [r (apply new-ejml-matrix (api/shape m))
             a (->> a (api/broadcast-like m) to-ejml-matrix)]
         (doseq [[i j] (ejml-index-range m)]
           (let [^double x (mp/get-2d m i j)
                 ^double y (mp/get-2d a i j)]
             (mp/set-2d! r i j (f x y))))
         r))
    ([m f a more]
       (let [r (apply new-ejml-matrix (api/shape m))
             a (->> a (api/broadcast-like m) to-ejml-matrix)
             more (map #(->> % (api/broadcast-like m) to-ejml-matrix) more)]
         (doseq [[i j] (ejml-index-range m)]
           (let [^double x (mp/get-2d m i j)
                 ^double y (mp/get-2d a i j)
                 zs (map #(mp/get-2d % i j) more)]
             (mp/set-2d! r i j (apply f x y zs))))
         r)))
  ;;
  (element-map!
    ([m f]
       (doseq [[i j] (ejml-index-range m)]
         (let [^double x (mp/get-2d m i j)]
           (mp/set-2d! m i j (f x))
           m)))
    ([m f a]
       (let [a (->> a (api/broadcast-like m) to-ejml-matrix)]
         (doseq [[i j] (ejml-index-range m)]
           (let [^double x (mp/get-2d m i j)
                 ^double y (mp/get-2d a i j)]
             (mp/set-2d! m i j (f x y)))
           m)))
    ([m f a more]
       (let [a (->> a (api/broadcast-like m) to-ejml-matrix)
             more (map #(->> % (api/broadcast-like m) to-ejml-matrix) more)]
         (doseq [[i j] (ejml-index-range m)]
           (let [^double x (mp/get-2d m i j)
                 ^double y (mp/get-2d a i j)
                 zs (map #(mp/get-2d % i j) more)]
             (mp/set-2d! m i j (apply f x y zs))
             m)))))
  ;;
  (element-reduce
    ([m f]
       (reduce f (ejml-matrix-seq m)))
    ([m f init]
       (reduce f init (ejml-matrix-seq m))))

  mp/PRowOperations
  (swap-rows [m i j]
    (let [r (api/clone m)
          [_ cols] (api/shape m)]
      (doseq [col (range cols)]
        (mp/set-2d! r i col (mp/get-2d m j col))
        (mp/set-2d! r j col (mp/get-2d m i col)))
      r))
  (multiply-row [m i k]
    (let [r (api/clone m)
          [_ cols] (api/shape m)]
      (doseq [col (range cols)]
        (mp/set-2d! r i col (* k (mp/get-2d m i col))))
      r))
  (add-row [m i j k]
    "Returns a new matrix with row i added to row j times k"
    (let [r (api/clone m)
          [_ cols] (api/shape m)]
      (doseq [col (range cols)]
        (mp/set-2d! r i col
                    (+ (mp/get-2d m i col)
                       (* k (mp/get-2d m j col)))))
      r))

#_(defprotocol PRowSetting
  "Protocol for row setting. Should set a dimension 0 (row) slice to thegiven row value."
  (set-row [m i row])
  (set-row! [m i row]))


)

(mpi/register-implementation (new-ejml-matrix 2 2))

;; Local Variables:
;; eval: (put 'do-op 'clojure-indent-function 2)
;; eval: (put 'arg-error 'clojure-indent-function 0)
;; eval: (put 'unsupported-error 'clojure-indent-function 0)
;; End:
