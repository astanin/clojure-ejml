# clojure-ejml

Efficient Java Matrix Library now for Clojure.

Implementation of [core.matrix](https://github.com/mikera/matrix-api#readme)
and linear algebra APIs for EJML data types.

[EJML][ejml] is a small, pure Java library for linear algebra and matrix operations.
EJML is likely to be slower than native linear algebras, but it is good enough for many application, and it is easier to install as a dependency.

[ejml]: https://code.google.com/p/efficient-java-matrix-library/

## Installation

The library is not quite for the first release to Clojars yet.
To install it, clone this repository, and run `lein install` to install it locally.
Add

    [clojure-ejml "0.18.0.1-SNAPSHOT"]

to the list of `:dependencies` in you `project.clj`.


## Usage

### Core Matrix API

This library implements Clojure [core.matrix API][core.matrix] for
DenseMatrix64F type from the EJML library (more types may be supported
in the future).

[core.matrix]: https://github.com/mikera/core.matrix/blob/develop/src/main/clojure/clojure/core/matrix.clj

```clojure
user=> (require 'clojure.core.matrix.impl.ejml)
nil
user=> (use 'clojure.core.matrix)
nil
```

To create a DenseMatrix64F from Clojure, use `:ejml` implementation tag:

```clojure
user=> (def m (matrix :ejml [[1 2][3 4]]))
#'user/m
user=> m
#<DenseMatrix64F Type = dense , numRows = 2 , numCols = 2
 1.000   2.000
 3.000   4.000
>
```

Some basic matrix operations:

```clojure
user=> (mul m 10)
#<DenseMatrix64F Type = dense , numRows = 2 , numCols = 2
10.000  20.000
30.000  40.000
>
user=> (mmul m m)
#<DenseMatrix64F Type = dense , numRows = 2 , numCols = 2
 7.000  10.000
15.000  22.000
>
user=> (add m m)
#<DenseMatrix64F Type = dense , numRows = 2 , numCols = 2
 2.000   4.000
 6.000   8.000
>
user=> (trace m)
5.0
```

### Linear Algebra API

EJML is a fine linear algebra library. Core Matrix API lacks many of
linear algebra operations.  This projects intends to extend the Core
Matrix API.
[`clojure.core.matrix.linalg`](src/clojure/core/matrix/linalg.clj)
namespace will be the playground to develop extensions.


## License

Copyright © 2013 Sergey Astanin

Distributed under the Eclipse Public License, the same as Clojure.
