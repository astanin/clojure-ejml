(ns clojure.core.matrix.linalg
  "This is sandbox for linear algebra extensions to clojure.core.matrix API.

Status of matrix operations support in clojure APIs w.r.t NumPy and
Matlab (as of 2013-09):

|---------------+------------------------------+----------------------------+---------------+---------------------------------|
| category      | functionality                | numpy & scipy              | matlab        | c.c.matrix.protocols            |
|---------------+------------------------------+----------------------------+---------------+---------------------------------|
| products      | dot product                  | dot                        | dot           | PVectorOps vector-dot           |
|               | cross product                | cross                      | cross         | PVectorCross vector-cross       |
|               | inner product                | inner                      | *, mtimes     | PMatrixProducts inner-product   |
|               | outer product                | outer                      | meshgrid?     | PMatrixProducts outer-product   |
|               | tensor dot product           | tensordot                  | -             | -                               |
|               | Einstein summation           | einsum                     | -             | -                               |
|               | matrix power                 | matrix_power               | mpower        | PAddProductMutable add-product! |
|---------------+------------------------------+----------------------------+---------------+---------------------------------|
| decomposition | Cholesky                     | cholesky                   | chol          | TODO                            |
|               | incomplete Cholesky          | TODO                       | ichol         |                                 |
|               | LU                           | s.l.lu                     | lu            |                                 |
|               | incomplete LU                | s.s.l.spilu                | ilu           |                                 |
|               | QR                           | qr                         | qr            | TODO                            |
|               | SVD                          | svd                        | svd           | TODO                            |
|               | largest singular values      | svd                        | svds          |                                 |
|---------------+------------------------------+----------------------------+---------------+---------------------------------|
| eigenvalues   | with vectors                 | eig                        | eig           | TODO                            |
|               | with vectors (Hermitian)     | eigh                       | eig           | TODO                            |
|               | largest eigen values         | -                          | eigs          | -                               |
|               | largest eigen values         | -                          | eigs          | -                               |
|               | eigenvalues only             | eigvals                    | eig           | -                               |
|               | eigenvalues only (Hermitian) | eigvalsh                   | eig           | -                               |
|---------------+------------------------------+----------------------------+---------------+---------------------------------|
| summary       | matrix and vector norm       | norm                       | norm          | -                               |
|               | condition number             | cond                       | cond, condeig | -                               |
|               | determinant                  | det, slogdet               | det           | PMatrixOps determinant          |
|               | trace                        | trace                      | trace         | PMatrixOps trace                |
|---------------+------------------------------+----------------------------+---------------+---------------------------------|
| solvers       | SLE                          | solve                      | linsolve      |                                 |
|               | tensor equation              | tensor_solve               | -             |                                 |
|               | least-squares SLE            | lstsq                      | lscov         |                                 |
|               | multiplicative inverse       | inv                        | inv           |                                 |
|               | Moore-Penrose inverse        | pinv, s.l.pinv2, s.l.pinvh | pinv          |                                 |
|               | tensor inverse               | tensorinv                  | -             |                                 |
|---------------+------------------------------+----------------------------+---------------+---------------------------------|

where s.l.* = scipy.linalg functions, s.s.l.* = scipy.sparse.linalg functions.

=> clojure.core.matrix lacks protocols for decompositions and solvers;
PMatrixOps can be extended with (cond). Norms deserve to be a separate
protocol as also non-matrix types may implement them (PVectorDistance
may be extended to support non-Euclidean distances too)."
)


(defprotocol PHasNorm
  "Protocol for types which have at least one norm.

  All implementations should implement the default norm as the
  unary (norm v) method. Imlementations with more than one norm should
  implement binary (norm v norm-id) method.

  The following convention should be used for matrices and
  vectors (similar to NumPy):

  ========  =============================  ==========================
  norm-id   norm for matrices              norm for vectors
  ========  =============================  ==========================
  0         nil                            number of non-zero values
  1         max column-sum of abs. values  sum of absolute values
  2         2-norm (largest sing. value)   Euclidean norm (default)
  :inf      max row-sum of abs. values     max of absolute values
  :fro      Frobenius norm (default)       nil

"
  (norm [m] "Calculates default norm.")
  (norm [m norm-id] "Calculates a norm identified with norm-id."))
