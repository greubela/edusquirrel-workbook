package it.evadid.evacuation.core.io.instances.bits

import it.evadid.evacuation.core.datastructures.seqs.BitSequence
import it.evadid.evacuation.core.io.traits.encoder.IO
import it.evadid.evacuation.core.utility.DataStructureHelper

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

case class HuffmanIO[T](prefixMap: Map[T, BitSequence]) extends IO[Seq[T], BitSequence] {

  prefixMap.values.foreach(sequence =>
    assert(prefixMap.values.count(_.hasPrefix(sequence)) == 1, "Invalid Prefix Map: " + sequence + " is Prefix of another entry in: " + prefixMap + "!")
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

    res.toList
  }
}

object HuffmanIO {

  private trait HuffmanNode[T] {
    def handleEncodeRequest(mySequence: BitSequence, intoMap: mutable.Map[T, BitSequence]): Unit

    def weight: Int
  }

  private case class HuffmanNodeOuter[T](element: T, weight: Int) extends HuffmanNode[T] {
    override def handleEncodeRequest(mySequence: BitSequence, intoMap: mutable.Map[T, BitSequence]): Unit = {
      intoMap.put(element, mySequence)
    }
  }

  private case class HuffmanNodeInner[T](children: Seq[HuffmanNode[T]], weight: Int) extends HuffmanNode[T] {
    assert(children.length == 2, "Huffman inner node must have 2 children!")

    override def handleEncodeRequest(mySequence: BitSequence, intoMap: mutable.Map[T, BitSequence]): Unit = {
      children.head.handleEncodeRequest(mySequence.append(true), intoMap)
      children.tail.head.handleEncodeRequest(mySequence.append(false), intoMap)
    }

  }

  def createEncodingMap[T](frequencyMap: Map[T, Int]): Map[T, BitSequence] = {

    val ordering: Ordering[HuffmanNode[T]] = Ordering.by(_.weight)
    val nodes = new mutable.PriorityQueue[HuffmanNode[T]]()(ordering.reverse)
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
