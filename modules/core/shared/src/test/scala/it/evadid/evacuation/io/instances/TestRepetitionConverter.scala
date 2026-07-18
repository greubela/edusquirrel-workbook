package it.evadid.evacuation.io.instances

import it.evadid.evacuation.core.datastructures.seqs.BitSequence
import it.evadid.evacuation.core.io.instances.bits.RepetitionConverter
import util.TestUtil

object TestRepetitionConverter {

  //Todo: Change to proper Run Length Encoding with repetition for runs

  def testList(list: List[BitSequence]): Unit = {
    val encoded = RepetitionConverter.convert(list)
    val decoded = RepetitionConverter.reconstruct(encoded)

    println("Repetition list sizes: " + list.size + " -> " + encoded.size + " -> " + decoded.size + "(bit sizes: " + list.map(_.size).sum + " -> " + encoded.map(_.size).sum + " -> " + decoded.map(_.size).sum + ")")

    assert(list.size == decoded.size, "Repetition encoder: De(En(list)) != list, different sizes: " + list.size + " <-> " + decoded.size)
    list.indices.foreach(i => assert(list(i) == decoded(i), "\"Repetition encoder: De(En(list)), element at " + i + ": " + list(i) + " <-> " + decoded(i))
    )

  }


  def main(args: Array[String]): Unit = {
    testList(TestUtil.repetitionList().map(BitSequence(_)).toList)
    testList(TestUtil.rndNumbers().map(BitSequence(_)).toList)
  }


}
