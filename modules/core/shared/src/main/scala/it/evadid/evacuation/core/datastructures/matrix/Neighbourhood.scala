package it.evadid.evacuation.core.datastructures.matrix

case class Neighbourhood(name: String, function: Seq[MatrixPosition])

object Neighbourhood {

  val moore: Neighbourhood = Neighbourhood("moore", List((0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1), (1, 0), (1, 1)))
  val neumann: Neighbourhood = Neighbourhood("neumann", List((0, 1), (-1, 0), (0, -1), (1, 0)))
  val knight: Neighbourhood = Neighbourhood("knight", List((-1, 2), (-2, 1), (-2, -1), (-1, -2), (1, -2), (2, -1), (2, 1), (1, 2)))

}
