package it.evadid.evacuation.core.io.instances.binary

import it.evadid.evacuation.core.datastructures.seqs.BitSequence
import it.evadid.evacuation.core.io.traits.encoder.IO
import upickle.default.ReadWriter

case class SizeContentIO(bitsForSize: Int) extends IO[List[BitSequence], BitSequence] derives ReadWriter {
  require(bitsForSize >= 1 && bitsForSize <= 32, "Size width must be between 1 and 32")

  override def encode(in: List[BitSequence]): BitSequence = {
    in.foreach(bitSeq => require(bitSeq.size.toLong < (1L << bitsForSize), "Element length does not fit in the size header"))
    val withSizes = in.map(bitSeq => BitSequence(bitSeq.size).ensureSize(bitsForSize).append(bitSeq))
    withSizes.foldLeft(BitSequence.empty)(_.append(_))
  }

  override def decode(out: BitSequence): List[BitSequence] = {

    @scala.annotation.tailrec
    def go(toDecode: BitSequence, encoded: List[BitSequence]): List[BitSequence] = {
      if (toDecode.size == 0) encoded
      else {
        require(toDecode.size >= bitsForSize, "Last length header is incomplete")
        val length = toDecode.head(bitsForSize).toLong
        require(length <= Int.MaxValue, "Element length exceeds supported sequence size")
        val contentSize = length.toInt
        val tail = toDecode.tail(bitsForSize)
        require(tail.size >= contentSize, "Last content is incomplete")
        val content = tail.head(contentSize)
        val rem = tail.tail(contentSize)
        go(rem, encoded.appended(content))
      }
    }

    go(out, List())

  }
}

