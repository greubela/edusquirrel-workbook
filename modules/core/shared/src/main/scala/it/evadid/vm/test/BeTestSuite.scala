package it.evadid.vm.test

import it.evadid.vm.BeProgram
import upickle.default.*

sealed trait BeTestSuite derives ReadWriter{

  def evaluateOn(program: BeProgram): BeTestResult

}

case class SampleBeTest(pythonTestCode: String) extends BeTestSuite derives ReadWriter {

  override def evaluateOn(program: BeProgram): BeTestResult = ???
}
