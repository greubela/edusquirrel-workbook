package it.evadid.evacuation.core.io.instances.bits

import it.evadid.evacuation.core.datastructures.seqs.BitSequence
import it.evadid.evacuation.core.io.traits.encoder.IO

object PaddedIO extends IO[List[BitSequence], BitSequence] {

  override def encode(bitSequences: List[BitSequence]): BitSequence = {
    val maxSize = if bitSequences.isEmpty then 0 else bitSequences.map(_.size).max.max(1)
    val padded = bitSequences.map(_.ensureSize(maxSize))
    val size = BitSequence.fullInt(maxSize)
    padded.foldLeft(size)(_.append(_))
  }

  override def decode(out: BitSequence): List[BitSequence] = {
    require(out.size >= 32, "Padded sequence header is incomplete")
    val size = out.headInt
    val payload = out.tailInt
    require(size >= 0 && (size != 0 || payload.size == 0), "Invalid padded element width")
    if size == 0 then Nil
    else {
      require(payload.size % size == 0, "Padded sequence has a partial element")
      payload.seq.grouped(size).map(BitSequence(_)).toList
    }
  }


}
