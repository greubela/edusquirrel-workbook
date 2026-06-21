package it.evadid.evacuation.core.io.instances.basic

import it.evadid.evacuation.core.io.traits.encoder.IO.ByteIO

object ByteFixedLengthShortIO extends ByteIO[Short] {
  override def decode(out: Array[Byte]): Short = {
    assert(out.length == 2, "Array must have between 2 bytes to be a valid Short, but had " + out.length + ": " + out.toList + "")
    val uBytes: Array[Int] = out.map(byteVal => if (byteVal < 0) byteVal + 256 else byteVal)
    val res = (uBytes(0) << 8) + uBytes(1)
    res.toShort
  }

  override def encode(in: Short): Array[Byte] = Array(
    (in >> 8).toByte,
    (in.toByte))


  def main(args: Array[String]): Unit = {

    1.to(Short.MaxValue).foreach(nr => {
      val ret =  decode(encode(nr.toShort))
      if(ret != nr) println("error: " + nr)
    })

    println("finished!")

  }

}
