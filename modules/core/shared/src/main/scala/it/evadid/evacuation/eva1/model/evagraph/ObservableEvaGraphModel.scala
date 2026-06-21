package it.evadid.evacuation.eva1.model.evagraph

import it.evadid.evacuation.core.datastructures.graphs.Position._
import it.evadid.evacuation.core.datastructures.graphs._
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.RouterOrEdge

class ObservableEvaGraphModel extends ObservableGraph[Router, ConnectionInfo] with WeightedNeighbourStructure[Router, ConnectionInfo, Edge[Router, ConnectionInfo]] {

  def clear(): Unit = {
    nodes.toList.foreach(nodes -= _)
  }

  def replaceEdgeSpeeds(newSpeedInPxS: Integer): Unit = {
    val newEdges = edges.map(edge => {
      val dist = edge.start.distTo(edge.dest)
      val newDelay = dist * 1000.0 / newSpeedInPxS
      val newInfo = ConnectionInfo(edge.content.maxParallelism, newDelay)
      Edge[Router, ConnectionInfo](edge.start, edge.dest, newInfo)
    })

    clearEdges()

    newEdges.foreach(+=>)
  }

  override def getDistFromEdge(edge: Edge[Router, ConnectionInfo]): Double = edge.content.delayInMs // edge.start.pos.distTo(edge.dest.pos)

  def ensureRouterAtPosition(pos: Position): Router =
    getRouterAtPosition(pos).getOrElse({
      val newRouter = Router(pos)
      nodes += newRouter
      newRouter
    })

  def getRouterAtPosition(pos: Position): Option[Router] = nodes.find(_.pos == pos)

  def +=(start: Router, dest: Router, parallelism: Int = 1): Unit = addStandardEdge(start, dest, parallelism)

  def +=(start: Position, dest: Position, parallelism: Int): Unit = addStandardEdge(start, dest, parallelism)

  def addStandardEdge(start: Router, dest: Router, parallelism: Int = 1): Unit = {
    val dist = ConnectionInfo.getConnectionDelayFromRouterDist(start.distTo(dest))
    val connectionInfo = ConnectionInfo(parallelism, dist)
    this += Edge(start, dest, connectionInfo)
  }

  def addStandardEdge(start: Position, dest: Position, parallelism: Int): Unit = {
    val routerStart = ensureRouterAtPosition(start)
    val routerDest = ensureRouterAtPosition(dest)
    addStandardEdge(routerStart, routerDest, parallelism)
  }

  def getClosestRouter(pos: Position, exclude: Option[Router] = None): (Router, Double) = {
    if (exclude.isEmpty) Position.getNearestElement(nodes, pos)
    else Position.getNearestElements(nodes, pos, 2).filterNot(_._1 == exclude.get).head
  }


}

object ObservableEvaGraphModel {


  def createEmptyGraph(): ObservableEvaGraphModel = new ObservableEvaGraphModel()

  def fillToSportHall(graph: ObservableEvaGraphModel, offsetX: Int = 0, offsetY: Int = 0): Unit = {

    graph.clear()

    val sportHallNode1 = Router((offsetX + 75 + 0 * 90, offsetY + 120), 4)
    val sportHallNode2 = Router((offsetX + 75 + 1 * 90, offsetY + 120), 4)
    val sportHallNode3 = Router((offsetX + 75 + 2 * 90, offsetY + 120), 4)

    val connectionNode = Router((offsetX + 75 + 3 * 90, offsetY + 120))

    val topNode = Router((offsetX + 75 + 3 * 90, offsetY + 120 - 90), 5)
    val bottomNode = Router((offsetX + 75 + 3 * 90, offsetY + 120 + 90), 5)

    val middleHallwayNode = Router((offsetX + 75 + 4 * 90, offsetY + 120))
    val rightHallwayNode = Router((offsetX + 75 + 5 * 90, offsetY + 120))

    val exitNode = Router((offsetX + 75 + 6 * 90, offsetY + 120), 0, 10000, true)


    graph += (sportHallNode1, sportHallNode2, 8)
    graph += (sportHallNode2, sportHallNode3, 8)
    graph += (sportHallNode3, connectionNode, 8)


    graph += (connectionNode, middleHallwayNode, 8)
    graph += (middleHallwayNode, rightHallwayNode, 8)

    graph += (rightHallwayNode, exitNode, 8)

    graph += (topNode, connectionNode, 2)
    graph += (bottomNode, connectionNode, 2)

    val sportHallNode1t = Router((offsetX + 75 + 0 * 90, offsetY + 120 - 90), 3)
    val sportHallNode1b = Router((offsetX + 75 + 0 * 90, offsetY + 120 + 90), 3)

    val sportHallNode2t = Router((offsetX + 75 + 1 * 90, offsetY + 120 - 90), 3)
    val sportHallNode2b = Router((offsetX + 75 + 1 * 90, offsetY + 120 + 90), 3)

    val sportHallNode3t = Router((offsetX + 75 + 2 * 90, offsetY + 120 - 90), 3)
    val sportHallNode3b = Router((offsetX + 75 + 2 * 90, offsetY + 120 + 90), 3)

    graph += (sportHallNode1t, sportHallNode1, 10)
    graph += (sportHallNode1b, sportHallNode1, 10)

    graph += (sportHallNode2t, sportHallNode2, 10)
    graph += (sportHallNode2b, sportHallNode2, 10)

    graph += (sportHallNode3t, sportHallNode3, 10)
    graph += (sportHallNode3b, sportHallNode3, 10)

  }

