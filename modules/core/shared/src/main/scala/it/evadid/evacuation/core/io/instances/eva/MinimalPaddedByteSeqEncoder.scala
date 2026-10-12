package it.evadid.evacuation.core.io.instances.eva

import it.evadid.evacuation.core.io.instances.basic.ByteFixedLengthIntIO
import it.evadid.evacuation.core.io.traits.encoder.IO

object MinimalPaddedByteSeqEncoder extends IO[Seq[Array[Byte]], Array[Byte]] {

  override def decode(out: Array[Byte]): Seq[Array[Byte]] = {
    require(out.length >= 5, "Missing padded sequence header")
    val contentSize = ByteFixedLengthIntIO.decode(out.slice(0, 4))
    val byteSize = out(4) & 0xff
    require(contentSize >= 0, "Negative element count")
    require(out.length.toLong - 5 == contentSize.toLong * byteSize, "Inconsistent padded sequence payload")
    if (byteSize == 0) List.fill(contentSize)(Array.emptyByteArray)
    else out.drop(5).grouped(byteSize).toList
  }

  override def encode(byteArrays: Seq[Array[Byte]]): Array[Byte] = {
    val contentSize = ByteFixedLengthIntIO.encode(byteArrays.length)
    val maxBytes: Int = byteArrays.map(_.length).maxOption.getOrElse(0)
    require(maxBytes <= 255, "Element width exceeds the one-byte header")
    val bytesContent: Array[Byte] = byteArrays.flatMap(_.reverse.padTo(maxBytes, 0.asInstanceOf[Byte]).reverse).toArray
    contentSize ++ Array(maxBytes.toByte) ++ bytesContent
  }


}
