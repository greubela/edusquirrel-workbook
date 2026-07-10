package it.evadid.homepage.workbook.content

import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExercise
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}

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
