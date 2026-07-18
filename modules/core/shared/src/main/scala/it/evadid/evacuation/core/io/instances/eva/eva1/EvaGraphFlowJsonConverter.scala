package it.evadid.evacuation.core.io.instances.eva.eva1

import it.evadid.core.datastructures.graph.{PositionableEdge}
import it.evadid.evacuation.core.datastructures.graphs.{Position}
import it.evadid.evacuation.core.io.traits.encoder.IO
import it.evadid.evacuation.eva1.model.evagraph.{ConnectionInfo, EvaGraphModel, Router}
import upickle.default.{macroRW, ReadWriter => RW, _}

import scala.io.{BufferedSource, Source}

object EvaGraphFlowJsonConverter extends IO[EvaGraphModel, String] {

  case class SimpleEdge(start: Router, dest: Router, content: ConnectionInfo)

  case class GraphData(nodes: List[Router], edges: List[SimpleEdge])

  /*private def simpleConverter: IO[GraphData, String] = new IO[GraphData, String] {
    override def decode(out: String): GraphData = {
      Unpickle[GraphData].fromString(out).get
    }

    override def encode(in: GraphData): String = {
      val str = Pickle.intoString(in)
      str
    }
  }*/

  implicit val rp: RW[Position] = macroRW
  implicit val ri: RW[ConnectionInfo] = macroRW
  implicit val rr: RW[Router] = macroRW
  implicit val rw: RW[SimpleEdge] = macroRW
  implicit val rgd: RW[GraphData] = macroRW

  private def decodeSimple(out: String): GraphData = {
    read[GraphData](out)
  }

  private def encodeSimple(in: GraphData): String = {
    write[GraphData](in)
  }

  override def encode(in: EvaGraphModel): String = {
    val res = GraphData(in.nodesList, in.edgesList.map(e => SimpleEdge(e.start, e.dest, e.content)))
    encodeSimple(res)
  }

  override def decode(out: String): EvaGraphModel = {
    val res = decodeSimple(out)
    val edges = res.edges.map(e => new PositionableEdge(e.start, e.dest, e.content))
    EvaGraphModel(res.nodes, edges)
  }

  def main(args: Array[String]): Unit = {
    val graph: EvaGraphModel = EvaGraphModel.createQuickTest()

    val encoded = encode(graph)
    println("encoded: " + encoded)
    val r = encode(decode(encoded))
    val s = decode(encode(graph))
    println(String.valueOf(graph) + " --> " + s)
    println(encoded + " --> " + r)

    val fileSource: String = "E:\\Nextcloud\\Andre Home\\Arbeit\\Dr\\Projects\\Evakuierung\\Git Programmier Projekt\\EvaProject\\dist\\material\\alltest.evaG"
    val input: BufferedSource = Source.fromFile(fileSource)
    val inputStr: String = input.mkString
    val res = decode(inputStr)
    println("res: " + res)
  }

}
