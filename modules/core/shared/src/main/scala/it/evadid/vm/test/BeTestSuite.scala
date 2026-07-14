package it.evadid.vm.test

import it.evadid.vm.BeProgram

trait BeTestSuite {

  def evaluateOn(program: BeProgram): BeTestResult

}
