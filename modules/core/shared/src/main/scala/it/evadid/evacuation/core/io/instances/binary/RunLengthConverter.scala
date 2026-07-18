package it.evadid.evacuation.core.io.instances.binary

import it.evadid.evacuation.core.io.traits.converter.Converter
import it.evadid.evacuation.core.utility.BinaryUtility

import scala.collection.mutable.ListBuffer

class RunLengthConverter extends Converter[Array[Byte]] {

  // ToDo
  override def convert(in: Array[Byte]): Array[Byte] = {

    var resBuffer: ListBuffer[Byte] = ListBuffer()

    if (in.isEmpty) {
      in
    } else {
      var lastByte: Byte = in(0)
      var countLastByte = 0

      def pushToBuffer(): Unit = {
        resBuffer += countLastByte.toByte
        resBuffer += lastByte
      }

      in.zipWithIndex.foreach(tup => {
        val curByte = tup._1
        val index = tup._2

        if (lastByte == curByte && countLastByte < 255) {
          countLastByte = countLastByte + 1
        } else {
          pushToBuffer()

          lastByte = curByte
          countLastByte = 1
        }

      })
      pushToBuffer()
      resBuffer.toArray
    }
  }


  override def reconstruct(out: Array[Byte]): Array[Byte] = {
    assert(out.length % 2 == 0, "out has no RunLength format: Number of bytes must be even!")

    val resBuffer: ListBuffer[Byte] = ListBuffer()

    out.sliding(2, 2).foreach(arrTup => {
      val amount = BinaryUtility.byteToUInt(arrTup(0))
      val byte = arrTup(1)
      0.until(amount).foreach(nr => resBuffer += byte)

    })

    resBuffer.toArray

  }

}
