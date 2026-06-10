package it.evadid.workbook.model.abstractions

import it.evadid.core.datastructures.file.FileDescription

sealed trait TypeOfTextContent {
}

object TypeOfTextContent {
  case object PLAINTEXT extends TypeOfTextContent

  case object HTML extends TypeOfTextContent

  case object MARKDOWN extends TypeOfTextContent

  sealed trait URL_TYPE extends TypeOfTextContent 

  case object URL_RELATIVE_TO_GLOBAL_RESOURCES extends URL_TYPE

  case class URL_RELATIVE_TO_WORKBOOK_RESOURCES(workbookRoot: FileDescription) extends URL_TYPE

}
