package it.evadid.evacuation.core.io.instances.binary

import it.evadid.evacuation.core.io.traits.converter.Converter
import it.evadid.evacuation.core.utility.BinaryUtility

class MinimalEncoder extends Converter[Array[Byte]] {

  val encodingShemes = Map[Int, List[Converter[Array[Byte]]]](
    0 -> List(Converter.identity[Array[Byte]]()),
    1 -> List(new RunLengthConverter),

    10 -> List(new BytePlaneConverter(2), new RunLengthConverter),
  //  11 -> List(new BytePlaneConverter(3), new RunLengthConverter),
    12 -> List(new BytePlaneConverter(4), new RunLengthConverter),
    13 -> List(new BytePlaneConverter(8), new RunLengthConverter),
    14 -> List(new BytePlaneConverter(12), new RunLengthConverter)

   // 21 -> List(new BitplaneConverter, new RunLengthConverter),
   // 20 -> List(new RunLengthConverter, new RunLengthConverter),

    /* ,50 -> List(new DeflateConverter),
     51 -> List(new GZipConverter)*/
  )

  override def convert(in: Array[Byte]): Array[Byte] = {
    println("\n/* encoding of (" + in.length + ") bytes ---")
    val options = encodingShemes.map(tup => {
      val id = tup._1
      val encoder = tup._2

      val contentRes: Array[Byte] = encoder.foldLeft(in)((bytes, conv) => conv.convert(bytes))
      println("Size of Encoding " + id + ": " + contentRes.length)
      (List(id.toByte) ++ contentRes).toArray
    }).toList
    val res = options.minBy(_.length)
    println("--- chosen encoding " + BinaryUtility.byteToUInt(res(0)) + " (" + res.length + " bytes) */\n")
    res
  }

  override def reconstruct(out: Array[Byte]): Array[Byte] = {
    assert(out.length > 0, "out must not be empty!")
    val content = out.slice(1, out.length)
    val converterID = BinaryUtility.byteToUInt(out(0))
    assert(encodingShemes.contains(converterID), "Unknown compression sheme: " + converterID + "!")
    val converter = encodingShemes(converterID)

    val res = converter.reverse.foldLeft(content)((bytes, conv) => conv.reconstruct(bytes))
    res

  }

}

object MinimalEncoder {

  def testExample(): Unit = {

  }


}
