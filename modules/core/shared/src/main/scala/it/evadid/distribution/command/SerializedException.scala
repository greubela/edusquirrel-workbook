package it.evadid.distribution.command

import upickle.default.*

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.SerializedException.SimpleStackTraceElement

case class SerializedException(msg: String, stackTrace: Seq[SimpleStackTraceElement], cause: Option[SerializedException]) extends Throwable(msg) derives ReadWriter {

  def asCauseOf(e: Throwable): SerializedException = SerializedException(e, this)

  lazy val serializer: Serializer[SerializedException] = DefaultSerializer.serializerExceptionS

  lazy val toJson: String = serializer.serialize(this)

}

object SerializedException {


  case class SimpleStackTraceElement(val declaringClass: String,
                                     val MethodName: String,
                                     val fileName: String,
                                     val lineNumber: Int) derives ReadWriter

  private def toSimple(el: StackTraceElement): SimpleStackTraceElement = SimpleStackTraceElement(el.getClassName, el.getMethodName, el.getFileName, el.getLineNumber)

  private def serializedStackTrace(error: Throwable): Seq[SimpleStackTraceElement] = error match {
    case serialized: SerializedException => serialized.stackTrace
    case other => other.getStackTrace.toList.map(toSimple)
  }

  def apply(err: Throwable, cause: Throwable): SerializedException =
    SerializedException(err.getMessage, serializedStackTrace(err), Option(cause).map(apply))

  def apply(err: Throwable): SerializedException = err match {
    case serialized: SerializedException => serialized
    case other => SerializedException(other.getMessage, serializedStackTrace(other), Option(other.getCause).map(apply))
  }

  /** The message of `error` followed by the distinct messages of its causes, for saved error statuses. */
  def messageOf(error: Throwable): String =
    Iterator.iterate(Option(error))(_.flatMap(causeOf)).takeWhile(_.isDefined).flatten.take(8)
      .map(e => Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.getClass.getSimpleName)).toList.distinct.mkString(": ")

  private def causeOf(error: Throwable): Option[Throwable] = error match {
    case serialized: SerializedException => serialized.cause
    case other => Option(other.getCause).filterNot(_ eq other)
  }

  def apply(msg: String): SerializedException = apply(Exception(msg))

  def apply(msg: String, cause: Throwable): SerializedException = apply(msg, Option(cause))

  def apply(msg: String, cause: Option[Throwable]): SerializedException =
    apply(msg).copy(cause = cause.map(apply))
}
