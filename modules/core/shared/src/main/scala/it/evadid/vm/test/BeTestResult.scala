package it.evadid.vm.test

import upickle.default.ReadWriter

enum BeTestResult derives ReadWriter {
  case Passed, Failed
  case Error(message: String)

  def hasPassed: Boolean = this == Passed
}
