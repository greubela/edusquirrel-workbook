package it.evadid.vm.test

import it.evadid.core.datastructures.language.AppLanguage.{Java, Lisp, Python, SnapLanguage}
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.distribution.clients.LocalExecutionClient
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.distribution.commandTypes.WorkerCommands.{PythonCallback, RunPythonRequest, RunPythonResult}
import it.evadid.util.logging.Logger
import it.evadid.vm.conversion.unittest.ProgramForUnitTestExecution
import it.evadid.workbook.interactions.programming.ProgrammingTestInvocation
import it.evadid.workbook.interactions.programming.ProgrammingTestInvocation.Scalar
import it.evadid.workbook.interactions.programming.state.{ProgrammingStateBeExpression, ProgrammingStateRaw}
import munit.FunSuite

import scala.concurrent.{ExecutionContext, Future}

class ProgrammingTestRunnerSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val program = ProgramForUnitTestExecution("def area(w, h):\n    return w * h\n")

  private final class Worker(reply: RunPythonRequest => Future[RunPythonResult]) extends LocalExecutionClient[RunPythonRequest, RunPythonResult] {
    val requests = State(Vector.empty[RunPythonRequest])
    override def canExecuteCommand(command: ExecutionCommand): Boolean = command.name == WorkerCommands.runPython.name
    override protected def rawExecution(logger: Logger, input: RunPythonRequest): Future[RunPythonResult] = {
      requests.update(_ :+ input)
      reply(input)
    }
  }
  private def worker(value: ujson.Value, stdout: String = "", callbacks: Seq[PythonCallback] = Nil) =
    new Worker(_ => Future.successful(RunPythonResult(value, stdout, "", callbacks)))
  private def report(status: String, reason: String, extra: (String, ujson.Value)*): ujson.Value =
    ujson.Obj.from(Seq("status" -> ujson.Str(status), "reason" -> ujson.Str(reason)) ++ extra)
  private def run(client: Worker, observeProgram: Boolean = false, observeInvocation: Boolean = false, tests: String = "assert area(2, 3) == 6") =
    ProgrammingTestRunner.execute(program, tests, Seq(client), observeProgram, observeInvocation, observeComparison = true)

  test("worker reports map to passed, failed and explained error results") {
    val cases = Seq(
      ("passed", "") -> BeTestResult.Passed,
      ("failed", "assertion") -> BeTestResult.Failed,
      ("failed", "student-runtime") -> BeTestResult.Failed,
      ("error", "invalid-tests") -> BeTestResult.Error("The unit-test definition is invalid."),
      ("error", "execution-limit") -> BeTestResult.Error("The unit-test execution exceeded its limits."),
      ("error", "test-runtime") -> BeTestResult.Error("The unit tests could not be evaluated."),
      ("error", "java-execution") -> BeTestResult.Error("The program invocation did not complete."),
      ("passed", "assertion") -> BeTestResult.Error("The unit-test service returned an invalid result."))
    Future.traverse(cases) { case ((status, reason), expected) =>
      run(worker(report(status, reason))).map(result => assertEquals(result.result, expected, s"$status/$reason"))
    }.map(_ => ())
  }

  test("the request carries source, tests and observation flags without capturing output") {
    val client = worker(report("passed", ""))
    run(client, observeProgram = true).map { _ =>
      val request = client.requests.now().head
      assertEquals(request.context("_eva_source").str, program.pythonSource)
      assertEquals(request.context("_eva_tests").str, "assert area(2, 3) == 6")
      assertEquals(request.context("_eva_entry_point"), ujson.Null)
      assertEquals(request.context("_eva_observe_program"), ujson.Bool(true))
      assertEquals(request.context("_eva_publish_command_comparison"), ujson.Bool(false))
      assert(request.context("_eva_turtle_primitives").obj.nonEmpty)
      assert(!request.captureStdout && !request.captureStderr)
      assert(request.code.contains("def _eva_evaluate"))
    }
  }

  test("program observations are validated and decoded into turtle commands") {
    val observation = ujson.Obj("commandCount" -> 2, "commands" -> ujson.Arr(ujson.Arr("forward", 10), ujson.Arr("right", 90)))
    for {
      accepted <- run(worker(report("passed", "", "programObservation" -> observation)), observeProgram = true)
      unexpected <- run(worker(report("passed", "", "programObservation" -> observation)))
      badArity <- run(worker(report("passed", "", "programObservation" ->
        ujson.Obj("commandCount" -> 1, "commands" -> ujson.Arr(ujson.Arr("forward"))))), observeProgram = true)
      onError <- run(worker(report("error", "test-runtime", "programObservation" -> observation)), observeProgram = true)
      nullObservation <- run(worker(report("passed", "", "programObservation" -> ujson.Null)), observeProgram = true)
    } yield {
      assertEquals(accepted.result, BeTestResult.Passed)
      assertEquals(accepted.programObservation.map(_.commandCount), Some(2))
      assertEquals(accepted.programObservation.map(_.commands.map(_.name.universalInterpretation())), Some(List("forward", "right")))
      assertEquals(accepted.programObservation.map(_.commands.head.args), Some(List(10.0)))
      assertEquals(unexpected.result, BeTestResult.Error("The unit-test service returned an invalid result."))
      assertEquals(badArity.result, BeTestResult.Error("The unit-test service returned an invalid result."))
      assertEquals(onError.result, BeTestResult.Error("The unit-test service returned an invalid result."))
      assertEquals(nullObservation.programObservation, None)
    }
  }

  test("failed assertions may publish the invocation of a defined entry point") {
    val invocation = ujson.Obj("target" -> ujson.Null, "arguments" -> ujson.Arr())
    run(worker(report("failed", "assertion", "testInvocation" -> invocation)), observeInvocation = true).map { result =>
      assertEquals(result.result, BeTestResult.Failed)
      assertEquals(result.testInvocation, Some(ProgrammingTestInvocation(None, Vector.empty)))
    }
  }

  test("missing workers, failing workers, polluted output and oversized inputs are unavailable or rejected") {
    val unavailable = BeTestResult.Error("Unit-test execution is unavailable.")
    for {
      none <- ProgrammingTestRunner.execute(program, "assert True", Nil)
      failing <- run(new Worker(_ => Future.failed(IllegalStateException("offline"))))
      printed <- run(worker(report("passed", ""), stdout = "debug"))
      callbacks <- run(worker(report("passed", ""), callbacks = Seq(PythonCallback("turtle", "forward", Nil))))
      emptyTests <- run(worker(report("passed", "")), tests = "   ")
      longTests <- run(worker(report("passed", "")), tests = "x" * 8193)
    } yield {
      assertEquals(none.result, unavailable)
      assertEquals(failing.result, unavailable)
      assertEquals(printed.result, BeTestResult.Error("The unit-test service returned an invalid result."))
      assertEquals(callbacks.result, BeTestResult.Error("The unit-test service returned an invalid result."))
      assertEquals(emptyTests.result, BeTestResult.Error("The unit-test input is empty or exceeds its limits."))
      assertEquals(longTests.result, BeTestResult.Error("The unit-test input is empty or exceeds its limits."))
    }
  }

  test("programs are prepared for unit-test execution from every supported representation") {
    assertEquals(ProgramForUnitTestExecution.fromState(ProgrammingStateRaw(Python, "x = 1")).map(_.pythonSource), Right("x = 1"))
    val java = ProgramForUnitTestExecution.fromState(ProgrammingStateRaw(Java,
      "class Main { static void square(double size) { Turtle.forward(size); } public static void main(String[] args) { square(3); } }"))
    assert(java.exists(_.commandEntryPoint.exists(_.methodIdsByName.contains("square"))), java)
    val snap = ProgramForUnitTestExecution.fromState(ProgrammingStateRaw(Python, "forward(10)").toSnapXml)
    assert(snap.isRight, snap)
    val expression = ProgramForUnitTestExecution.fromState(ProgrammingStateRaw(Python, "forward(10)").toBeExpression)
    assert(expression.exists(_.pythonSource.contains("forward")), expression)
    assert(ProgramForUnitTestExecution.fromState(ProgrammingStateRaw(Python, "forward(10)").toPythonStructured).isRight)
    assert(ProgramForUnitTestExecution.fromState(ProgrammingStateRaw(Lisp, "(forward 10)")).isLeft)
    assert(ProgramForUnitTestExecution.fromState(ProgrammingStateRaw(Java, "class {")).isLeft)
  }

  test("invocation reports accept bounded scalar arguments and reject malformed values") {
    val valid = ujson.Obj("target" -> "area", "arguments" -> ujson.Arr(
      ujson.Obj("kind" -> "bool", "value" -> true), ujson.Obj("kind" -> "int", "value" -> "-12"),
      ujson.Obj("kind" -> "double", "value" -> java.lang.Long.toHexString(java.lang.Double.doubleToLongBits(1.5)))))
    val parsed = ProgrammingTestInvocation.fromReport(valid)
    assertEquals(parsed, Some(ProgrammingTestInvocation(Some("area"), Vector(Scalar.Bool(true), Scalar.Integer("-12"),
      Scalar.Float64("3ff8000000000000")))))
    assertEquals(upickle.default.read[ProgrammingTestInvocation](upickle.default.write(parsed.get)), parsed.get)
    val invalid = Seq(
      ujson.Obj("target" -> "area"),
      ujson.Obj("target" -> " area", "arguments" -> ujson.Arr()),
      ujson.Obj("target" -> ujson.Null, "arguments" -> ujson.Arr(ujson.Obj("kind" -> "int", "value" -> "1"))),
      ujson.Obj("target" -> "f", "arguments" -> ujson.Arr(ujson.Obj("kind" -> "int", "value" -> "01"))),
      ujson.Obj("target" -> "f", "arguments" -> ujson.Arr(ujson.Obj("kind" -> "double", "value" -> "7ff0000000000000"))),
      ujson.Obj("target" -> "f", "arguments" -> ujson.Arr(ujson.Obj("kind" -> "text", "value" -> "x"))),
      ujson.Obj("target" -> "f", "arguments" -> ujson.Arr.from(Seq.fill(9)(ujson.Obj("kind" -> "bool", "value" -> false)))),
      ujson.Str("area"))
    invalid.foreach(value => assertEquals(ProgrammingTestInvocation.fromReport(value), None, value.render()))
    intercept[IllegalArgumentException](ProgrammingTestInvocation(None, Vector(Scalar.Bool(true))))
    intercept[Exception](upickle.default.read[Scalar]("""{"kind":"int","value":"x"}"""))
  }

  test("command comparisons are read only with the requested flag and when consistent") {
    def comparisonWith(expected: Int) = ujson.Obj("actualCount" -> 2, "expectedCount" -> 2, "commandIndex" -> 1,
      "actual" -> ujson.Arr("forward", 10), "expected" -> ujson.Arr("forward", expected))
    val comparison = comparisonWith(20)
    val report = ujson.Obj("target" -> "draw", "arguments" -> ujson.Arr(), "commandComparison" -> comparison)
    assertEquals(ProgrammingTestInvocation.fromReport(report, false).flatMap(_.commandComparison), None)
    val read = ProgrammingTestInvocation.fromReport(report, true).flatMap(_.commandComparison)
    assertEquals(read.map(c => (c.commandIndex, c.actual.map(_.args), c.expected.map(_.args))), Some((1, Some(List(10.0)), Some(List(20.0)))))
    val same = comparisonWith(10)
    assertEquals(ProgrammingTestInvocation.fromReport(ujson.Obj("target" -> "draw", "arguments" -> ujson.Arr(), "commandComparison" -> same), true)
      .flatMap(_.commandComparison), None)
  }
}
