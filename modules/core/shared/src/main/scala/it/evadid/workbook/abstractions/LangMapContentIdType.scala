package it.evadid.workbook.abstractions

import upickle.default.*

case class LangMapContentIdType(contentRole: RoleInWorkbook, contentType: TypeOfTextDisplay) derives ReadWriter {

}
