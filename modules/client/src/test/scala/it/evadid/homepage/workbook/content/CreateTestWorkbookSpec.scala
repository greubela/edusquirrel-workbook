package it.evadid.homepage.workbook.content

import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExerciseFullJava
import munit.FunSuite

class CreateTestWorkbookSpec extends FunSuite {
  test("test workbook includes the full Java exercise") {
    val workbook = CreateTestWorkbook(null).createWorkbook

    assert(
      workbook.allChildrenFullSubtree.exists {
        case exercise: ProgrammingExerciseFullJava => exercise.elementId == "prog-full-java"
        case _ => false
      }
    )
  }
}
