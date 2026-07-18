package it.evadid.evacuation.core.io.instances.eva

import it.evadid.evacuation.core.io.instances.basic.ByteIndexIO
import it.evadid.evacuation.core.io.traits.encoder.IO

object IndicesIO extends IO[Seq[Int], Array[Byte]] {

  override def decode(out: Array[Byte]): Seq[Int] = MinimalPaddedEncoder(ByteIndexIO).decode(out)


  override def encode(in: Seq[Int]): Array[Byte] = MinimalPaddedEncoder(ByteIndexIO).encode(in)

  def main(args: Array[String]): Unit = {

    val list = List(3, 4, 5, 255, 128, 256)
    val encoded = encode(list)
    val decoded = decode(encoded)

    list.foreach(nr => println(s"$nr: ${BigInt(nr).toByteArray.toList}"))

    println("list: " + list)
    println("encoded: " + encoded.toList)
    println("decoded: " + decoded.toList)

  }

}
