package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.commandTypes.WorkerCommands.{RunPythonResult, runPython}
import it.evadid.vm.conversion.unittest.ProgramForUnitTestExecution
import it.evadid.vm.test.CPythonRuntime
import it.evadid.workbook.abstractions.grading.{GradingImportance, GradingStatus}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingUnitTestGrader
import it.evadid.workbook.elements.interactionElements.programming.state.*
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future}

/** Unit-test grading end to end: the grader prepares each answer, the runner sends its Python test harness
  * to a worker, here a real CPython, and the reply becomes a grading status and the published details.
  */
class ProgrammingUnitTestGraderSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  override def munitIgnore: Boolean = !CPythonRuntime.available
  private val python = Seq(CPythonRuntime.client)

  private val square = ProgrammingStateRaw(Python, "for _ in range(4):\n    forward(20)\n    right(90)\n")
  /** The expected commands as a literal, which lets a failure report the first differing command. */
  private val squareTest = "assert run_program()[\"commands\"] == " + List.fill(4)("""["forward", 20], ["right", 90]""").mkString("[", ", ", "]")
  private def grader(tests: String, importance: GradingImportance = GradingImportance.REQUIRED_TEST, observe: Boolean = false,
      invocation: Boolean = false, comparison: Boolean = false) =
    ProgrammingUnitTestGrader("square", tests, importance, publishProgramObservation = observe,
      publishInvocationArguments = invocation, publishCommandComparison = comparison)
  private def status(tests: String, state: ProgrammingState, clients: Seq[ExecutionClient] = python): Future[GradingStatus] =
    grader(tests).gradeState(state, clients).map(_.gradingStatus)
  private def every(state: ProgrammingStateRaw): List[ProgrammingState] =
    List(state, ProgrammingStatePythonStructured(state.code), state.toJava, ProgrammingStateJavaStructured(state.toJava.asInstanceOf[ProgrammingStateRaw].code))
  private def errorMessage(result: GradingStatus): String = result match {
    case GradingStatus.TestError(error) => error.msg
    case other => fail(s"expected a test error, not $other")
  }

  test("the same answer passes its unit test in Python and Java, raw and structured") {
    val answers = every(square)
    Future.traverse(answers)(answer => status(squareTest, answer)).map { results =>
      assertEquals(results, List.fill(answers.size)(GradingStatus.Correct), answers.map(_.associatedLanguage).toString)
    }
  }

  test("answers importing the turtle library run against the same recorded commands") {
    val imports = List("from turtle import *\n", "from turtle import forward, right\n")
    val answers = imports.map(header => ProgrammingStateRaw(Python, header + square.code)) :+
      ProgrammingStateRaw(Python, "import turtle\n\nfor _ in range(4):\n    turtle.forward(20)\n    turtle.right(90)\n")
    Future.traverse(answers)(answer => status(squareTest, answer)).map { results =>
      assertEquals(results, List.fill(answers.size)(GradingStatus.Correct))
    }
  }

  test("a wrong answer fails its unit test in every language") {
    val answers = every(ProgrammingStateRaw(Python, "for _ in range(3):\n    forward(20)\n    right(90)\n"))
    Future.traverse(answers)(answer => status(squareTest, answer)).map { results =>
      assertEquals(results, List.fill(answers.size)(GradingStatus.Incorrect))
    }
  }

  test("a student's syntax or runtime error fails the test; a runaway program is a limit error") {
    val cases = List("forward(" -> GradingStatus.Incorrect, "forward(1)\nx = 1 / 0\n" -> GradingStatus.Incorrect,
      "forward(1)\nundefined_name()\n" -> GradingStatus.Incorrect)
    Future.traverse(cases)((source, _) => status(squareTest, ProgrammingStateRaw(Python, source))).flatMap { results =>
      assertEquals(results, cases.map(_._2))
      status(squareTest, ProgrammingStateRaw(Python, "while True:\n    pass\n"))
    }.map(result => assertEquals(errorMessage(result), "The unit-test execution exceeded its limits."))
  }

  test("tests that are not plain assertions are reported as an invalid definition") {
    Future.traverse(List("x = 1", "assert (", "import os\nassert True"))(tests => status(tests, square)).map { results =>
      results.foreach(result => assertEquals(errorMessage(result), "The unit-test definition is invalid."))
    }
  }

  test("a Java answer's methods can be invoked by name with arguments") {
    val java = ProgrammingStateRaw(Java, "class Drawing {\n    static void side(double length) {\n        Turtle.forward(length);\n        Turtle.turnRight(90);\n    }\n" +
      "    public static void main(String[] args) {\n        for (int i = 0; i < 4; i++) {\n            side(20);\n        }\n    }\n}\n")
    val methods = ProgramForUnitTestExecution.fromState(java).toOption.flatMap(_.commandEntryPoint).map(_.methodIdsByName.keySet)
    assertEquals(methods, Some(Set("side")), "main is the whole program, not a method target")
    val sideTest = """assert run_java("side", [20])["commands"] == [["forward", 20], ["right", 90]]"""
    for {
      whole <- status(squareTest, java)
      side <- status(sideTest, java)
      wrongArgument <- status(sideTest.replace("[20]", "[10]"), java)
      unknown <- status("""assert run_java("missing", [])["commands"] == []""", java)
    } yield {
      assertEquals((whole, side, wrongArgument), (GradingStatus.Correct, GradingStatus.Correct, GradingStatus.Incorrect))
      assertEquals(errorMessage(unknown), "The program invocation did not complete.")
    }
  }

  test("Snap answers run as straight-line turtle blocks; other Snap programs are reported as unsupported") {
    val straight = ProgrammingStateRaw(Python, "forward(20)\nright(90)\n" * 4)
    val answers = List[ProgrammingState](straight.toSnapXml, straight.toSnapScalaModel.toOption.get)
    for {
      passed <- Future.traverse(answers)(answer => status(squareTest, answer))
      loop <- status(squareTest, square.toSnapXml)
    } yield {
      assertEquals(passed, List.fill(answers.size)(GradingStatus.Correct))
      assertEquals(errorMessage(loop), "These Snap blocks do not yet have a verified unit-test translation.")
    }
  }

  test("a failed test publishes the observed drawing only when the test is visible") {
    val wrong = ProgrammingStateRaw(Python, "forward(10)\nright(90)\n")
    for {
      visible <- grader(squareTest, observe = true).gradeState(wrong, python)
      hidden <- grader(squareTest, GradingImportance.HIDDEN_TEST, observe = true).gradeState(wrong, python)
    } yield {
      assertEquals(visible.gradingStatus, GradingStatus.Incorrect)
      val observation = visible.additionalData.flatMap(_.programObservation).getOrElse(fail("no observation"))
      assertEquals(observation.commandCount, 2)
      assertEquals(observation.commands.map(_.name.universalInterpretation()), Seq("forward", "right"))
      assertEquals(hidden.gradingStatus, GradingStatus.Incorrect)
      assertEquals(hidden.additionalData, None, "a hidden test reveals nothing about the program")
    }
  }

  test("a failed test publishes the invocation and the first differing command when allowed") {
    val wrong = ProgrammingStateRaw(Python, "forward(20)\nright(45)\n")
    for {
      detailed <- grader(squareTest, invocation = true, comparison = true).gradeState(wrong, python)
      plain <- grader(squareTest).gradeState(wrong, python)
      hidden <- grader(squareTest, GradingImportance.HIDDEN_TEST, invocation = true, comparison = true).gradeState(wrong, python)
    } yield {
      val invocation = detailed.additionalData.flatMap(_.testInvocation).getOrElse(fail("no invocation"))
      assertEquals(invocation.target, None, "the whole program ran")
      val difference = invocation.commandComparison.getOrElse(fail("no comparison"))
      assertEquals((difference.actualCount, difference.expectedCount, difference.commandIndex), (2, 8, 1))
      assertEquals(difference.actual.map(_.args), Some(List(45.0)))
      assertEquals(difference.expected.map(_.args), Some(List(90.0)))
      assertEquals(plain.additionalData, None)
      assertEquals(hidden.additionalData, None)
    }
  }

  test("without a Python worker, or with an unexpected reply, the test reports an error rather than a grade") {
    val chatty = runPython.toLocalExecutionClient((request, _) => Future(CPythonRuntime.run(request)).map(_.copy(stdout = "noise")))
    val broken = runPython.toLocalExecutionClient((_, _) => Future.successful(RunPythonResult(ujson.Obj("status" -> "maybe"), "", "", Nil)))
    for {
      missing <- status(squareTest, square, Nil)
      noisy <- status(squareTest, square, Seq(chatty))
      invalid <- status(squareTest, square, Seq(broken))
    } yield {
      assertEquals(errorMessage(missing), "Unit-test execution is unavailable.")
      assertEquals(errorMessage(noisy), "The unit-test service returned an invalid result.")
      assertEquals(errorMessage(invalid), "The unit-test service returned an invalid result.")
    }
  }
}
