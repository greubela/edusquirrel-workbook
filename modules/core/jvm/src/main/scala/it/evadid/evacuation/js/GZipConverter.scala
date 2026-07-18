package it.evadid.evacuation.js

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import it.evadid.evacuation.core.io.traits.converter.Converter

class GZipConverter extends Converter[Array[Byte]] {

  override def convert(in: Array[Byte]): Array[Byte] = {
    val arrOutputStream = new ByteArrayOutputStream()
    val zipOutputStream = new GZIPOutputStream(arrOutputStream)
    zipOutputStream.write(in)
    zipOutputStream.flush()
    zipOutputStream.close()
    arrOutputStream.toByteArray
  }

  override def reconstruct(out: Array[Byte]): Array[Byte] = {
    val zipInputStream = new GZIPInputStream(new ByteArrayInputStream(out))
    zipInputStream.readAllBytes()
  }
}