package it.evadid.vm.simulation.python

import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.commandTypes.WorkerCommands.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStatePythonStructured
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future}

class PythonProgramExecutionSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global

  test("Python state sends its complete source to the worker and returns captured console output") {
    val state = ProgrammingStatePythonStructured("from turtle import *\nforward(100)\nprintln(\"hai\")\n")
    val expected = RunPythonResult(ujson.Null, "forward(100)\nhai\n", "", Nil)
    given ExecutionClient = runPython.toLocalExecutionClient((request, _) => {
      assertEquals(request.code, state.toString)
      assert(request.logTurtleCalls)
      assert(request.resetGlobals && request.captureStdout && request.captureStderr)
      Future.successful(expected)
    })
    state.startExecution().map(result => assertEquals(result, expected))
  }

  test("Python execution failures remain failed futures") {
    given ExecutionClient = runPython.toLocalExecutionClient((_, _) =>
      Future.failed(IllegalArgumentException("Python execution failed")))
    ProgrammingStatePythonStructured("raise ValueError('bad')").startExecution().transform {
      case scala.util.Failure(error) => scala.util.Try(assert(error.getMessage.contains("Python execution failed")))
      case scala.util.Success(_) => scala.util.Failure(new AssertionError("The failed execution must not succeed."))
    }
  }
}
