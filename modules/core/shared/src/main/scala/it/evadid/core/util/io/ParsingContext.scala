package it.evadid.core.util.io

import it.evadid.distribution.command.SerializedException
import scala.util.control.NonFatal

/** Adds domain context at decoding boundaries; does not accept older formats. */
object ParsingContext {
  def preview(value: String, limit: Int = 180): String = {
    val text = Option(value).getOrElse("null").replace('\n', ' ').replace('\r', ' ')
    if text.length > limit then text.take(limit) + "…" else text
  }

  def reason(error: Throwable): String = {
    def messages(current: Throwable, remaining: Int): List[String] = {
      val message = Option(current.getMessage).filter(_.nonEmpty).getOrElse(current.getClass.getSimpleName)
      val cause = current match {
        case serialized: SerializedException => serialized.cause
        case other => Option(other.getCause)
      }
      preview(message, 600) :: (if remaining > 0 then cause.filterNot(_ eq current).toList.flatMap(messages(_, remaining - 1)) else Nil)
    }
    messages(error, 4).distinct.mkString(": ")
  }

  def apply[A](name: String, input: => String)(parse: => A): A =
    try parse
    catch case NonFatal(error) =>
      throw IllegalArgumentException(s"Error parsing $name: could not parse '${preview(input)}' because: ${reason(error)}", error)
}
