package it.evadid.homepage.workbook.content

import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.elements.{Workbook, WorkbookSection}
import it.evadid.workbook.model.interaction.plugins.programming.ProgrammingExercise

case class CreateTestWorkbook(fullInfo: FullInfo) extends WorkbookFactory {

  override def createWorkbook: Workbook = {
    workbook("TestWorkbook/WorkbookTitle", List(section1))
  }

  lazy val section1: WorkbookSection = {
    section("sec1Id", "TestWorkbook/Sec1", List[WorkbookElement](
      container("TestWorkbook/Sec1Cont1", List(
        ProgrammingExercise("prog-1")
      )
    )))
  }

  override def workbookId: String = "workbookTest"
}
