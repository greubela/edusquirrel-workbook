package it.evadid.core.datastructures.matrix


case class Direction(name: String, toPosition: MatrixPosition) {

}

object Direction {

  def fromPosition(matrixPosition: MatrixPosition): Option[Direction] = mooreDirections.find(_.toPosition == matrixPosition)

  val TOP: Direction = Direction("top", MatrixPosition(0, -1))
  val LEFT: Direction = Direction("left", MatrixPosition(-1, 0))
  val RIGHT: Direction = Direction("right", MatrixPosition(1, 0))
  val BOTTOM: Direction = Direction("bottom", MatrixPosition(0, 1))

  val TOP_LEFT: Direction = Direction("top_left", MatrixPosition(-1, -1))
  val TOP_RIGHT: Direction = Direction("top_right", MatrixPosition(1, -1))
  val BOTTOM_LEFT: Direction = Direction("bottom_left", MatrixPosition(-1, 1))
  val BOTTOM_RIGHT: Direction = Direction("bottom_right", MatrixPosition(1, 1))

  val diagonal: List[Direction] = List(TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT)
  val neumannDirections: List[Direction] = List(TOP, LEFT, RIGHT, BOTTOM)

  val mooreDirections: List[Direction] = neumannDirections ++ diagonal

  def fromString(str: String): Option[Direction] =
    try {
      val res = str.toLowerCase() match {
        case "down" => BOTTOM
        case "bottom" => BOTTOM
        case "top" => TOP
        case "up" => TOP

        case "right" => RIGHT
        case "left" => LEFT

        case "topleft" => TOP_LEFT
        case "upleft" => TOP_LEFT
        case "topright" => TOP_RIGHT
        case "upright" => TOP_RIGHT

        case "bottomleft" => BOTTOM_LEFT
        case "downleft" => BOTTOM_LEFT
        case "bottomright" => BOTTOM_RIGHT
        case "downright" => BOTTOM_RIGHT
      }
      Some(res)
    } catch {
      case e: Throwable => None
    }


}