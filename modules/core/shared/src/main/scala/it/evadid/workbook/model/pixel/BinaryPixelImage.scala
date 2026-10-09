package it.evadid.workbook.model.pixel

import upickle.default.*

case class PixelPosition(row: Int, column: Int) derives ReadWriter {
  require(row >= 0 && column >= 0, "Pixel coordinates must be nonnegative")
}

/** Row-major binary image. Bounds keep authored classroom canvases reasonably small. */
case class BinaryPixelImage(rows: Int, columns: Int, pixels: List[Boolean]) derives ReadWriter {
  require(rows >= 1 && rows <= 32 && columns >= 1 && columns <= 32, "Pixel dimensions must be between 1 and 32")
  require(pixels.size == rows * columns, "Pixel count must match dimensions")
  def index(position: PixelPosition): Int = {
    require(position.row < rows && position.column < columns, "Pixel is outside the image")
    position.row * columns + position.column
  }
  def at(position: PixelPosition): Boolean = pixels(index(position))
  def toggle(position: PixelPosition): BinaryPixelImage = {
    val i = index(position)
    copy(pixels = pixels.updated(i, !pixels(i)))
  }
  def sameDimensions(other: BinaryPixelImage): Boolean = rows == other.rows && columns == other.columns
  def matchingPixels(other: BinaryPixelImage): Int = {
    require(sameDimensions(other), "Images must have matching dimensions")
    pixels.zip(other.pixels).count((a, b) => a == b)
  }
  def binaryRows: List[String] = pixels.grouped(columns).map(_.map(b => if b then "1" else "0").mkString).toList
}
object BinaryPixelImage {
  def blank(rows: Int, columns: Int): BinaryPixelImage = {
    require(rows >= 1 && rows <= 32 && columns >= 1 && columns <= 32, "Pixel dimensions must be between 1 and 32")
    BinaryPixelImage(rows, columns, List.fill(rows * columns)(false))
  }
  def fromRows(rows: List[String]): BinaryPixelImage = {
    require(rows.nonEmpty && rows.head.nonEmpty && rows.forall(r => r.length == rows.head.length && r.forall(c => c == '0' || c == '1')),
      "Binary rows must be rectangular and contain only 0 and 1")
    BinaryPixelImage(rows.size, rows.head.length, rows.flatMap(_.map(_ == '1')))
  }
}
