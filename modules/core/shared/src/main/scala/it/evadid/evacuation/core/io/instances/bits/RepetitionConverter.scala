package it.evadid.evacuation.core.io.instances.bits

import it.evadid.evacuation.core.datastructures.seqs.BitSequence
import it.evadid.evacuation.core.io.traits.converter.Converter

import scala.collection.mutable.ListBuffer

object RepetitionConverter extends Converter[Seq[BitSequence]] {

  override def convert(in: Seq[BitSequence]): Seq[BitSequence] = if (in.isEmpty) in else {
    val buf: ListBuffer[BitSequence] = ListBuffer()

    var last = in.head
    var counter: Long = 1L
    var rem = in.tail

    def newRun(): Unit = {
      buf.append(BitSequence(counter))
      buf.append(last)
      if (rem.nonEmpty) {
        last = rem.head
        counter = 1L
      }
    }

    while (rem.nonEmpty) {
      if (rem.head == last) counter += 1
      else newRun()
      rem = rem.tail
    }
    newRun()

    buf.toList
  }

  override def reconstruct(out: Seq[BitSequence]): Seq[BitSequence] = {
    assert(out.size % 2 == 0, "Repetition List should have even size!")

    var rem = out
    var res: ListBuffer[BitSequence] = ListBuffer()
    while (rem.nonEmpty) {
      val rep = rem.head
      val value = rem.tail.head
      1.to(rep.toInt).toList.foreach(_ => res += value)
      rem = rem.tail.tail
    }

    res.toList

  }
}
