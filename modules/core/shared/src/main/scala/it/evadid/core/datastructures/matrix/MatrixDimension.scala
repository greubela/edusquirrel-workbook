package it.evadid.core.datastructures.matrix

import scala.language.implicitConversions


case class MatrixDimension(cols: Int, rows: Int, wrapAround: Boolean) {

  assert(cols > 0 && rows > 0, "Dimension must be positive")

  def transposed: MatrixDimension = MatrixDimension(rows, cols, wrapAround)

  val positions: Seq[PositionInMatrix] = (0 until rows).flatMap(rowNr => (0 until cols).map(colNr => MatrixPosition(colNr, rowNr))).map(_.in(this))
  val transposedPositions: Seq[PositionInMatrix] = (0 until cols).flatMap(colNr => (0 until rows).map(rowNr => MatrixPosition(colNr, rowNr))).map(_.in(this))

  def positionsClockwise: Seq[PositionInMatrix] = {
    ???
    // Todo: finish (~half: top -> right -> bottom -> left, inc. startAt etc. until ~half)
  }


  def getColPositions(colNr: Int, startAtRow: Int = 0, endAtRow: Int = rows): Seq[PositionInMatrix] =
    (startAtRow until endAtRow).map(MatrixPosition(colNr, _).in(this))

  def getRowPositions(rowNr: Int, startAtCol: Int = 0, endAtCol: Int = cols): Seq[PositionInMatrix] =
    (startAtCol until endAtCol).map(MatrixPosition(_, rowNr).in(this))

  def addRow(amount: Int = 1): MatrixDimension = MatrixDimension(cols, rows + amount, wrapAround)

  def addCol(amount: Int = 1): MatrixDimension = MatrixDimension(cols + amount, rows, wrapAround)

  def encodeToInt(): Int = {
    assert(cols < Short.MaxValue && rows < Short.MaxValue, "Max 2^16-1 rows and cols supported for byte encoding")
    val arr = Array((cols >> 8).toByte, (cols & 255).toByte, (rows >> 8).toByte, (rows & 255).toByte)
    BigInt(arr).toInt
  }


}

object MatrixDimension {

  implicit def tupToDim(tup: (Int, Int)): MatrixDimension = MatrixDimension(tup._1, tup._2, false)

  def apply(cols: Int, rows: Int): MatrixDimension = MatrixDimension(cols, rows, false)

  /*
    def parseFromInt(nr: Int): MatrixDimension = {
      val dim = BigInt(nr).toByteArray
      val col = (byteToUInt(dim(0)) << 8) + byteToUInt(dim(1))
      val row = (byteToUInt(dim(2)) << 8) + byteToUInt(dim(3))
      MatrixDimension(col, row)
    }*/

}
