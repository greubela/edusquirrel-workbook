package it.evadid.evacuation.core.utility

import scala.language.implicitConversions

object GeneralUtility {


  implicit def factoryToEitherFactory[T](factory: String => T): String => Either[T, Exception] =
    str => try {
      Left(factory(str))
    } catch {
      case ex: Exception => Right(ex)
    }


  def formatDuration(timeInMs: Long, oneMilliPosition: Boolean = true): String = {

    var res: String = ""
    var suffix: String = ""

    val toMillisPart = if (oneMilliPosition) (timeInMs % 1000) / 100 else (timeInMs % 1000)
    val toSeconds: Long = timeInMs / 1000
    val toSecondsPart: Long = toSeconds % 60
    val toMinutes = toSeconds / 60
    val toMinutesPart: Long = toMinutes % 60
    val toHours = toMinutes / 60
    val toHoursPart: Long = toHours

    if (toHoursPart > 0) {
      res += s"$toHoursPart:"
      suffix = "h"
    }

    if (toMinutesPart > 0 && suffix.nonEmpty) {
      res += (f"$toMinutesPart%02d" + ":")
    } else if (toMinutesPart > 0) {
      res += s"$toMinutesPart:"
      suffix = "min"
    }

    if (suffix.nonEmpty) {
      res += f"$toSecondsPart%02d"
    } else {
      res += toSecondsPart
      suffix = "s"
    }

    if (toMillisPart > 0) {
      res += "." + toMillisPart
    }

    res + " " + suffix

  }

}
