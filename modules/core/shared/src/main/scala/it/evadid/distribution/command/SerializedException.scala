package it.evadid.distribution.command

import it.evadid.distribution.command.SerializedException.SimpleStackTraceElement

case class SerializedException(msg: String, stackTrace: Array[SimpleStackTraceElement], cause: Option[SerializedException]) extends Throwable(msg) {

  def asCauseOf(e: Throwable): SerializedException = SerializedException(e, this)

}

object SerializedException {


  case class SimpleStackTraceElement(val declaringClass: String,
                                     val MethodName: String,
                                     val fileName: String,
                                     val lineNumber: Int)

  private def toSimple(el: StackTraceElement): SimpleStackTraceElement = SimpleStackTraceElement(el.getClassName, el.getMethodName, el.getFileName, el.getLineNumber)

  def apply(err: Throwable, cause: Throwable): SerializedException = SerializedException(err.getMessage, err.getStackTrace.map(toSimple), Option(SerializedException(cause)))

  def apply(err: Throwable): SerializedException = {
    val cause = Option(err.getCause).map(SerializedException.apply)
    SerializedException(err.getMessage, err.getStackTrace.map(toSimple), cause)
  }

  def apply(msg: String): SerializedException = SerializedException(Exception(msg))

  def apply(msg: String, cause: Throwable): SerializedException = SerializedException(Exception(msg, cause))

  def apply(msg: String, cause: Option[Throwable]): SerializedException = {
    val exception = if (cause.nonEmpty) Exception(msg, cause.get) else Exception(msg)
    SerializedException(exception)
  }

}

