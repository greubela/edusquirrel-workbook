package it.evadid.homepage.workbook.content

import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.elements.{Workbook, WorkbookSection}
import it.evadid.workbook.model.interaction.plugins.programming.ProgrammingExercise

case class CreateTestWorkbook(fullInfo: FullInfo) extends WorkbookFactory {

  override def createWorkbook: Workbook = {
    workbook("testWorkbook", List(section1))
  }

  lazy val section1: WorkbookSection = {
    section("sec1Id", "sec-1", List[WorkbookElement](
      ProgrammingExercise(),
    ))
  }

  override def workbookId: String = "workbookTest"
}
