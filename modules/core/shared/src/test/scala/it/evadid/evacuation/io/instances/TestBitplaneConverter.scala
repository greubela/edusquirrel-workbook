package it.evadid.evacuation.io.instances

import it.evadid.evacuation.core.datastructures.seqs.BitSequence
import it.evadid.evacuation.core.io.instances.binary.BitplaneConverter

object TestBitplaneConverter {


  def main(args: Array[String]): Unit = {
    val conv = new BitplaneConverter()

    val arr: Array[Byte] = List(200, 127, 100, 5, 0, 4).map(_.toByte).toArray

    val converted = conv.convert(arr)
    val original = conv.reconstruct(converted)

    printArr("original", arr)
    printArr("converted", converted)
    printArr("reconstruced", original)

    0.until(8).foreach(curPlane => {
      println("Bitplane " + curPlane + ": " + BitSequence.getBitplane(arr.map(BitSequence.paddedNumber(_, 8)).toIndexedSeq, curPlane))
    })
  }

  def printArr(name: String, arr: Array[Byte]): Unit = {
    println(name + ": ")
    println("\t" + arr.mkString(" "))
    println("\t" + arr.map(BitSequence.paddedNumber(_, 8)).toIndexedSeq.mkString(" "))
  }

}
