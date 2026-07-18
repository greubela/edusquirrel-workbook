package it.evadid.evacuation.core.io.instances.bits

import it.evadid.evacuation.core.datastructures.seqs.BitSequence
import it.evadid.evacuation.core.io.traits.encoder.IO

object PaddedIO extends IO[List[BitSequence], BitSequence] {

  override def encode(bitSequences: List[BitSequence]): BitSequence = {
    val maxSize = bitSequences.maxBy(_.size).size
    bitSequences.map(_.ensureSize(maxSize))
    val size = BitSequence.fullInt(maxSize)
    bitSequences.foldLeft(size)(_.append(_))
  }

  override def decode(out: BitSequence): List[BitSequence] = {
    val size = out.headInt
    out.tailInt.seq.grouped(size).map(BitSequence(_)).toList
  }


}
