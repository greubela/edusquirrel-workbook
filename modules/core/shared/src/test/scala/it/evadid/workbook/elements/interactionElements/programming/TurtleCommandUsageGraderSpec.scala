package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.language.AppLanguage.{English, Danish}
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.vm.conversion.ProgrammingStateConversions.*
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import it.evadid.workbook.support.TestExecutionContext
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import upickle.default.*
import munit.FunSuite

class TurtleCommandUsageGraderSpec extends FunSuite {
  private def grader(command: String, maximum: Int, measure: CommandUsageMeasure): TurtleCommandUsageGrader =
    TurtleCommandUsageGrader(TurtleCommand.resolveCanonicalNames(command).get, maximum, measure, s"limit-$command-$measure")
  private val loop = ProgrammingStatePythonString("for i in range(4):\n    forward(10)")
  private val target = TurtleGraphic(List.fill(4)(TurtleCommand.Forward(10.0)))
  private def exercise(limits: MaxCommandUsage): TurtleRecreateShapeInteraction =
    TurtleRecreateShapeInteraction("limited-shape", loop, target, maxCommandUsage = limits)

  test("each map entry gets its own required grader, independently of the shape and other limits") {
    val limits = MaxCommandUsage(occurrencesInCode = Map("forward" -> 1, "right" -> 0),
      callsDuringExecution = Map("forward" -> 3))
    val interaction = exercise(limits)
    val constraints = interaction.gradingElements.collect { case g: TurtleCommandUsageGrader => g }
    assertEquals(interaction.gradingElements.count(_.isInstanceOf[RecreateShapeGrader]), 1)
    assertEquals(constraints.size, 3)
    assertEquals(constraints.map(_.key).distinct.size, 3)
    assert(constraints.forall(_.importance == GradingImportance.REQUIRED_TEST))
    Future.traverse(interaction.gradingElements)(_.gradeStateSafe(loop, false, TestExecutionContext())).map { results =>
      assertEquals(results.head.gradingStatus, GradingStatus.Correct())
      assertEquals(results.tail.map(_.gradingStatus), List(GradingStatus.Correct(), GradingStatus.Correct(), GradingStatus.Incorrect()))
    }
  }

  test("source and execution limits include the boundary and work across Python, Snap and Java fragments") {
    val cases = List(
      (CommandUsageMeasure.OccurrencesInCode, 1, GradingStatus.Correct()),
      (CommandUsageMeasure.OccurrencesInCode, 0, GradingStatus.Incorrect()),
      (CommandUsageMeasure.CallsDuringExecution, 4, GradingStatus.Correct()),
      (CommandUsageMeasure.CallsDuringExecution, 3, GradingStatus.Incorrect()))
    Future.traverse(List(loop, loop.toSnapXml, loop.toJava)) { state =>
      Future.traverse(cases) { (measure, maximum, expected) =>
        grader("fd", maximum, measure).gradeStateSafe(state, false, TestExecutionContext()).map { result =>
          assertEquals(result.gradingStatus, expected, clues(state, measure))
          val feedback = result.feedbackInformation.get
          assert(feedback.tryGetInLanguage(English).exists(_.contains(maximum.toString)))
          assert(feedback.tryGetInLanguage(Danish).exists(_.nonEmpty))
        }
      }
    }
  }

  test("checked Java helper calls are counted once in source and repeatedly during execution") {
    val java = ProgrammingStateJavaString("""class Drawing {
      static void step() { Turtle.forward(10); }
      public static void main(String[] args) {
        for (int i = 0; i < 4; i++) { step(); }
      }
    }""")
    for {
      source <- grader("forward", 1, CommandUsageMeasure.OccurrencesInCode).gradeStateSafe(java, false, TestExecutionContext())
      runtime <- grader("forward", 3, CommandUsageMeasure.CallsDuringExecution).gradeStateSafe(java, false, TestExecutionContext())
    } yield {
      assertEquals(source.gradingStatus, GradingStatus.Correct())
      assertEquals(runtime.gradingStatus, GradingStatus.Incorrect())
    }
  }

  test("aliases share counts while absent commands remain unlimited") {
    val state = ProgrammingStatePythonString("fd(10)\nforward(20)\nrt(90)\nturn_right(90)\nright(90)")
    val constraints = exercise(MaxCommandUsage(callsDuringExecution = Map("forward" -> 2))).gradingElements
      .collect { case g: TurtleCommandUsageGrader => g }
    assertEquals(constraints.size, 1)
    for {
      allowed <- constraints.head.gradeStateSafe(state, false, TestExecutionContext())
      rotation <- grader("turn", 2, CommandUsageMeasure.CallsDuringExecution).gradeStateSafe(state, false, TestExecutionContext())
    } yield {
      assertEquals(allowed.gradingStatus, GradingStatus.Correct())
      assertEquals(rotation.gradingStatus, GradingStatus.Incorrect())
      assertEquals(exercise(MaxCommandUsage()).gradingElements.size, 1)
    }
  }

