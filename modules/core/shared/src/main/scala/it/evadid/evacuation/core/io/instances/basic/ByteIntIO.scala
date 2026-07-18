package it.evadid.evacuation.core.io.instances.basic

import it.evadid.evacuation.core.io.traits.encoder.IO.ByteIO


object ByteIntIO extends ByteIO[Int] {
  override def decode(out: Array[Byte]): Int = BigInt(out).toInt

  override def encode(in: Int): Array[Byte] = BigInt(in).toByteArray
}
