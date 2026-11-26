package it.evadid.vm.test

enum BeTestResult {
  case Passed, Failed
  case Error(message: String)

  def hasPassed: Boolean = this == Passed
}
