package it.evadid.evacuation.core.datastructures.matrix

import MatrixPosition._

case class PositionInMatrix(cPos: MatrixPosition, dim: MatrixDimension, unused: Boolean) {

  override val hashCode: Int = {
    var hash: Int = 7;
    hash = 31 * hash + cPos.hashCode()
    hash = 31 * hash + dim.hashCode()
    hash
  }

  val isInRange: Boolean =
    if (dim.wrapAround) true
    else cPos.x >= 0 && cPos.y >= 0 && cPos.x < dim.cols && cPos.y < dim.rows

  val asIndex: Option[Int] =
    if (isInRange) Some(cPos.x + dim.cols * cPos.y)
    else None

  def getFromOrFail[T](m: Matrix[T]): T = m.elements(asIndex.get)

  def getFrom[T](m: Matrix[T]): Option[T] = asIndex.map(m.elements)

  def neighbours(neighbourFunc: Seq[MatrixPosition]): Set[PositionInMatrix] =
    neighbourFunc.map(_.add(cPos)).map(_.in(dim)).filter(_.isInRange).toSet

  override val toString: String = "PiM[" + cPos.x + "|" + cPos.y + "]"
}

object PositionInMatrix {


  def apply(pos: MatrixPosition, dim: MatrixDimension): PositionInMatrix = {
    val canonical =
      if (dim.wrapAround) MatrixPosition((pos.x % dim.cols + dim.cols) % dim.cols, (pos.y % dim.rows + dim.rows) % dim.rows)
      else pos
    PositionInMatrix(canonical, dim, false)
  }

  def apply(index: Int, dim: MatrixDimension): PositionInMatrix = {
    apply(MatrixPosition(index % dim.cols, index / dim.cols), dim)
  }

  def main(args: Array[String]): Unit = {

    val pim: PositionInMatrix = (0, 1).in(MatrixDimension(2, 5))
   // print("index: " + pim.asIndex + " --> transposed: " + pim.transposedIndex)

  }

}