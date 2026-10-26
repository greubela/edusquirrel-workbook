package it.evadid.vm.test

import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingTestProgram
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

private[evadid] object ProgrammingTestRunner {
  private given ExecutionContext = ExecutionContext.global
  private val unavailable = BeTestResult.Error("Unit-test execution is unavailable.")
  private val invalidResult = BeTestResult.Error("The unit-test service returned an invalid result.")
  private val turtlePrimitives = ujson.Obj.from(SnapTurtleCatalog.Primitives.flatMap { primitive =>
    primitive.allPythonNames.map(_ -> ujson.Obj("command" -> primitive.turtleCommand,
      "arity" -> primitive.arity, "inputs" -> writeJs(primitive.inputKinds.map(_.toString))))
  })

  def execute(program: ProgrammingTestProgram, tests: String,
      clients: List[ExecutionClient]): Future[BeTestResult] = {
    val source = program.source
    val sourceLimit = if program.commandEntryPoint.isDefined then 131072 else 32768
    if source == null || tests == null || source.length > sourceLimit || tests.trim.isEmpty || tests.length > 8192 then
      Future.successful(BeTestResult.Error("The unit-test input is empty or exceeds its limits."))
    else {
      val request = WorkerCommands.RunPythonRequest(script, context = Map(
        "_eva_source" -> ujson.Str(source), "_eva_tests" -> ujson.Str(tests),
        "_eva_entry_point" -> program.commandEntryPoint.map(writeJs(_)).getOrElse(ujson.Null),
        "_eva_turtle_primitives" -> turtlePrimitives),
        captureStdout = false, captureStderr = false)
      val factory = WorkerCommands.runPython
      val command = ExecutionCommand(factory.name, writeJs(request))
      Try(clients.find(_.canExecuteCommand(command))).toOption.flatten match {
        case None => Future.successful(unavailable)
        case Some(client) => Try(factory.sendCommandTo(client, request)).fold(
          _ => Future.successful(unavailable),
          _.map { reply =>
            val report = reply.result
            if report.stdout == "" && report.stderr == "" && report.callbackOps == Nil then decode(report.value)
            else invalidResult
          }.recover { case NonFatal(_) => unavailable })
      }
    }
  }

  private def decode(value: ujson.Value): BeTestResult = Try {
    require(value.obj.keySet == Set("status", "reason"))
    (value("status").str, value("reason").str) match {
      case ("passed", "") => BeTestResult.Passed
      case ("failed", "assertion" | "student-syntax" | "student-runtime") => BeTestResult.Failed
      case ("error", "invalid-tests") => BeTestResult.Error("The unit-test definition is invalid.")
      case ("error", "execution-limit") => BeTestResult.Error("The unit-test execution exceeded its limits.")
      case ("error", "test-runtime") => BeTestResult.Error("The unit tests could not be evaluated.")
      case ("error", "program-execution" | "java-execution") => BeTestResult.Error("The program invocation did not complete.")
      case _ => throw IllegalArgumentException("Invalid unit-test result.")
    }
  }.getOrElse(invalidResult)

  private val script = """def _eva_evaluate(source, tests, entry_point, turtle_primitives):
    import ast
    import math
    import sys

    budget = [250000, 4096, 10000]
    compiled = isinstance(entry_point, dict)

    def result(status, reason=""):
        if any(remaining < 0 for remaining in budget):
            return {"status": "error", "reason": "execution-limit"}
        return {"status": status, "reason": reason}

    try:
        tree = ast.parse(tests, filename="<workbook-tests>", mode="exec")
        if not tree.body or not all(isinstance(node, ast.Assert) for node in tree.body):
            return result("error", "invalid-tests")
        test_code = compile(tree, "<workbook-tests>", "exec", optimize=0)
    except BaseException:
        return result("error", "invalid-tests")
    try:
        student_code = compile(source, "<student-source>", "exec", optimize=0)
    except SyntaxError:
        return result("error", "program-execution") if compiled else result("failed", "student-syntax")
    except BaseException:
        return result("error", "execution-limit")

    class BudgetExceeded(BaseException):
        pass

    class ProgramExecutionError(BaseException):
        pass

    commands = []

    def primitive(specification):
        def record(*arguments):
            if len(arguments) != specification["arity"]:
                raise TypeError("Wrong number of Turtle arguments.")
            for kind, value in zip(specification["inputs"], arguments):
                if kind == "Numeric" and (type(value) not in (int, float) or not math.isfinite(value)):
                    raise TypeError("Turtle arguments must be finite numbers.")
                if kind == "Bool" and type(value) is not bool:
                    raise TypeError("This Turtle argument must be boolean.")
                if kind == "Color" and type(value) is not str:
                    raise TypeError("This Turtle argument must be a color string.")
            if specification["command"] != "receive_go":
                budget[2] -= 1
                if budget[2] < 0:
                    raise BudgetExceeded()
                commands.append([specification["command"], *arguments])
        return record

    def trace(frame, event, argument):
        if frame.f_code.co_filename in ("<student-source>", "<workbook-tests>"):
            frame.f_trace_opcodes = True
            if event in ("line", "opcode"):
                budget[0] -= 1
                if budget[0] < 0:
                    raise BudgetExceeded()
        return trace

    class Output:
        encoding = "utf-8"

        def write(self, text):
            budget[1] -= len(text)
            if budget[1] < 0:
                raise BudgetExceeded()
            return len(text)

        def flush(self):
            pass

        def isatty(self):
            return False

    previous = (sys.gettrace(), sys.stdout, sys.stderr)
    try:
        sys.stdout = Output()
        sys.stderr = Output()
        sys.settrace(trace)
        student = {name: primitive(specification) for name, specification in turtle_primitives.items()}
        student["__name__"] = "__main__"
        try:
            exec(student_code, student, student)
        except (BudgetExceeded, MemoryError, RecursionError):
            return result("error", "execution-limit")
        except BaseException:
            return result("error", "program-execution") if compiled else result("failed", "student-runtime")

        if any(remaining < 0 for remaining in budget):
            return result("error", "execution-limit")
        snapshot = tuple(tuple(command) for command in commands)
        invocations = [0]

        def invoke(methodName=None, arguments=()):
            if not compiled or (methodName is not None and
                    (type(methodName) is not str or methodName not in entry_point["methods"])):
                raise ProgramExecutionError()
            invocations[0] += 1
            execute = student[entry_point["functionName"]]
            method = None if methodName is None else entry_point["methods"][methodName]
            invocation = execute(method, arguments)
            if invocation.get("status") != "Completed":
                raise ProgramExecutionError()
            return invocation

        def run_program():
            recorded = invoke()["commands"] if compiled else snapshot
            return {"status": "Completed", "commands": [list(command) for command in recorded]}

        namespace = {"run_java": invoke} if compiled else student
        namespace["run_program"] = run_program
        try:
            exec(test_code, namespace, namespace)
        except (BudgetExceeded, MemoryError, RecursionError):
            return result("error", "execution-limit")
        except ProgramExecutionError:
            return result("error", "program-execution")
        except AssertionError:
            return result("failed", "assertion")
        except BaseException as error:
            cursor = error.__traceback__
            while cursor is not None:
                if not compiled and cursor.tb_frame.f_code.co_filename == "<student-source>":
                    return result("failed", "student-runtime")
                cursor = cursor.tb_next
            return result("error", "test-runtime")
        if compiled and invocations[0] == 0:
            return result("error", "invalid-tests")
        return result("passed")
    finally:
        sys.settrace(previous[0])
        sys.stdout, sys.stderr = previous[1], previous[2]

_eva_evaluate(_eva_source, _eva_tests, _eva_entry_point, _eva_turtle_primitives)
"""
}
