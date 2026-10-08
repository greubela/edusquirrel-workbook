package it.evadid.homepage.workbook.content

import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExerciseFullJava
import munit.FunSuite

class CreateTestWorkbookSpec extends FunSuite {
  test("test workbook includes a QR exercise with a meaningful byte requirement") {
    val exercises = CreateTestWorkbook(null).createWorkbook.allChildrenFullSubtree.collect {
      case q: it.evadid.workbook.elements.interactionElements.qr.CreateQrCodeInteraction => q
    }
    assertEquals(exercises.size, 1)
    assertEquals(exercises.head.requirements.minBytes, 32)
    assert(!exercises.head.isPassed)
  }

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
