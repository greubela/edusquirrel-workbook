package it.evadid.evacuation.core.io.instances.basic

import it.evadid.evacuation.core.io.traits.encoder.IO.ByteIO

object ByteIndexIO extends ByteIO[Int] {

  override def decode(out: Array[Byte]): Int = {
    assert(out.length > 0 && out.length <= 4, "Array must have between 1-4 bytes to be a valid Int, but had " + out.length + ": " + out.toList + "")

    val bytes: Array[Int] = Array(
      if (out.length == 4) out(0) else 0,
      if (out.length > 2) out(out.length - 3) else 0,
      if (out.length > 1) out(out.length - 2) else 0,
      out.last)

    val uBytes: Array[Int] = bytes.map(byteVal => if (byteVal < 0) byteVal + 256 else byteVal)

    (uBytes(0) << 24) + (uBytes(1) << 16) + (uBytes(2) << 8) + uBytes(3)
  }

  override def encode(number: Int): Array[Byte] = {

    val unsignedBytes: Long = if (number < 0) number.asInstanceOf[Long] + 4294967296L else number.asInstanceOf[Long]

    val arr: Array[Byte] = Array(
      (unsignedBytes >> 24).toByte,
      (unsignedBytes >> 16).toByte,
      (unsignedBytes >> 8).toByte,
      (unsignedBytes.toByte))

    if (arr(0) != 0) arr
    else if (arr(1) != 0) Array(arr(1), arr(2), arr(3))
    else if (arr(2) != 0) Array(arr(2), arr(3))
    else Array(arr(3))

  }


}