  test("unused definitions and untaken branches count in source but not as executed calls") {
    val state = ProgrammingStatePythonString("def unused():\n    forward(1)\nif False:\n    fd(2)")
    for {
      source <- grader("forward", 1, CommandUsageMeasure.OccurrencesInCode).gradeStateSafe(state, false, TestExecutionContext())
      runtime <- grader("forward", 0, CommandUsageMeasure.CallsDuringExecution).gradeStateSafe(state, false, TestExecutionContext())
    } yield {
      assertEquals(source.gradingStatus, GradingStatus.Incorrect())
      assertEquals(runtime.gradingStatus, GradingStatus.Correct())
    }
  }

  test("partial execution and malformed programs never produce a successful execution-limit result") {
    val infinite = ProgrammingStatePythonString("while True:\n    pass")
    for {
      source <- grader("forward", 0, CommandUsageMeasure.OccurrencesInCode).gradeStateSafe(infinite, false, TestExecutionContext())
      runtime <- grader("forward", 0, CommandUsageMeasure.CallsDuringExecution).gradeStateSafe(infinite, false, TestExecutionContext())
      invalid <- grader("forward", 0, CommandUsageMeasure.OccurrencesInCode)
        .gradeStateSafe(ProgrammingStatePythonString("forward("), false, TestExecutionContext())
    } yield {
      assertEquals(source.gradingStatus, GradingStatus.Correct())
      assert(runtime.gradingStatus.isError)
      assert(invalid.gradingStatus.isError)
    }
  }

  test("user-defined command names do not hide explicitly bound turtle methods") {
    val userOnly = ProgrammingStatePythonString("def forward(n):\n    pass\nforward(1)")
    val withNativeCall = ProgrammingStatePythonString(userOnly.code + "\nturtle.forward(2)")
    val saved = ProgrammingState.serializer.deserialize(ProgrammingState.serializer.serialize(withNativeCall.toBeExpression))
    Future.traverse(CommandUsageMeasure.values.toList) { measure =>
      Future.traverse(List(withNativeCall, saved)) { state =>
        for {
          user <- grader("forward", 0, measure).gradeStateSafe(userOnly, false, TestExecutionContext())
          forbidden <- grader("forward", 0, measure).gradeStateSafe(state, false, TestExecutionContext())
          one <- grader("forward", 1, measure).gradeStateSafe(state, false, TestExecutionContext())
        } yield {
          assertEquals(user.gradingStatus, GradingStatus.Correct())
          assertEquals(forbidden.gradingStatus, GradingStatus.Incorrect(), clues(measure))
          assertEquals(one.gradingStatus, GradingStatus.Correct())
        }
      }
    }
  }

  test("constraint identities are deterministic and independent of map ordering or other limits") {
    val first = exercise(MaxCommandUsage(occurrencesInCode = Map("forward" -> 1, "right" -> 2),
      callsDuringExecution = Map("forward" -> 4)))
    val reordered = exercise(MaxCommandUsage(occurrencesInCode = Map("right" -> 2, "forward" -> 1),
      callsDuringExecution = Map("forward" -> 4)))
    assertEquals(first.gradingElements.map(_.key), reordered.gradingElements.map(_.key))
    val changed = exercise(MaxCommandUsage(occurrencesInCode = Map("right" -> 2, "forward" -> 0),
      callsDuringExecution = Map("forward" -> 4)))
    assertEquals(first.gradingElements.map(_.key).zip(changed.gradingElements.map(_.key)).count((a, b) => a != b), 1)
  }

  test("limits, individual graders and their results roundtrip through the registered serializers") {
    val original = exercise(MaxCommandUsage(occurrencesInCode = Map("fd" -> 1), callsDuringExecution = Map("forward" -> 4)))
    val restored = List(
      WorkbookElementFactory.serializerRefBasedJson.deserialize(WorkbookElementFactory.serializerRefBasedJson.serialize(original)),
      WorkbookElementFactory.serializerConstructorLike.deserialize(original.toStringConstructorLike))
    restored.foreach { element =>
      assertEquals(element, original)
      assertEquals(element.asInstanceOf[TurtleRecreateShapeInteraction].gradingElements, original.gradingElements)
    }
    val g: ProgrammingTestGrader = original.gradingElements.collectFirst { case g: TurtleCommandUsageGrader => g }.get
    assertEquals(read[ProgrammingTestGrader](write(g)), g)
    assertEquals(readBinary[ProgrammingTestGrader](writeBinary(g)), g)
    g.gradeStateSafe(loop, false, TestExecutionContext()).map { result =>
      assertEquals(read[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]](write(result)), result)
      assertEquals(readBinary[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]](writeBinary(result)), result)
    }
  }

  test("negative maxima, invalid names and currently unsupported non-turtle elements are rejected") {
    intercept[IllegalArgumentException](MaxCommandUsage(occurrencesInCode = Map("forward" -> -1)))
    intercept[IllegalArgumentException](MaxCommandUsage(callsDuringExecution = Map("forward" -> -1)))
    intercept[IllegalArgumentException](MaxCommandUsage(occurrencesInCode = Map("" -> 1)))
    intercept[IllegalArgumentException](exercise(MaxCommandUsage(occurrencesInCode = Map("doRepeat" -> 1))))
    intercept[IllegalArgumentException](exercise(MaxCommandUsage(callsDuringExecution = Map("misspelled" -> 1))))
  }
}
