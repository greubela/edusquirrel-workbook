package it.evadid.evacuation.core.io.instances.eva

import it.evadid.evacuation.core.io.instances.basic.ByteFixedLengthIntIO
import it.evadid.evacuation.core.io.traits.encoder.IO

object MinimalPaddedByteSeqEncoder extends IO[Seq[Array[Byte]], Array[Byte]] {

  override def decode(out: Array[Byte]): Seq[Array[Byte]] = {
    val contentSize = ByteFixedLengthIntIO.decode(out.slice(0, 4))
    val byteSize = out(4).toInt
    val content = out.slice(5, out.length).grouped(byteSize).toList

    assert(contentSize == content.length, "Missing Elements from content, expected: " + contentSize + ", but was: " + content.length + "!")
    content
  }

  override def encode(byteArrays: Seq[Array[Byte]]): Array[Byte] = {
    val contentSize = ByteFixedLengthIntIO.encode(byteArrays.length)
    val maxBytes: Int = byteArrays.map(_.length).max
    val bytesContent: Array[Byte] = byteArrays.flatMap(_.reverse.padTo(maxBytes, 0.asInstanceOf[Byte]).reverse).toArray
    contentSize ++ Array(maxBytes.toByte) ++ bytesContent
  }


}
