package it.evadid.vm.test

sealed trait BeTestResult {

  def hasPassed: Boolean

}
