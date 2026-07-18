package it.evadid.evacuation.core.io.instances.eva.eva1

import it.evadid.evacuation.core.io.instances.binary.MinimalEncoder
import it.evadid.evacuation.core.io.instances.string.Base64IO
import it.evadid.evacuation.core.io.traits.encoder.IO
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.{EvaEdge, EvaGraph}
import it.evadid.evacuation.eva1.model.evagraph.{ConnectionInfo, EvaGraphModel, Router}

import scala.io.{BufferedSource, Source}

object EvaFlowGraphBase64Converter extends IO[EvaGraphModel, String]{


  override def decode(out: String): EvaGraphModel = {
    val minBytes = Base64IO.decode(out)
    val rawBytes = new MinimalEncoder().reconstruct(minBytes)
    EvaFlowGraphByteIO.decode(rawBytes)
  }

  override def encode(in: EvaGraphModel): String = {
    val bytes = EvaFlowGraphByteIO.encode(in)
    val minBytes = new MinimalEncoder().convert(bytes)
    Base64IO.encode(minBytes)
  }

  def main(args: Array[String]): Unit = {
    val graph = EvaGraphModel.createQuickTest()
    val encoded = encode(graph)
    println("encoded: " + encoded)
    val r = encode(decode(encoded))
    val s = decode(encode(graph))
    println(String.valueOf(graph) + " --> " + s)
    println(encoded + " --> " + r)

  }

  def main2(args: Array[String]): Unit = {
    val fileSource = "E:\\Nextcloud\\Andre Home\\Arbeit\\Dr\\Projects\\Evakuierung\\Git Programmier Projekt\\EvaProject\\dist\\material\\alltest.evaG"
    val input: BufferedSource = Source.fromFile(fileSource)
    val inputStr: String = input.mkString
    // simpleConverter.decode(inputStr)
  }

}