  def fillToQuickTest(graph: ObservableEvaGraphModel): Unit = {

    graph.clear()


    val left = Router((100, 100), 0, 10000, true)
    val right = Router((700, 100), 0, 10000, true)


    graph += (Router((200, 50), 10), Router((200, 100)), 1)
    graph += (Router((400, 50), 10), Router((400, 100)), ConnectionInfo(2, 1000))
    graph += (Router((600, 50), 10), Router((600, 100)), 1)

    graph += (Router((300, 150), 10), Router((300, 100)), 1)
    graph += (Router((500, 150), 10), Router((500, 100)), 1)

    graph += (left, graph.getRouterAtPosition((200, 100)).get, ConnectionInfo(2, 2000))
    graph += (right, graph.getRouterAtPosition((600, 100)).get, ConnectionInfo(2, 2000))

    graph += Edge(graph.ensureRouterAtPosition(200, 100), graph.ensureRouterAtPosition(300, 100), ConnectionInfo(2, 2000))
    graph += Edge(graph.ensureRouterAtPosition(300, 100), graph.ensureRouterAtPosition(400, 100), ConnectionInfo(3, 2000))
    graph += Edge(graph.ensureRouterAtPosition(400, 100), graph.ensureRouterAtPosition(500, 100), ConnectionInfo(3, 2000))
    graph += Edge(graph.ensureRouterAtPosition(500, 100), graph.ensureRouterAtPosition(600, 100), ConnectionInfo(2, 2000))


  }

  def fillToQuickTest2(graph: ObservableEvaGraphModel): Unit = {
    graph.clear()

    val r1: Router = Router((100, 100), 2)
    val r2: Router = Router((200, 200), 1)
    val r3: Router = Router((300, 200), 1)
    val r4: Router = Router((400, 100), isExit = true)

    graph += (r1, r2, ConnectionInfo(2, 750))
    graph += (r2, r3, ConnectionInfo(2, 500))
    graph += (r3, r4, ConnectionInfo(3, 750))

  }

  def fillToHalfMindGraph(graph: ObservableEvaGraphModel): Unit = {
    graph.clear()

    graph += ((620 / 2, 160 / 2), (560 / 2, 160 / 2), 1)
    graph += ((620 / 2, 160 / 2), (620 / 2, 215 / 2), 1)
    graph += ((620 / 2, 215 / 2), (675 / 2, 200 / 2), 1)
    graph += ((620 / 2, 215 / 2), (675 / 2, 240 / 2), 1)
    graph += ((620 / 2, 215 / 2), (620 / 2, 410 / 2), 1)
    graph += ((620 / 2, 410 / 2), (560 / 2, 410 / 2), 1)
    graph += ((620 / 2, 410 / 2), (560 / 2, 450 / 2), 1)
    graph += ((620 / 2, 410 / 2), (560 / 2, 450 / 2), 1)
    graph += ((620 / 2, 410 / 2), (620 / 2, 520 / 2), 1)

    // left (purple)
    graph += ((475 / 2, 660 / 2), (620 / 2, 520 / 2), 1)
    graph += ((475 / 2, 660 / 2), (435 / 2, 630 / 2), 1)
    graph += ((475 / 2, 660 / 2), (440 / 2, 700 / 2), 1)
    graph += ((440 / 2, 700 / 2), (485 / 2, 720 / 2), 1)
    graph += ((440 / 2, 700 / 2), (450 / 2, 750 / 2), 1)
    graph += ((440 / 2, 700 / 2), (290 / 2, 850 / 2), 1)

    graph += ((290 / 2, 850 / 2), (250 / 2, 800 / 2), 1)
    graph += ((290 / 2, 850 / 2), (250 / 2, 800 / 2), 1)
    graph += ((290 / 2, 850 / 2), (250 / 2, 880 / 2), 1)

    graph += ((250 / 2, 880 / 2), (285 / 2, 920 / 2), 1)

    // right (blue)
    graph += ((650 / 2, 490 / 2), (620 / 2, 520 / 2), 1)

    graph += ((735 / 2, 520 / 2), (620 / 2, 520 / 2), 1)
    graph += ((735 / 2, 520 / 2), (710 / 2, 570 / 2), 1)
    graph += ((735 / 2, 520 / 2), (750 / 2, 570 / 2), 1)

    graph += ((888 / 2, 520 / 2), (735 / 2, 520 / 2), 1)
    graph += ((888 / 2, 520 / 2), (870 / 2, 480 / 2), 1)
    graph += ((888 / 2, 520 / 2), (900 / 2, 480 / 2), 1)
    graph += ((888 / 2, 520 / 2), (1050 / 2, 520 / 2), 1)

    graph += ((1050 / 2, 520 / 2), (1020 / 2, 560 / 2), 1)
    graph += ((1050 / 2, 520 / 2), (1060 / 2, 560 / 2), 1)
    graph += ((1050 / 2, 520 / 2), (1190 / 2, 520 / 2), 1)
    graph += ((1190 / 2, 520 / 2), (1190 / 2, 470 / 2), 1)

    // escape (green)

    graph += ((1190 / 2, 520 / 2), (1250 / 2, 520 / 2), 1)
    graph += ((250 / 2, 880 / 2), (130 / 2, 1000 / 2), 1)
    graph += ((620 / 2, 160 / 2), (620 / 2, 40 / 2), 1)

  }


}

