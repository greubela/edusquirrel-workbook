package it.evadid.evacuation.core.io.instances.basic

import it.evadid.evacuation.core.io.traits.encoder.IO.ByteIO

object ByteBooleanIO extends ByteIO[Boolean] {
  override def decode(out: Array[Byte]): Boolean = {
    require(out.length == 1 && (out(0) == 0 || out(0) == 1), "Expected one boolean byte (0 or 1)")
    out(0) == 0
  }

  override def encode(in: Boolean): Array[Byte] = if (in) Array(0) else Array(1)
}
