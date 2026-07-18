package it.evadid.evacuation.core.io.instances.eva.eva1

import it.evadid.core.datastructures.graph.PositionableEdge
import it.evadid.evacuation.core.io.instances.basic.{ByteFixedLengthIntIO, ByteFixedLengthShortIO}
import it.evadid.evacuation.core.io.instances.eva.eva1.EdgeByteConverter.BYTES_PER_EDGE
import it.evadid.evacuation.core.io.traits.encoder.IO.ByteIO
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaEdge
import it.evadid.evacuation.eva1.model.evagraph.{ConnectionInfo, Router}

import scala.collection.mutable.ListBuffer

class EdgeByteConverter(nodes: List[Router]) extends ByteIO[EvaEdge] {


  override def decode(out: Array[Byte]): EvaEdge = {

   // println("### convert edge: " + out.mkString(","))

    assert(out.length <= BYTES_PER_EDGE, "insufficient information to reconstruct edge (" + BYTES_PER_EDGE + " needed, " + out.length + " existing)")

    val indexA = ByteFixedLengthShortIO.decode(out.slice(0, 2))
    val indexB = ByteFixedLengthShortIO.decode(out.slice(2, 4))

    assert(nodes.size > indexA && nodes.size > indexB, "index need be in range of node list (indexA: " + indexA + ", indexB: " + indexB + ", size: " + nodes.size + ")")

    val par = ByteFixedLengthIntIO.decode(out.slice(4, 8))
    val del = ByteFixedLengthIntIO.decode(out.slice(8, 12))

    new PositionableEdge(nodes(indexA), nodes(indexB), ConnectionInfo(par, del))
  }

  override def encode(in: EvaEdge): Array[Byte] = {

    val buf = new ListBuffer[Byte]()

    assert(nodes.contains(in.start) && nodes.contains(in.dest), "start and dest of edge must be contained in node list!")
    val indexA = nodes.indexOf(in.start)
    val indexB = nodes.indexOf(in.dest)
    assert(indexA <= Short.MaxValue && indexB <= Short.MaxValue, "index must be as short for this conversion")

    buf addAll ByteFixedLengthShortIO.encode(indexA.toShort)
    buf addAll ByteFixedLengthShortIO.encode(indexB.toShort)

    buf addAll ByteFixedLengthIntIO.encode(in.content.maxParallelism)
    buf addAll ByteFixedLengthIntIO.encode(in.content.delayInMs)

    buf.toArray

  }
}
object EdgeByteConverter{

  val BYTES_PER_EDGE = 12

}