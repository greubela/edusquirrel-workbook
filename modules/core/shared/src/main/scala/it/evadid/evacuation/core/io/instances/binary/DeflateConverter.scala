package it.evadid.evacuation.core.io.instances.binary

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

import it.evadid.evacuation.core.io.traits.converter.Converter

case class DeflateConverter() extends Converter[Array[Byte]] {

// Todo: Not working in ScalaJS???

  override def convert(in: Array[Byte]): Array[Byte] = {
    val deflater = new Deflater()
    deflater.setInput(in)
    deflater.finish()

    val writeTo = new Array[Byte](in.length)
    val compressedLength = deflater.deflate(writeTo)

    println("content: " + in.length + " -> " + compressedLength)

    writeTo.slice(0, compressedLength)
  }

  override def reconstruct(out: Array[Byte]): Array[Byte] = {

    val inflater = new Inflater()
    inflater.setInput(out)

    val outputStream = new ByteArrayOutputStream()
    val buffer = new Array[Byte](1024)
    while (!inflater.finished) {
      val count = inflater.inflate(buffer)
      outputStream.write(buffer, 0, count)
    }
    val res = outputStream.toByteArray
    println("content: " + out.length + " -> " + res.length)
    res
  }
}
