package it.evadid.core.datastructures.matrix

import upickle.default.*

case class Neighbourhood(name: String, function: Seq[MatrixPosition]) derives ReadWriter

object Neighbourhood {

  val moore: Neighbourhood = Neighbourhood("moore", List((0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1), (1, 0), (1, 1)))
  val neumann: Neighbourhood = Neighbourhood("neumann", List((0, 1), (-1, 0), (0, -1), (1, 0)))
  val knight: Neighbourhood = Neighbourhood("knight", List((-1, 2), (-2, 1), (-2, -1), (-1, -2), (1, -2), (2, -1), (2, 1), (1, 2)))

}
