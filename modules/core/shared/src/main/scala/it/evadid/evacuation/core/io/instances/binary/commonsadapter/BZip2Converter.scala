package it.evadid.evacuation.core.io.instances.binary.commonsadapter

import it.evadid.evacuation.core.io.traits.converter.Converter
//import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
//import org.apache.commons.compress.compressors.bzip2.{BZip2CompressorInputStream, BZCOS}

case class BZip2Converter() extends Converter[Array[Byte]] {


  override def convert(data: Array[Byte]): Array[Byte] = ???/*{
    val out = new ByteArrayOutputStream()
    val gzip = new XZCompressorOutputStream(out)

    var offset = 0
    var repeat = false

    def writeToBuffer(offset: Int): Int = {
      val dest = data.length.min(offset + 1024)
      val size = dest - offset

      gzip.write(data, offset, size)
      gzip.flush()

      dest
    }

    var writtenUntil = 0
    while (writtenUntil < data.length) {
      writtenUntil = writeToBuffer(writtenUntil)
    }
    gzip.finish()

    out.toByteArray

  }*/

  override def reconstruct(data: Array[Byte]): Array[Byte] = ???/*{
    val out = new ByteArrayOutputStream()
    val in = new ByteArrayInputStream(data)

    val ungzip = new BZip2CompressorInputStream(in)
    val buffer = new Array[Byte](2048)

    var bytesRead = ungzip.read(buffer)
    do {
      out.write(buffer, 0, bytesRead)
      out.flush()
      bytesRead = ungzip.read(buffer)
    } while (bytesRead >= 0)

    out.toByteArray


  }*/

}
