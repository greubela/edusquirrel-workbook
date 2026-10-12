package it.evadid.evacuation.core.io.instances.bits

import it.evadid.evacuation.core.datastructures.seqs.BitSequence
import it.evadid.evacuation.core.io.traits.encoder.IO
import it.evadid.evacuation.core.utility.DataStructureHelper

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import upickle.default.ReadWriter

case class HuffmanIO[T](prefixMap: Map[T, BitSequence]) extends IO[Seq[T], BitSequence] derives ReadWriter {

  prefixMap.values.foreach(sequence =>
    require(sequence.size > 0 && prefixMap.values.count(_.hasPrefix(sequence)) == 1,
      "Codes must be nonempty and prefix-free")
  )
  private val decodingMap = DataStructureHelper.reverseMap(prefixMap)

  override def encode(in: Seq[T]): BitSequence =
    in.map(prefixMap).foldLeft(BitSequence.empty)(_.append(_))

  override def decode(out: BitSequence): Seq[T] = {
    val res: ListBuffer[T] = ListBuffer()

    var cur = BitSequence.empty
    var rem = out.seq

    while (rem.nonEmpty) {
      cur = cur.append(rem.head)
      if (decodingMap.contains(cur)) {
        res += decodingMap(cur)
        cur = BitSequence.empty
      }
      rem = rem.tail
    }
    require(cur.size == 0, "Encoded data ends with an incomplete or unknown Huffman code")
    res.toList
  }
}

object HuffmanIO {

  private sealed trait HuffmanNode[T] derives upickle.default.ReadWriter {
    def handleEncodeRequest(mySequence: BitSequence, intoMap: mutable.Map[T, BitSequence]): Unit

    def weight: Long
  }

  private case class HuffmanNodeOuter[T](element: T, weight: Long) extends HuffmanNode[T] derives upickle.default.ReadWriter {
    override def handleEncodeRequest(mySequence: BitSequence, intoMap: mutable.Map[T, BitSequence]): Unit = {
      intoMap.put(element, mySequence)
    }
  }

  private case class HuffmanNodeInner[T](children: Seq[HuffmanNode[T]], weight: Long) extends HuffmanNode[T] derives upickle.default.ReadWriter {
    assert(children.length == 2, "Huffman inner node must have 2 children!")

    override def handleEncodeRequest(mySequence: BitSequence, intoMap: mutable.Map[T, BitSequence]): Unit = {
      children.head.handleEncodeRequest(mySequence.append(true), intoMap)
      children.tail.head.handleEncodeRequest(mySequence.append(false), intoMap)
    }

  }

  def createEncodingMap[T](frequencyMap: Map[T, Int]): Map[T, BitSequence] = {
    require(frequencyMap.values.forall(_ > 0), "Symbol frequencies must be positive")
    if frequencyMap.isEmpty then return Map.empty
    if frequencyMap.size == 1 then return Map(frequencyMap.head._1 -> BitSequence(List(false)))

    val ordering: Ordering[HuffmanNode[T]] = Ordering.by(_.weight)
    val nodes = new mutable.PriorityQueue[HuffmanNode[T]]()(using ordering.reverse)
    frequencyMap.foreachEntry((element, weight) => nodes.enqueue(HuffmanNodeOuter(element, weight)))

    while (nodes.size > 1) {
      val c1 = nodes.dequeue()
      val c2 = nodes.dequeue()
      val inner = HuffmanNodeInner[T](Seq(c1, c2), c1.weight + c2.weight)
      nodes.enqueue(inner)
      //println("nodes: " + nodes)
    }
    val res = mutable.Map[T, BitSequence]()
    nodes.head.handleEncodeRequest(BitSequence.empty, res)
    res.toMap
  }

}
