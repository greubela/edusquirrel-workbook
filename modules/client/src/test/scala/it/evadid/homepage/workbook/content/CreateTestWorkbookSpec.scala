package it.evadid.homepage.workbook.content

import it.evadid.workbook.elements.interactionElements.programming.{JavaTurtleTask, ProgrammingExerciseFullJava}
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

  test("square pilot has a separate ID and parameter targets") {
    val workbook = CreateTestWorkbook(null).createWorkbook
    val exercises = workbook.allChildrenFullSubtree.collect { case exercise: ProgrammingExerciseFullJava => exercise }
    val pilot = exercises.filter(_.elementId == "java-square-pilot")

    assertEquals(pilot.size, 1)
    assertEquals(pilot.head.turtleTask, Some(JavaTurtleTask.squarePilot))
    assertEquals(exercises.find(_.elementId == "prog-full-java").get.turtleTask, None)
  }
}
