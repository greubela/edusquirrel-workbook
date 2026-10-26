package it.evadid.vm.test

import it.evadid.distribution.clients.ExecutionClient
import it.evadid.vm.io.stringPrinter.python.JavaTurtlePythonExport
import it.evadid.vm.parsing.java.turtle.JavaTurtleVmPrograms
import upickle.default.*

import scala.concurrent.Future

sealed trait BeTestSuite derives ReadWriter {
  def evaluateOn(pythonSource: String, availableExecutionClients: List[ExecutionClient]): Future[BeTestResult]
  def evaluateJavaTurtle(program: JavaTurtleVmPrograms.Program, availableExecutionClients: List[ExecutionClient]): Future[BeTestResult]
}

/** A nonempty suite of top-level Python assertions. Java Turtle assertions must
  * call run_java(methodName, arguments), returning a completed execution report;
  * omitting methodName invokes main, not a Java return-valued function.
  * Execution is limited to 250,000 line/opcode events and 4096 output characters.
  */
case class SampleBeTest(pythonTestCode: String) extends BeTestSuite derives ReadWriter {
  override def evaluateOn(pythonSource: String, clients: List[ExecutionClient]): Future[BeTestResult] =
    ProgrammingTestRunner.execute(pythonSource, pythonTestCode, None, clients)

  override def evaluateJavaTurtle(program: JavaTurtleVmPrograms.Program, clients: List[ExecutionClient]): Future[BeTestResult] = {
    val exported = JavaTurtlePythonExport.render(program)
    ProgrammingTestRunner.execute(exported.source, pythonTestCode, Some(exported.methods.view.mapValues(_.index).toMap), clients)
  }
}

