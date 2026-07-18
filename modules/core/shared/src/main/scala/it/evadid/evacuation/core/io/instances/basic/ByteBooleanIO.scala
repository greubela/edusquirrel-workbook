package it.evadid.evacuation.core.io.instances.basic

import it.evadid.evacuation.core.io.traits.encoder.IO.ByteIO

object ByteBooleanIO extends ByteIO[Boolean] {
  override def decode(out: Array[Byte]): Boolean = out(0).toByte == 0

  override def encode(in: Boolean): Array[Byte] = if (in) Array(0) else Array(1)
}
