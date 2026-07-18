package it.evadid.homepage.util.serializing

import fs2.{Fallible, Stream}
import fs2.data.csv.lowlevel

import scala.scalajs.js
import scala.scalajs.js.{Dictionary, JSON}

object IoSerialization {

  def parseJson(content: String): Map[String, String] = try {
    val parsed: Dictionary[String] = JSON.parse(content).asInstanceOf[js.Dictionary[String]]
    parsed.toMap
  } catch {
    case e: Throwable => {
      e.printStackTrace()
      println("Error parsing JSON: " + e.getMessage + "\n" + content)
      Map.empty[String, String]
    }
  }

  def parseCsv(content: String): List[List[String]] = {
    val res: Either[Throwable, List[List[String]]] = Stream
      .emit(content)
      .covary[Fallible]
      .through(lowlevel.rows[Fallible, String](separator = ';'))
      .map(_.values.toList)
      .compile
      .toList

    res.getOrElse(List.empty[List[String]])

  }
  
  
}
