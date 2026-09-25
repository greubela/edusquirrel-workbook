package it.evadid.workbook.abstractions

import upickle.ReadWriter.*
import upickle.default.*

sealed trait TypeOfTextDisplay derives ReadWriter {

  def serializerName: String = this.getClass.getSimpleName

  override val toString: String = serializerName
}

object TypeOfTextDisplay {
  val allElements: List[TypeOfTextDisplay] = List(PLAINTEXT_UNDERSCORE_REPLACABLE, PLAINTEXT, HTML, MARKDOWN, URL_RELATIVE_TO_TECHNICAL_RESOURCES, URL_RELATIVE_TO_WORKBOOK_RESOURCES, URL_ABSOLUTE)

  case object PLAINTEXT_UNDERSCORE_REPLACABLE extends TypeOfTextDisplay

  case object PLAINTEXT extends TypeOfTextDisplay

  case object HTML extends TypeOfTextDisplay

  case object MARKDOWN extends TypeOfTextDisplay

  sealed trait URL_TYPE extends TypeOfTextDisplay derives ReadWriter

  case object URL_RELATIVE_TO_TECHNICAL_RESOURCES extends URL_TYPE

  case object URL_RELATIVE_TO_WORKBOOK_RESOURCES extends URL_TYPE

  case object URL_ABSOLUTE extends URL_TYPE

}
