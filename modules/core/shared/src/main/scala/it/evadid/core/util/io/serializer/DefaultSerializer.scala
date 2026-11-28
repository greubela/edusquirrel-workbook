package it.evadid.core.util.io.serializer

import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.*
import upickle.ReadWriter
import upickle.default.*

import java.time.Instant
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

object DefaultSerializer {

  private val instantFormat = new DateTimeFormatterBuilder().appendInstant(9).toFormatter(Locale.ROOT)

  /** The one text form of a point in time: ISO 8601 in UTC with exactly nine fractional digits,
    * e.g. `2026-11-28T12:00:00.000000000Z`. Four-digit years sort chronologically as text.
    */
  def instantText(instant: Instant): String = instantFormat.format(instant)

  /** The one codec of points in time, written as [[instantText]]. */
  given instantReadWriter: ReadWriter[Instant] =
    upickle.default.readwriter[String].bimap[Instant](instantText, Instant.parse)

  lazy val serializerExceptionS: Serializer[SerializedException] = Serializer.fromUpickleJson(using SerializedException.derived$ReadWriter)
  /** Any error as its saved [[SerializedException]] (messages only). */
  lazy val serializerException: Serializer[Throwable] = serializerExceptionS.map[Throwable](identity, SerializedException(_))

  val serializerJsonStringMap: Serializer[Map[String, String]] = Serializer.fromUpickleJson[Map[String, String]]

}
