package it.evadid.evacuation.core.io.instances.basic

import it.evadid.evacuation.core.io.traits.encoder.IO.ByteIO

object ByteFixedLengthIntIO extends ByteIO[Int] {

  override def decode(out: Array[Byte]): Int = {
    assert(out.length == 4, "Array must have between 4 bytes to be a valid Int, but had " + out.length + ": " + out.toList + "")
    val uBytes: Array[Int] = out.map(byteVal => if (byteVal < 0) byteVal + 256 else byteVal)
    (uBytes(0) << 24) + (uBytes(1) << 16) + (uBytes(2) << 8) + uBytes(3)
  }

  override def encode(in: Int): Array[Byte] = Array(
    (in >> 24).toByte,
    (in >> 16).toByte,
    (in >> 8).toByte,
    (in.toByte))


}
