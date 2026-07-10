package it.evadid.workbook.abstractions

import it.evadid.core.datastructures.file.FileDescription

sealed trait TypeOfTextDisplay {
}

object TypeOfTextDisplay {
  case object PLAINTEXT extends TypeOfTextDisplay

  case object HTML extends TypeOfTextDisplay

  case object MARKDOWN extends TypeOfTextDisplay

  sealed trait URL_TYPE extends TypeOfTextDisplay

  case object URL_RELATIVE_TO_GLOBAL_RESOURCES extends URL_TYPE

  case class URL_RELATIVE_TO_WORKBOOK_RESOURCES(workbookRoot: FileDescription) extends URL_TYPE

}
