package it.evadid.evacuation.core.io.instances.binary

import it.evadid.evacuation.core.datastructures.seqs.BitSequence
import it.evadid.evacuation.core.io.traits.encoder.IO

case class SizeContentIO(bitsForSize: Int) extends IO[List[BitSequence], BitSequence] {
  assert(bitsForSize <= 32, "Bitsequence must have a maximum length of 2**32!")

  override def encode(in: List[BitSequence]): BitSequence = {
    in.foreach(bitSeq => assert(bitSeq.size < (1 << bitsForSize), "BitSeq size of " + bitSeq.size + " cannot be encoded with " + bitsForSize + "bits!"))
    val withSizes = in.map(bitSeq => BitSequence(bitSeq.size).ensureSize(bitsForSize).append(bitSeq))
    withSizes.foldLeft(BitSequence.empty)(_.append(_))
  }

  override def decode(out: BitSequence): List[BitSequence] = {

    @scala.annotation.tailrec
    def go(toDecode: BitSequence, encoded: List[BitSequence]): List[BitSequence] = {
      if (toDecode.size == 0) encoded
      else {
        assert(toDecode.size > bitsForSize, "BitSeq has an invalid length: Last length is incomplete!")
        val contentSize = out.head(bitsForSize).toInt
        val tail = toDecode.tail(bitsForSize)
        assert(tail.size >= contentSize, "BitSeq has an invalid length: last content is incomplete!")
        val content = tail.head(contentSize)
        val rem = tail.tail(contentSize)
        go(rem, encoded.appended(content))
      }
    }

    go(out, List())

  }
}

