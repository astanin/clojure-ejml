(ns clojure.core.linalg
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
