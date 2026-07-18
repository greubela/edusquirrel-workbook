package it.evadid.evacuation.io.instances

import it.evadid.evacuation.core.io.instances.bits.HuffmanIO

import scala.collection.mutable

object TestHuffmanIO {


  def main(args: Array[String]): Unit = {
    val weightMap = Map("A" -> 10, "B" -> 15, "C" -> 30, "D" -> 16, "E" -> 29)
    HuffmanIO.createEncodingMap(weightMap)

    val sampleText = "this is an example of a huffman tree"
    val sampleWeights: mutable.Map[Char, Int] = mutable.Map()
    sampleText.toList.distinct.foreach(char => sampleWeights.put(char, sampleText.toList.count(_ == char)))
    val encodingMap = HuffmanIO.createEncodingMap(sampleWeights.toMap)
    val encoder = HuffmanIO(encodingMap)
    val encoded = encoder.encode(sampleText.toList)
    val decoded = encoder.decode(encoded)

    println("encoding: " + sampleText + " -> " + encoded + " -> " + decoded)
    println("bit lengths: " + (sampleText.length * 8) + " -> " + encoded.size + " -> " + (decoded.size * 8))

  }


}
