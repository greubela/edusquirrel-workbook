package it.evadid.vm.test

import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.WorkerCommands
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

private[evadid] object ProgrammingTestRunner {
  private given ExecutionContext = ExecutionContext.global
  private val unavailable = BeTestResult.Error("Unit-test execution is unavailable.")
  private val invalidResult = BeTestResult.Error("The unit-test service returned an invalid result.")

  def execute(source: String, tests: String, methods: Option[Map[String, Int]],
      clients: List[ExecutionClient]): Future[BeTestResult] = {
    val sourceLimit = if methods.isDefined then 131072 else 32768
    if source == null || tests == null || source.length > sourceLimit || tests.trim.isEmpty || tests.length > 8192 then
      Future.successful(BeTestResult.Error("The unit-test input is empty or exceeds its limits."))
    else {
      val request = WorkerCommands.RunPythonRequest(script, context = Map(
        "_eva_source" -> ujson.Str(source), "_eva_tests" -> ujson.Str(tests),
        "_eva_is_java" -> ujson.Bool(methods.isDefined),
        "_eva_java_methods" -> writeJs(methods.getOrElse(Map.empty[String, Int]))),
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
      case ("error", "java-execution") => BeTestResult.Error("The Java Turtle invocation did not complete.")
      case _ => throw IllegalArgumentException("Invalid unit-test result.")
    }
  }.getOrElse(invalidResult)

  private val script = """def _eva_evaluate(source, tests, is_java, java_methods):
    import ast
    import sys

    budget = [250000, 4096]

    def result(status, reason=""):
        if budget[0] < 0 or budget[1] < 0:
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
        return result("error", "java-execution") if is_java else result("failed", "student-syntax")
    except BaseException:
        return result("error", "execution-limit")

    class BudgetExceeded(BaseException):
        pass

    class JavaExecutionError(BaseException):
        pass

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
        student = {"__name__": "__main__"}
        try:
            exec(student_code, student, student)
        except (BudgetExceeded, MemoryError, RecursionError):
            return result("error", "execution-limit")
        except BaseException:
            return result("error", "java-execution") if is_java else result("failed", "student-runtime")

        if budget[0] < 0 or budget[1] < 0:
            return result("error", "execution-limit")
        if not is_java:
            namespace = student
        else:
            execute = student["java_turtle_run"]
            invocations = [0]

            def run_java(methodName=None, arguments=()):
                if methodName is not None and (type(methodName) is not str or methodName not in java_methods):
                    raise JavaExecutionError()
                invocations[0] += 1
                invocation = execute(None if methodName is None else java_methods[methodName], arguments)
                if invocation.get("status") != "Completed":
                    raise JavaExecutionError()
                return invocation

            namespace = {"run_java": run_java}
        try:
            exec(test_code, namespace, namespace)
        except (BudgetExceeded, MemoryError, RecursionError):
            return result("error", "execution-limit")
        except JavaExecutionError:
            return result("error", "java-execution")
        except AssertionError:
            return result("failed", "assertion")
        except BaseException as error:
            cursor = error.__traceback__
            while cursor is not None:
                if not is_java and cursor.tb_frame.f_code.co_filename == "<student-source>":
                    return result("failed", "student-runtime")
                cursor = cursor.tb_next
            return result("error", "test-runtime")
        if is_java and invocations[0] == 0:
            return result("error", "invalid-tests")
        return result("passed")
    finally:
        sys.settrace(previous[0])
        sys.stdout, sys.stderr = previous[1], previous[2]

_eva_evaluate(_eva_source, _eva_tests, _eva_is_java, _eva_java_methods)
"""
}
