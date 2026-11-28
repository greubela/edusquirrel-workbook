package it.evadid.workbook.abstractions

import upickle.ReadWriter.*
import upickle.default.*

sealed trait TypeOfTextDisplay derives ReadWriter

object TypeOfTextDisplay {
  val allElements: Seq[TypeOfTextDisplay] = List(PLAINTEXT_UNDERSCORE_REPLACABLE, PLAINTEXT, HTML, MARKDOWN, URL_RELATIVE_TO_TECHNICAL_RESOURCES, URL_RELATIVE_TO_WORKBOOK_RESOURCES, URL_ABSOLUTE)

  @upickle.implicits.key("PLAINTEXT_UNDERSCORE_REPLACABLE")
  case object PLAINTEXT_UNDERSCORE_REPLACABLE extends TypeOfTextDisplay

  @upickle.implicits.key("PLAINTEXT")
  case object PLAINTEXT extends TypeOfTextDisplay

  @upickle.implicits.key("HTML")
  case object HTML extends TypeOfTextDisplay

  @upickle.implicits.key("MARKDOWN")
  case object MARKDOWN extends TypeOfTextDisplay

  sealed trait URL_TYPE extends TypeOfTextDisplay derives ReadWriter

  @upickle.implicits.key("URL_RELATIVE_TO_TECHNICAL_RESOURCES")
  case object URL_RELATIVE_TO_TECHNICAL_RESOURCES extends URL_TYPE

  @upickle.implicits.key("URL_RELATIVE_TO_WORKBOOK_RESOURCES")
  case object URL_RELATIVE_TO_WORKBOOK_RESOURCES extends URL_TYPE

  @upickle.implicits.key("URL_ABSOLUTE")
  case object URL_ABSOLUTE extends URL_TYPE

}
