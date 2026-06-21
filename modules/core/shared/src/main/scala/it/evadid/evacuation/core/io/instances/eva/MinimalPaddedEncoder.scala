package it.evadid.evacuation.core.io.instances.eva

import it.evadid.evacuation.core.io.traits.encoder.IO
import it.evadid.evacuation.core.io.traits.encoder.IO.ByteIO


case class MinimalPaddedEncoder[T](io: ByteIO[T]) extends IO[Seq[T], Array[Byte]] {

  // TODO: Encoding (Huffman default // repeating // leer + Fülling mit Richtung)

  override def decode(out: Array[Byte]): Seq[T] = {
    val content = MinimalPaddedByteSeqEncoder.decode(out)
    content.map(io.decode).toList
  }

  override def encode(in: Seq[T]): Array[Byte] = {
    val byteSeq: Seq[Array[Byte]] = in.map(io.encode)
    MinimalPaddedByteSeqEncoder.encode(byteSeq)
  }
}

object MinimalPaddedEncoder{

  //val seqLongEncoder


}
