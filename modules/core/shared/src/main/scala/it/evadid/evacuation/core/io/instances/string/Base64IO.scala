package it.evadid.evacuation.core.io.instances.string

import it.evadid.evacuation.core.io.traits.encoder.IO

object Base64IO extends IO[Array[Byte], String] {
  override def decode(out: String): Array[Byte] = java.util.Base64.getDecoder.decode(out)

  override def encode(in: Array[Byte]): String = java.util.Base64.getEncoder.encodeToString(in)
}
