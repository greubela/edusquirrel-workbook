package it.evadid.util

object JvmUtils {

  def envOrError(name: String): String = {
    env(name).getOrElse(throw new IllegalStateException(s"$name is not configured in env"))
  }

  def env(name: String): Option[String] =
    Option(System.getenv(name)).map(_.trim).filter(_.nonEmpty)

  def envInt(name: String): Option[Int] =
    env(name).flatMap(_.toIntOption)

}
