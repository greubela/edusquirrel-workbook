package it.evadid.evacuation.eva2.model

import it.evadid.core.datastructures.graph.{Edge, WeightedNeighbourStructure}
import it.evadid.core.datastructures.matrix.*
import it.evadid.evacuation.core.algorithm.routing.Dijkstra.DijkstraInformation
import it.evadid.evacuation.core.algorithm.routing.model.SearchNode
import it.evadid.evacuation.core.algorithm.routing.{Dijkstra, GraphAccess}
import it.evadid.evacuation.core.graphic.spritemap.FloorSpriteProperties
import it.evadid.evacuation.core.graphic.sprites.traits.FloorSprite

import scala.collection.mutable

implicit class FloorMatrix(matrix: Matrix[FloorSprite]) {

  val savePositions: Seq[PositionInMatrix] = matrix.elementsAtPosition.filter(_._1.isSave).map(_._2)

  // also cache this result?

  private val reachableNeighbourCache: mutable.Map[(MatrixPosition, Seq[MatrixPosition]), Set[PositionInMatrix]] = mutable.Map()

  def getReachableNeighbourPositions(pos: MatrixPosition, neighbour: Seq[MatrixPosition], defaultReachableIfNotMoore: Boolean = false): Set[PositionInMatrix] = {
    val key = (pos, neighbour)
    if (!reachableNeighbourCache.contains(key)) {
      val res = matrix.getNeighbourPositions(pos, neighbour).filter(canPass(_, pos, defaultReachableIfNotMoore))
      reachableNeighbourCache.put(key, res)
      res
    } else {
      reachableNeighbourCache(key)
    }
  }

  private def tilePropertiesAt(pos: MatrixPosition): FloorSpriteProperties = matrix.get(pos).get.properties

  def asGraph(neighbourFunc: Neighbourhood): WeightedNeighbourStructure[PositionInMatrix, Int, Edge[PositionInMatrix, Int]] = {

    val neighbourMap = new mutable.HashMap[PositionInMatrix, List[Edge[PositionInMatrix, Int]]]()

    def calcNeighbours(n: PositionInMatrix): List[Edge[PositionInMatrix, Int]] = {
      n.neighbours(neighbourFunc.function).filter(_ != n).filter(canPass(n, _)).map(Edge(n, _, -1)).toList
    }

    matrix.positions.foreach(pos => neighbourMap.put(pos, calcNeighbours(pos)))

    new WeightedNeighbourStructure[PositionInMatrix, Int, Edge[PositionInMatrix, Int]] {
      override def getDistFromEdge(edge: Edge[PositionInMatrix, Int]): Double = 1.0

      override def getNeighbours(n: PositionInMatrix): List[Edge[PositionInMatrix, Int]] = neighbourMap(n)
    }
  }

  def dijkstraAccess(neighbourFunc: Seq[MatrixPosition]): GraphAccess[PositionInMatrix, DijkstraInformation] =
    GraphAccess.fromFunction[PositionInMatrix, Dijkstra.DijkstraInformation](searchNode => {
      val reachable = getReachableNeighbourPositions(searchNode.node, neighbourFunc, false)
      val searchNodes = reachable.map(pim => new SearchNode(pim, Some(searchNode.node), new Dijkstra.DijkstraInformation(searchNode.info.distFromStart + 1)))
      searchNodes.toList
    })

  def canPass(pos1: MatrixPosition, pos2: MatrixPosition, defaultIfNotMooreNeighbours: Boolean = false): Boolean = {

    if (pos1 == pos2) true
    else if (pos1.x < 0 || pos2.x < 0 || pos1.x >= matrix.dim.cols || pos2.x >= matrix.dim.cols) false
    else if (pos1.y < 0 || pos2.y < 0 || pos2.y >= matrix.dim.rows || pos2.y >= matrix.dim.rows) false
    else {
      val dist = pos1.euclidianDistTo(pos2)

      if (dist > 1.5) {
        defaultIfNotMooreNeighbours
      }
      else {

        val prop1 = tilePropertiesAt(pos1)
        val prop2 = tilePropertiesAt(pos2)

        if (pos1 == pos2.inDirection(Direction.LEFT)) prop1.isFreeRight && prop2.isFreeLeft
        else if (pos1 == pos2.inDirection(Direction.RIGHT)) prop1.isFreeLeft && prop2.isFreeRight
        else if (pos1 == pos2.inDirection(Direction.BOTTOM)) prop1.isFreeTop && prop2.isFreeBottom
        else if (pos1 == pos2.inDirection(Direction.TOP)) prop1.isFreeBottom && prop2.isFreeTop
        else if (pos1 == pos2.inDirection(Direction.TOP_LEFT)) prop1.isFreeBottomRight && prop2.isFreeTopLeft
        else if (pos1 == pos2.inDirection(Direction.BOTTOM_LEFT)) prop1.isFreeTopRight && prop2.isFreeBottomLeft
        else if (pos1 == pos2.inDirection(Direction.BOTTOM_RIGHT)) prop1.isFreeTopLeft && prop2.isFreeBottomRight
        else if (pos1 == pos2.inDirection(Direction.TOP_RIGHT)) prop1.isFreeBottomLeft && prop2.isFreeTopRight
        else defaultIfNotMooreNeighbours
      }

    }


  }


  def getReachableNeighbours(pos: MatrixPosition, neighbour: Seq[MatrixPosition], defaultReachableIfNotMoore: Boolean = false): Set[FloorSprite] =
    getReachableNeighbourPositions(pos, neighbour, defaultReachableIfNotMoore).map(_.cPos).flatMap(matrix.get)


}

object FloorMatrix {



  def createFloor(cols: Int, rows: Int, defaultTile: FloorSprite): Matrix[FloorSprite] = {
    val factory: (PositionInMatrix => FloorSprite) = pos => defaultTile
    Matrix[FloorSprite](MatrixDimension(cols, rows), factory)
  }


}


