package util

import scala.scalajs.js.typedarray.{ArrayBuffer, DataView}

object TypeConversion {

  def decodeArrayBuffer(buf: ArrayBuffer): Array[Byte] = {
    val data = new DataView(buf)
    Array.tabulate[Byte](data.byteLength)(index => data.getInt8(index))
  }

  def stringToBase64ByteArray(in: String): Array[Byte] = java.util.Base64.getDecoder.decode(in)
  
  def base64ByteArrayToString(in: Array[Byte]): String = java.util.Base64.getEncoder.encodeToString(in)
  
}
