package it.evadid.evacuation.core.io.instances.basic

import it.evadid.evacuation.core.io.traits.encoder.IO.ByteIO

object ByteLongIO extends ByteIO[Long] {

  override def decode(out: Array[Byte]): Long = BigInt(out).toLong

  override def encode(in: Long): Array[Byte] = BigInt(in).toByteArray
}
