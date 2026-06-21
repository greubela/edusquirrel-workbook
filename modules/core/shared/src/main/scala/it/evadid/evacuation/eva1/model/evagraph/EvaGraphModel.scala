package it.evadid.evacuation.eva1.model.evagraph

import it.evadid.evacuation.core.datastructures.graphs.{ImmutableGraphListImpl, PositionableEdge, WeightedNeighbourStructure}

case class EvaGraphModel(nodesList: List[Router], edgesList: List[PositionableEdge[Router, ConnectionInfo]]) extends ImmutableGraphListImpl[Router, ConnectionInfo, PositionableEdge[Router, ConnectionInfo]](nodesList, edgesList) with WeightedNeighbourStructure[Router, ConnectionInfo, PositionableEdge[Router, ConnectionInfo]] {

  def +=(start: Router, dest: Router, parallelism: Integer): EvaGraphModel = {
    val graph = this.addEdge(start, dest, ConnectionInfo(parallelism, ConnectionInfo.getConnectionDelayFromRouterDist(start.distTo(dest))))
    new EvaGraphModel(graph.nodes.toList, graph.edges.toList)
  }

  override def getDistFromEdge(edge: PositionableEdge[Router, ConnectionInfo]): Double = edge.content.delayInMs

  //override protected def createInstance(nodes: Seq[Router], edges: Seq[Edge[Router, ConnectionInfo]]): EvaGraphModel=  new EvaGraphModel(nodes.toList, edges.toList)

  override protected def createInstance(nodes: Seq[Router], edges: Seq[PositionableEdge[Router, ConnectionInfo]]): this.type = new EvaGraphModel(nodes.toList, edges.toList).asInstanceOf[this.type]

  override def createEdge(start: Router, dest: Router, info: ConnectionInfo): PositionableEdge[Router, ConnectionInfo] = new PositionableEdge(start, dest, info)


}


object EvaGraphModel {

  def createInstance(nodes: Seq[Router], edges: Seq[PositionableEdge[Router, ConnectionInfo]]): EvaGraphModel = new EvaGraphModel(nodes.toList, edges.toList)

  def emptyGraph(): EvaGraphModel = new EvaGraphModel(List(), List())

  def createQuickTest(): EvaGraphModel = {

    val left = Router((100, 100), 0, 10000, true)
    val right = Router((700, 100), 0, 10000, true)

    val r1 = Router((200, 50), 10)
    val r2 = Router((200, 100))

    val r3 = Router((400, 50), 10)
    val r4 = Router((400, 100), 2)

    val r5 = Router((600, 50), 10)
    val r6 = Router((600, 100))

    val r7 = Router((300, 150), 10)
    val r8 = Router((500, 150), 10)

    val r9 = Router((300, 100))
    val r10 = Router((500, 100))

    val graph = emptyGraph()
      .+=(r1, r2, 1)
      .+=(r3, r4, 1)
      .+=(r5, r6, 1)

      .+=(r7, r9, 1)
      .+=(r8, r10, 1)

      .+=(left, r2, 2)
      .+=(right, r6, 2)

      .+=(r2, r9, 2)
      .+=(r9, r4, 3)
      .+=(r4, r10, 3)
      .+=(r10, r6, 2)

    val savePositions = graph.nodes.filter(_.isExit)
    println("### created graph -- nodes: " + graph.nodes.size + ", edges: " + graph.edges.size + ", save positions: " + savePositions)

    graph

  }

  def createSportsHall(offsetX: Int = 0, offsetY: Int = 0): EvaGraphModel = {

    val sportHallNode1 = Router((offsetX + 75 + 0 * 90, offsetY + 120), 4)
    val sportHallNode2 = Router((offsetX + 75 + 1 * 90, offsetY + 120), 4)
    val sportHallNode3 = Router((offsetX + 75 + 2 * 90, offsetY + 120), 4)

    val connectionNode = Router((offsetX + 75 + 3 * 90, offsetY + 120))

    val topNode = Router((offsetX + 75 + 3 * 90, offsetY + 120 - 90), 5)
    val bottomNode = Router((offsetX + 75 + 3 * 90, offsetY + 120 + 90), 5)

    val middleHallwayNode = Router((offsetX + 75 + 4 * 90, offsetY + 120))
    val rightHallwayNode = Router((offsetX + 75 + 5 * 90, offsetY + 120))

    val exitNode = Router((offsetX + 75 + 6 * 90, offsetY + 120), 0, 10000, true)

    val sportHallNode1t = Router((offsetX + 75 + 0 * 90, offsetY + 120 - 90), 3)
    val sportHallNode1b = Router((offsetX + 75 + 0 * 90, offsetY + 120 + 90), 3)

    val sportHallNode2t = Router((offsetX + 75 + 1 * 90, offsetY + 120 - 90), 3)
    val sportHallNode2b = Router((offsetX + 75 + 1 * 90, offsetY + 120 + 90), 3)

    val sportHallNode3t = Router((offsetX + 75 + 2 * 90, offsetY + 120 - 90), 3)
    val sportHallNode3b = Router((offsetX + 75 + 2 * 90, offsetY + 120 + 90), 3)


    emptyGraph()
      .+=(sportHallNode1, sportHallNode2, 8)
      .+=(sportHallNode2, sportHallNode3, 8)
      .+=(sportHallNode3, connectionNode, 8)

      .+=(connectionNode, middleHallwayNode, 8)
      .+=(middleHallwayNode, rightHallwayNode, 8)
      .+=(rightHallwayNode, exitNode, 8)
      .+=(topNode, connectionNode, 2)
      .+=(bottomNode, connectionNode, 2)

      .+=(sportHallNode1t, sportHallNode1, 10)
      .+=(sportHallNode1b, sportHallNode1, 10)

      .+=(sportHallNode2t, sportHallNode2, 10)
      .+=(sportHallNode2b, sportHallNode2, 10)

      .+=(sportHallNode3t, sportHallNode3, 10)
      .+=(sportHallNode3b, sportHallNode3, 10)
  }


}
