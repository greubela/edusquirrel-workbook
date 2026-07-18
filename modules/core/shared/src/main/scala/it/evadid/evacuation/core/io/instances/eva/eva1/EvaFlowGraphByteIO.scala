package it.evadid.evacuation.core.io.instances.eva.eva1

import it.evadid.evacuation.core.io.instances.basic.ByteFixedLengthIntIO
import it.evadid.evacuation.core.io.traits.encoder.IO.ByteIO
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphModel

import scala.collection.mutable.ListBuffer


object EvaFlowGraphByteIO extends ByteIO[EvaGraphModel] {

  def decode(out: Array[Byte]): EvaGraphModel = {
    assert(out.length >= 4, "out needs to at least contain the number of nodes!")
    val nodeSize = ByteFixedLengthIntIO.decode(out.slice(0, 4))
    val endIndexOfNodes = 4 + nodeSize * RouterByteConverter.BYTES_PER_NODE
    val edgeSize = (out.length - endIndexOfNodes) / EdgeByteConverter.BYTES_PER_EDGE

    assert(out.length >= endIndexOfNodes, "needs to have enough bytes for nodes!")
    val bytesForNodes = out.slice(4, endIndexOfNodes)
    val nodes = bytesForNodes.grouped(RouterByteConverter.BYTES_PER_NODE).map(RouterByteConverter.decode).toList

    val bytesForEdges = out.slice(endIndexOfNodes, endIndexOfNodes + edgeSize * EdgeByteConverter.BYTES_PER_EDGE)
    val conv = new EdgeByteConverter(nodes)
    val edges = bytesForEdges.grouped(EdgeByteConverter.BYTES_PER_EDGE).map(conv.decode).toList

    new EvaGraphModel(nodes, edges)
  }

  override def encode(in: EvaGraphModel): Array[Byte] = {
    val buf = new ListBuffer[Byte]()

    val nodeList = in.nodes.toList

    buf addAll ByteFixedLengthIntIO.encode(nodeList.size)
    in.nodes.foreach(node => buf addAll RouterByteConverter.encode(node))

    val conv = new EdgeByteConverter(nodeList)
    in.edges.foreach(edge => buf addAll conv.encode(edge))

    buf.toArray

  }


  def main(args: Array[String]): Unit = {

    val graph = EvaGraphModel.createQuickTest()
    val bytes = encode(graph)

    println("graph: " + graph + ", bytes: " + bytes.mkString(","))

    val graphR = decode(encode(graph))

    println(String.valueOf(graph) + " --> " + graphR)

    val bytesR = encode(decode(bytes))

    println(bytes.mkString(",") + " --> " + bytesR.mkString(","))


  }

}
