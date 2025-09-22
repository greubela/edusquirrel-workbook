package util

import scala.scalajs.js.typedarray.{ArrayBuffer, DataView}

object TypeConversion {

  def decodeArayBuffer(buf: ArrayBuffer): Array[Byte] = {
    val data = new DataView(buf)
    val res = new Array[Byte](data.byteLength)
    for (index <- 0 until data.byteLength) {
      res(index) = data.getInt8(index)
    }
    res
  }

  def stringToBase64ByteArray(in: String): Array[Byte] = java.util.Base64.getDecoder.decode(in)
  
  def base64ByteArrayToString(in: Array[Byte]): String = java.util.Base64.getEncoder.encodeToString(in)
  

}
