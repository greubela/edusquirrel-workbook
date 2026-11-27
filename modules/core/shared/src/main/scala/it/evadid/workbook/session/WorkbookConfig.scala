package it.evadid.workbook.session

import it.evadid.workbook.elements.structureElements.WorkbookSection

case class WorkbookConfig(activeSection: Option[WorkbookSection]) derives upickle.default.ReadWriter
