package it.evadid.evacuation.io.instances

import it.evadid.evacuation.core.io.instances.binary.commonsadapter.{ NayukiAdapter}
import util.TestUtil

object TestNayukiDeflateConverter {

  def testList(arr: Array[Byte]): Unit = {
    val encoded = new NayukiAdapter().convert(arr)
    println("encoded: " + encoded.length)
    val decoded = new NayukiAdapter().reconstruct(encoded)

    println("Bzip list sizes: " + arr.length + " -> " + encoded.length + " -> " + decoded.length)

    assert(arr.length == decoded.length, "NayukiAdapter encoder: De(En(list)) != list, different sizes: " + arr.length + " <-> " + decoded.length)
    arr.indices.foreach(i => assert(arr(i) == decoded(i), "\"Repetition encoder: De(En(list)), element at " + i + ": " + arr(i) + " <-> " + decoded(i))
    )

  }


  def main(args: Array[String]): Unit = {
    testList(TestUtil.repetitionList(1, 1000000, 1000, 100).map(_.toByte).toArray)
    testList(TestUtil.repetitionList().map(_.toByte).toArray)
    testList(TestUtil.rndNumbers().map(_.toByte).toArray)
  }

}
