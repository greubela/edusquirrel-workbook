package it.evadid.workbook.abstractions

sealed trait TypeOfTextDisplay {

  def serializerName: String = this.getClass.getSimpleName

  override val toString: String = serializerName
}

object TypeOfTextDisplay {
  val allElements: List[TypeOfTextDisplay] = List(PLAINTEXT_UNDERSCORE_REPLACABLE, PLAINTEXT, HTML, MARKDOWN, URL_RELATIVE_TO_TECHNICAL_RESOURCES, URL_RELATIVE_TO_WORKBOOK_RESOURCES, URL_ABSOLUTE)

  case object PLAINTEXT_UNDERSCORE_REPLACABLE extends TypeOfTextDisplay

  case object PLAINTEXT extends TypeOfTextDisplay

  case object HTML extends TypeOfTextDisplay

  case object MARKDOWN extends TypeOfTextDisplay

  sealed trait URL_TYPE extends TypeOfTextDisplay

  case object URL_RELATIVE_TO_TECHNICAL_RESOURCES extends URL_TYPE

  case object URL_RELATIVE_TO_WORKBOOK_RESOURCES extends URL_TYPE

  case object URL_ABSOLUTE extends URL_TYPE

}
