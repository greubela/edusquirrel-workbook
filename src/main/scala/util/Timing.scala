package util

object Timing {


  def executeAndTime[R](func: () => R, infoStr: String = "[unkown purpose]"): R = {
    val startTime = System.currentTimeMillis()
    val res = func.apply()
    val endTime = System.currentTimeMillis()
    println("Measured time for " + infoStr + ": " + (endTime - startTime) + " ms")
    res
  }


}
