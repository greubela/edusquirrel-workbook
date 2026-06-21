package it.evadid.evacuation.io.instances

import util.TestUtil
import it.evadid.evacuation.core.io.instances.binary.RunLengthConverter
object TestRunLengthEncoder {


  def testList(arr: Array[Byte]): Unit = {
    val encoded = new RunLengthConverter().convert(arr)
    val decoded = new RunLengthConverter().reconstruct(encoded)

    println("Repetition list sizes: " + arr.length + " -> " + encoded.length + " -> " + decoded.length)

    assert(arr.length == decoded.length, "Repetition encoder: De(En(list)) != list, different sizes: " + arr.length + " <-> " + decoded.length)
    arr.indices.foreach(i => assert(arr(i) == decoded(i), "\"Repetition encoder: De(En(list)), element at " + i + ": " + arr(i) + " <-> " + decoded(i))
    )

  }


  def main(args: Array[String]): Unit = {
    testList(TestUtil.repetitionList(1, 1000000, 1000, 100).map(_.toByte).toArray)
    testList(TestUtil.repetitionList().map(_.toByte).toArray)
    testList(TestUtil.rndNumbers().map(_.toByte).toArray)
  }

}
