package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.workbook.interaction.sync.UpdateImportance

import munit.FunSuite
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
import ProgramStructureRequirement.*
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStatePythonStructured}
import it.evadid.workbook.abstractions.grading.GradingStatus
import it.evadid.workbook.support.TestExecutionContext
import scala.concurrent.{ExecutionContext, Future}

class ProgramStructureGraderSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private def grade(state: ProgrammingState, requirement: ProgramStructureRequirement) =
    ProgramStructureGrader(requirement, "structure", LanguageMapContentId("test/structure"))
      .gradeStateSafe(state, true, UpdateImportance.MAJOR, TestExecutionContext())

  test("comments and strings do not satisfy structural requirements") {
    val source = ProgrammingStatePythonStructured("# def quadrat(distanz): for i in range(4):\nforward(100)")
    Future.traverse(Seq(UsesLoop, DefinesFunction("quadrat", Seq("distanz")))) { requirement =>
      grade(source, requirement).map(result => assertEquals(result.gradingStatus, GradingStatus.Incorrect()))
    }
  }

  test("nested loops and named parameters are recognized in Python and Snap") {
    val source = ProgrammingStatePythonStructured("def quadrat(distanz):\n    for i in range(4):\n        forward(distanz)\n        right(90)\nfor j in range(5):\n    quadrat(100)")
    Future.traverse(Seq(source, source.toSnapXml)) { state =>
      Future.traverse(Seq(UsesLoop, DefinesFunction("quadrat", Seq("distanz")))) { requirement =>
        grade(state, requirement).map(result => assertEquals(result.gradingStatus, GradingStatus.Correct()))
      }
    }
  }

  test("manual constraints reject loops, helpers and additional turtle commands independently") {
    val cases = Seq(
      ProgrammingStatePythonStructured("for i in range(4):\n    forward(100)") -> NoLoops,
      ProgrammingStatePythonStructured("def quadrat():\n    forward(100)\nquadrat()") -> NoFunctions,
      ProgrammingStatePythonStructured("penup()\nforward(100)") -> OnlyCommands(Seq("forward", "right")))
    Future.traverse(cases)((state, requirement) => grade(state, requirement).map(result =>
      assertEquals(result.gradingStatus, GradingStatus.Incorrect())))
  }

  test("unparsable source reports an error instead of passing absence requirements") {
    grade(ProgrammingStatePythonStructured("def ???"), NoLoops).map(result => assert(result.gradingStatus.isError))
  }
}
