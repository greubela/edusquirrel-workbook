package it.evadid.evacuation.core.datastructures.matrix

import scala.language.implicitConversions

import it.evadid.evacuation.core.datastructures.matrix.MatrixPosition._

case class Matrix[T](dim: MatrixDimension, elements: List[T]) {

  val elementsAtPosition: Seq[(T, PositionInMatrix)] = dim.positions.map(pim => ((get(pim)).get, pim))

  assert(dim.rows * dim.cols == elements.length, "rows*cols must match the amount of tiles but " + dim.rows + " * " + dim.cols + " != " + elements.length)


  //private val mutableElements = elements.toBuffer // performance for get method (faster than list lookup)


  def get(pos: MatrixPosition): Option[T] =
    pos.in(this).asIndex.map(elements)

  def getNeighbourPositions(pos: MatrixPosition, neighbours: Seq[MatrixPosition]): Set[PositionInMatrix] = pos.in(this).neighbours(neighbours)

  def getNeighbours(pos: MatrixPosition, neighbours: Seq[MatrixPosition]): Set[T] =
    pos.in(this).neighbours(neighbours).flatMap(pim => pim.getFrom(this))

  def replace(pos: MatrixPosition, withTile: T): Matrix[T] = {
    pos.in(this).asIndex match {
      case Some(i) => Matrix(dim, elements.updated(i, withTile))
      case None => this
    }
  }

  def setToDimension(targetDimension: MatrixDimension, tileFactory: MatrixPosition => T): Matrix[T] = {
    if (targetDimension == dim) {
      return this
    } else {
      Matrix(targetDimension, pim => get(pim.cPos).getOrElse(tileFactory.apply(pim.cPos)))
    }
  }

  def addRow(tileFactory: MatrixPosition => T, rowNr: Int = dim.rows): Matrix[T] = {
    assert(rowNr >= 0 && rowNr <= dim.rows)

    val elementsBefore = (0 until rowNr).flatMap(dim.getRowPositions(_)).flatMap(_.asIndex).map(elements)
    val newElements = dim.getRowPositions(rowNr).map(pim => tileFactory(pim))
    val elementsAfter = (rowNr until dim.rows).flatMap(dim.getRowPositions(_)).flatMap(_.asIndex).map(elements)

    Matrix(dim.addRow(), (elementsBefore ++ newElements ++ elementsAfter).toList)
  }

  def addColumn(tileFactory: MatrixPosition => T, colNr: Int = dim.cols): Matrix[T] = {
    val translatedFactory: MatrixPosition => T = pos => tileFactory.apply(pos.transposed)
    transposed.addRow(translatedFactory, colNr).transposed
  }

  def removeRow(rowNr: Int = dim.rows - 1): Matrix[T] = {
    assert(rowNr >= 0 && rowNr < dim.rows && dim.rows > 1, "Cannot remove row " + rowNr + ": not in range [0, " + dim.rows + ")")

    val elementsBefore = elements.slice(0, rowNr * dim.cols)
    val elementsAfter = elements.slice((rowNr + 1) * dim.cols, elements.size)

    Matrix(dim.addRow(-1), (elementsBefore ++ elementsAfter))
  }

  def removeColumn(colNr: Int = dim.cols - 1): Matrix[T] = transposed.removeRow(colNr).transposed

  def mapTiles[O](func: T => O): Matrix[O] =
    Matrix[O](dim, elements.map(func))

  def transposed: Matrix[T] = Matrix(dim.transposed, dim.transposedPositions.flatMap(_.asIndex).map(elements).toList)

  override val toString: String = (0 until dim.rows).toList.map(dim.getRowPositions(_)).map(_.flatMap(_.asIndex).map(elements).mkString("\t\t")).mkString("\n")

}

object Matrix {

  implicit def dimFromMatrix[T](m: Matrix[T]): MatrixDimension = m.dim

  def apply[T](dim: MatrixDimension, elements: Seq[T]): Matrix[T] = Matrix[T](dim, elements.toList)

  def apply[T](cols: Int, rows: Int, tileGenerator: PositionInMatrix => T): Matrix[T] = apply[T](MatrixDimension(cols, rows), tileGenerator)

  def apply[T](dim: MatrixDimension, tileGenerator: PositionInMatrix => T): Matrix[T] =
    new Matrix(dim, dim.positions.map(PositionInMatrix(_, dim)).map(tileGenerator).toList)


}
