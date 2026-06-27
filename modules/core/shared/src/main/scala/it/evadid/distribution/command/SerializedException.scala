package it.evadid.distribution.command

import it.evadid.distribution.command.SerializedException.SimpleStackTraceElement

case class SerializedException(msg: String, stackTrace: Array[SimpleStackTraceElement], cause: Option[SerializedException]) extends Throwable(msg){


}

object SerializedException {


  case class SimpleStackTraceElement(val declaringClass: String,
                                     val MethodName: String,
                                     val fileName: String,
                                     val lineNumber: Int)

  private def toSimple(el: StackTraceElement): SimpleStackTraceElement = SimpleStackTraceElement(el.getClassName, el.getMethodName, el.getFileName, el.getLineNumber)


  def apply(err: Throwable): SerializedException = {
    if (err.getCause != null) return SerializedException(err.getMessage, err.getStackTrace.map(toSimple), None)
    else return {
      val cause = SerializedException(err.getCause)
      SerializedException(err.getMessage, err.getStackTrace.map(toSimple), Some(cause))
    }

  }

}

