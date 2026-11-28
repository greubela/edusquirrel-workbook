package it.evadid.workbook.session

import it.evadid.workbook.elements.structure.WorkbookSection

case class WorkbookConfig(activeSection: Option[WorkbookSection]) derives upickle.default.ReadWriter
