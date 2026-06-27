package it.evadid.workbook.model.interaction.plugins.TurtleStitch

import it.evadid.workbook.model.interaction.plugins.TurtleStitch.TurtleStitchProjectState.StorageFormat.BYTES_AS_RAW_STRING

import scala.util.*

case class TurtleStitchProjectState private(programXml: Option[String] = None) {

  def asString: String = programXml.getOrElse("")

  override val toString: String = programXml.match {
    case None => "TurtleStitchProjectState(None)"
    case Some(xml) => "TurtleStitchProjectState(" + xml.take(20) + ".../" + xml.length + " chars)"
  }

  def toXmlString: Try[String] = Try {
    programXml.get
  }

}

object TurtleStitchProjectState {

  def empty(): TurtleStitchProjectState = TurtleStitchProjectState(None)

  def parseFromString(s: String): Try[TurtleStitchProjectState] = {
    Success(TurtleStitchProjectState(Some(s)))
  }

  def parseFromStringOrEmpty(s: String): TurtleStitchProjectState = {
    parseFromString(s).getOrElse(empty())
  }

  def parseFromBytes(bytes: Array[Byte], storageFormat: StorageFormat = defaultStorageFormat): Try[TurtleStitchProjectState] = {
    val contentAsString: String = storageFormat match {
      case BYTES_AS_RAW_STRING => new String(bytes.map(_.toByte), "UTF-8")
      //case BYTES_AS_BASE64_STRING => JsHelpers.byteArrayToBase64String(bytes)
      case _ => ???
    }
    parseFromString(contentAsString)
  }


  enum StorageFormat {
    case BYTES_AS_RAW_STRING, BYTES_AS_BASE64_STRING
  }

  private val defaultStorageFormat: StorageFormat = StorageFormat.BYTES_AS_RAW_STRING

}




