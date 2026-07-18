package it.evadid.evacuation.core.utility

class Timer(name: String = "Timer") {

  case class PointInTime(time: Long, description: String)

  val points: collection.mutable.ListBuffer[PointInTime] = collection.mutable.ListBuffer[PointInTime](PointInTime(System.currentTimeMillis(), "START"))

  def addPoint(description: String): Unit = {
    points += PointInTime(System.currentTimeMillis(), description)
  }

  def stop(): Unit = {
    addPoint("STOP")
    print()
  }

  def print(): Unit = {
    println("\nTimer '" + name + "' stats. Total time: " + ((points.last.time - points.head.time)/1000.0) + "s")
    points.toList.zip(points.tail.toList).foreach(tup => {
      val startPoint = tup._1
      val endPoint = tup._2

      val diff = (endPoint.time - startPoint.time)/1000.0
      println("    '" + startPoint.description + "' -> '" + endPoint.description + "' in " + diff + "s")
    })

  }


}
