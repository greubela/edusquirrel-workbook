package it.evadid.vm.test

import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingTestProgramObservation
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingTestProgram
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

private[evadid] object ProgrammingTestRunner {
  case class Result(result: BeTestResult, programObservation: Option[ProgrammingTestProgramObservation] = None)

  private given ExecutionContext = ExecutionContext.global
  private val unavailable = Result(BeTestResult.Error("Unit-test execution is unavailable."))
  private val invalidResult = Result(BeTestResult.Error("The unit-test service returned an invalid result."))
  private val turtlePrimitives = ujson.Obj.from(SnapTurtleCatalog.Primitives.flatMap { primitive =>
    primitive.allPythonNames.map(_ -> ujson.Obj("command" -> primitive.turtleCommand,
      "arity" -> primitive.arity, "inputs" -> writeJs(primitive.inputKinds.map(_.toString))))
  })

  def execute(program: ProgrammingTestProgram, tests: String,
      clients: List[ExecutionClient], observeProgram: Boolean = false): Future[Result] = {
    val source = program.source
    val sourceLimit = if program.commandEntryPoint.isDefined then 131072 else 32768
    if source == null || tests == null || source.length > sourceLimit || tests.trim.isEmpty || tests.length > 8192 then
      Future.successful(Result(BeTestResult.Error("The unit-test input is empty or exceeds its limits.")))
    else {
      val request = WorkerCommands.RunPythonRequest(script, context = Map(
        "_eva_source" -> ujson.Str(source), "_eva_tests" -> ujson.Str(tests),
        "_eva_entry_point" -> program.commandEntryPoint.map(writeJs(_)).getOrElse(ujson.Null),
        "_eva_observe_program" -> ujson.Bool(observeProgram),
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
            if report.stdout == "" && report.stderr == "" && report.callbackOps == Nil then decode(report.value, observeProgram)
            else invalidResult
          }.recover { case NonFatal(_) => unavailable })
      }
    }
  }

  private def decode(value: ujson.Value, observeProgram: Boolean): Result = Try {
    require(value != null)
    val keys = value.obj.keySet.toSet
    require(keys == Set("status", "reason") || observeProgram && keys == Set("status", "reason", "programObservation"))
    val status = value("status").str
    val reason = value("reason").str
    val result = (status, reason) match {
      case ("passed", "") => BeTestResult.Passed
      case ("failed", "assertion" | "student-syntax" | "student-runtime") => BeTestResult.Failed
      case ("error", "invalid-tests") => BeTestResult.Error("The unit-test definition is invalid.")
      case ("error", "execution-limit") => BeTestResult.Error("The unit-test execution exceeded its limits.")
      case ("error", "test-runtime") => BeTestResult.Error("The unit tests could not be evaluated.")
      case ("error", "program-execution" | "java-execution") => BeTestResult.Error("The program invocation did not complete.")
      case _ => throw IllegalArgumentException("Invalid unit-test result.")
    }
    val observation = value.obj.get("programObservation").filter(_ != ujson.Null).map { report =>
      require(status == "passed" || status == "failed" && reason == "assertion")
      require(report.obj.keySet.toSet == Set("commandCount", "commands"))
      val count = report("commandCount").num
      require(count.isValidInt)
      val rows = report("commands").arr
      require(rows.size <= ProgrammingTestProgramObservation.PrefixLength)
      val commands = rows.toList.map { row =>
        require(row.arr.nonEmpty)
        val name = row.arr.head.str
        require(ProgrammingTestProgramObservation.commandArity(name).contains(row.arr.size - 1))
        TurtleCommand[Double](name, row.arr.tail.map(_.num).toList)
      }
      ProgrammingTestProgramObservation(count.toInt, commands)
    }
    Result(result, observation)
  }.getOrElse(invalidResult)

  private val script = """def _eva_evaluate(source, tests, entry_point, turtle_primitives, observe_program):
    import ast
    import math
    import sys

    budget = [250000, 4096, 10000]
    compiled = isinstance(entry_point, dict)
    observed = [None]
    observation_allowed = [True]

    def result(status, reason=""):
        if any(remaining < 0 for remaining in budget):
            status, reason = "error", "execution-limit"
        report = {"status": status, "reason": reason}
        if observe_program:
            report["programObservation"] = None
            if observation_allowed[0] and observed[0] is not None and (
                    status == "passed" or status == "failed" and reason == "assertion"):
                report["programObservation"] = {"commandCount": len(observed[0]),
                    "commands": [[command[0], *[float(value) for value in command[1:]]]
                        for command in observed[0][:32]]}
        return report

    def suppress_observation():
        observation_allowed[0] = False
        observed[0] = None

    def observe(commands):
        if not observe_program or not observation_allowed[0]:
            return
        if len(commands) > 10000:
            suppress_observation()
            return
        by_command = {value["command"]: value for value in turtle_primitives.values()}
        for command in commands:
            specification = by_command.get(command[0])
            if (specification is None or specification["command"] == "receive_go" or
                    any(kind != "Numeric" for kind in specification["inputs"]) or
                    len(command) != specification["arity"] + 1 or
                    any(type(value) not in (int, float) or not math.isfinite(value) or
                        abs(value) > 9007199254740991 for value in command[1:])):
                suppress_observation()
                return
        frozen = tuple(tuple(command) for command in commands)
        if observed[0] is not None and frozen != observed[0]:
            suppress_observation()
        else:
            observed[0] = frozen

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
            if (observe_program and event == "call" and frame.f_code.co_filename == "<student-source>" and
                    frame.f_back is not None and frame.f_back.f_code.co_filename == "<workbook-tests>"):
                suppress_observation()
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
            if observe_program and (methodName is not None or arguments):
                suppress_observation()
            if not compiled or (methodName is not None and
                    (type(methodName) is not str or methodName not in entry_point["methods"])):
                raise ProgramExecutionError()
            invocations[0] += 1
            execute = student[entry_point["functionName"]]
            method = None if methodName is None else entry_point["methods"][methodName]
            invocation = execute(method, arguments)
            if invocation.get("status") != "Completed":
                raise ProgramExecutionError()
            if methodName is None and not arguments:
                observe(invocation["commands"])
            return invocation

        def run_program():
            recorded = invoke()["commands"] if compiled else snapshot
            if not compiled:
                observe(recorded)
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

_eva_evaluate(_eva_source, _eva_tests, _eva_entry_point, _eva_turtle_primitives, _eva_observe_program)
"""
}
