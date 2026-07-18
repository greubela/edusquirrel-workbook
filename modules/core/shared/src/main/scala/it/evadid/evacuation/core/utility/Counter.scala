package it.evadid.evacuation.core.utility

class Counter(){

  private var count = -1

  def getNext: Integer = {
    count += 1
    count
  }

  def reset(): Unit = {
    count = -1
  }

}
