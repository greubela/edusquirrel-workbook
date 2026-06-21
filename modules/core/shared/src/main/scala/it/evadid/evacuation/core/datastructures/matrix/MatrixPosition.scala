package it.evadid.evacuation.core.datastructures.matrix

import scala.language.implicitConversions

import it.evadid.evacuation.core.datastructures.Direction

import scala.collection.mutable

case class MatrixPosition(x: Int, y: Int) {

  /*
  def left: MatrixPosition = MatrixPosition(x - 1, y)

  def right: MatrixPosition = MatrixPosition(x + 1, y)

  def top: MatrixPosition = MatrixPosition(x, y - 1)

  def bottom: MatrixPosition = MatrixPosition(x, y + 1)

  def topLeft: MatrixPosition = MatrixPosition(x - 1, y - 1)

  def bottomLeft: MatrixPosition = MatrixPosition(x - 1, y + 1)

  def bottomRight: MatrixPosition = MatrixPosition(x + 1, y + 1)

  def topRight: MatrixPosition = MatrixPosition(x + 1, y - 1)*/

  def inDirection(dir: Direction): MatrixPosition = add(dir.toPosition)

  def add(pos: MatrixPosition): MatrixPosition = MatrixPosition(x + pos.x, y + pos.y)

  def sub(pos: MatrixPosition): MatrixPosition = MatrixPosition(x - pos.x, y - pos.y)

  def mult(s: Int): MatrixPosition = MatrixPosition(s * x, s * y)

  def in(dim: MatrixDimension): PositionInMatrix = PositionInMatrix(this, dim)

  def transposed: MatrixPosition = MatrixPosition(y, x)

  override val toString: String = s"Pos[$x|$y]"

  def euclidianDistTo(pos2: MatrixPosition): Double = {
    val dx = pos2.x - x
    val dy = pos2.y - y
    Math.sqrt(dx * dx + dy * dy)
  }

  def manhattenDistTo(pos2: MatrixPosition): Integer = {
    val dx = pos2.x - x
    val dy = pos2.y - y
    Math.abs(dx) + Math.abs(dy)
  }

  def pDistTo(pos2: MatrixPosition, p: Int): Double = {
    val dx = pos2.x - x
    val dy = pos2.y - y
    Math.pow(Math.pow(dx, p) + Math.pow(dy, p), 1.0 / p)
  }

}

object MatrixPosition {

  implicit def tupToPos(posTup: (Int, Int)): MatrixPosition = MatrixPosition(posTup._1, posTup._2)

  implicit def pimToPos(posPim: PositionInMatrix): MatrixPosition = posPim.cPos

  def getRectangle(width: Int, height: Int): Seq[MatrixPosition] = {

    val buf = mutable.ListBuffer[MatrixPosition]()

    0.until(width).foreach(xPos => {
      0.until(height).foreach(yPos => {
        val pos = MatrixPosition(xPos, yPos)
        buf += pos
      })
    })

    buf.toList

  }

}
