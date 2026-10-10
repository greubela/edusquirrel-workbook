package it.evadid.evacuation.core.io.instances.eva.eva1

import it.evadid.core.datastructures.graph.PositionableEdge
import it.evadid.evacuation.core.datastructures.graphs.Position
import it.evadid.evacuation.core.graphic.model.{EvaImage, EvaFileInformation}
import it.evadid.evacuation.core.io.instances.eva.eva1.EvaGraphFlowJsonConverter.{GraphData, SimpleEdge}
import it.evadid.evacuation.eva1.model.evagraph.*
import munit.FunSuite
import upickle.default.*

class EvaGraphJsonSpec extends FunSuite {
  private val a = Router(Position(-3, 4), 2, 10, false)
  private val b = Router(Position(9, 10), 0, 100, true)
  private val edge = SimpleEdge(a, b, ConnectionInfo(1, 250))
  private def roundTrip[T: ReadWriter](value: T): Unit = {
    assertEquals(read[T](write(value)), value)
    assertEquals(readBinary[T](writeBinary(value)), value)
  }
  test("graph transfer records have default codecs without importing converter givens") {
    roundTrip(edge)
    roundTrip(GraphData(List(a, b), List(edge)))
    roundTrip(GraphData(Nil, Nil))
  }
  test("graph JSON round trips preserve nodes, isolated nodes, directions and edge metadata") {
    val isolated = Router(Position(30, 40))
    val graph = EvaGraphModel(List(a, b, isolated), List(new PositionableEdge(a, b, edge.content), new PositionableEdge(b, a, ConnectionInfo(2, 500))))
    val encoded = EvaGraphFlowJsonConverter.encode(graph)
    val decoded = EvaGraphFlowJsonConverter.decode(encoded)
    assertEquals(decoded.nodesList, graph.nodesList)
    assertEquals(decoded.edgesList.map(e => (e.start, e.dest, e.content)), graph.edgesList.map(e => (e.start, e.dest, e.content)))
    assertEquals(ujson.read(EvaGraphFlowJsonConverter.encode(decoded)), ujson.read(encoded))
  }
  test("empty graphs round trip through the legacy JSON converter") {
    val decoded = EvaGraphFlowJsonConverter.decode("""{"nodes":[],"edges":[]}""")
    assertEquals(decoded.nodesList, Nil)
    assertEquals(decoded.edgesList, Nil)
  }
  test("legacy graph JSON fields remain readable") {
    val json = """{"nodes":[{"pos":{"x":0,"y":0},"initCapacity":1,"maxCapacity":10,"isExit":false}],"edges":[]}"""
    val decoded = EvaGraphFlowJsonConverter.decode(json)
    assertEquals(decoded.nodesList, List(Router(Position(0, 0), 1, 10, false)))
    assertEquals(ujson.read(EvaGraphFlowJsonConverter.encode(decoded)), ujson.read(json))
  }
  test("image path descriptions round trip through both the sealed root and concrete codec") {
    val path = EvaImage.fromPath("images/学校.svg")
    roundTrip(path)
    roundTrip[EvaImage](path)
  }
  test("embedded image descriptions preserve filename, type and signed file bytes") {
    val embedded = EvaImage.fromData(EvaFileInformation("floor.png", Array[Byte](0, -1, 127, -128)))
    val decoded = List(read[EvaImage](write[EvaImage](embedded)), readBinary[EvaImage](writeBinary[EvaImage](embedded)),
      read[EvaImage.DataBasedEvaImage](write(embedded)), readBinary[EvaImage.DataBasedEvaImage](writeBinary(embedded)))
    decoded.foreach { image =>
      val data = image.asInstanceOf[EvaImage.DataBasedEvaImage]
      assertEquals(data.fullFileName, "floor.png")
      assertEquals(data.fileType, "png")
      assertEquals(data.data.toList, embedded.data.toList)
    }
  }
  test("positionable edges and graph models have default codecs with restored graph behavior") {
    val positioned = new PositionableEdge(a, b, edge.content)
    roundTrip(positioned)
    val graph = EvaGraphModel(List(a, b), List(positioned))
    roundTrip(graph)
    val decoded = readBinary[EvaGraphModel](writeBinary(graph))
    assertEquals(decoded.dirEdgesBetween(a, b).toList, List(positioned))
    assertEquals(decoded.edgesList.head.pos, positioned.pos)
    assertEquals(decoded.getDistFromEdge(decoded.edgesList.head), 250.0)
  }

}
