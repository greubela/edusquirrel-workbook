package it.evadid.evacuation.core.io.instances.binary

import it.evadid.evacuation.core.datastructures.seqs.BitSequence
import it.evadid.evacuation.core.io.traits.converter.Converter

class BitplaneConverter extends Converter[Array[Byte]] {

  // Todo
  override def convert(in: Array[Byte]): Array[Byte] = {

    val bytes = in.map(BitSequence.paddedNumber(_, 8))
    val res: BitSequence = 0.until(8).reverse.map(BitSequence.getBitplane(bytes.toIndexedSeq, _)).foldLeft(BitSequence.empty)(_.append(_))
    res.seq.sliding(8, 8).map(new BitSequence(_)).map(_.toByte).toArray

  }

  override def reconstruct(out: Array[Byte]): Array[Byte] = {

    val bitSequence = out.toList.map(BitSequence.paddedNumber(_, 8)).foldLeft(BitSequence.empty)(_ append _)
    assert(bitSequence.size == out.length * 8, "lengths are not matching, seq length: " + bitSequence.size + ", arr length: " + out.length + "!")

    val res = bitSequence.seq.sliding(out.length, out.length).map(new BitSequence(_)).toList

    val bytesSequence = out.indices.map(BitSequence.getBitplane(res, _)).foldLeft(BitSequence.empty)(_.append(_))
    val bytes = bytesSequence.seq.sliding(8,8).map(new BitSequence(_)).map(_.toByte).toArray.reverse
    bytes
  }
}
