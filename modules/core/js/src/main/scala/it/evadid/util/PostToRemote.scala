package it.evadid.util

import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import org.scalajs.dom
import org.scalajs.dom.{HttpMethod, RequestInit, Response}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

object PostToRemote {
  val instance = PostToRemote(Logger.withNameAndPrefixes(Some("PostToExternalSingleton"), PrintToStdLogger.printWarnAndError))
}

case class PostToRemote(logger: Logger) {

  def postTo(url: String, jsonPayload: String): Future[String] = {
    val init = new RequestInit {
      method = HttpMethod.POST
      headers = js.Dictionary(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json"
      )
      body = jsonPayload
    }

    val promise: Future[Response] = dom.fetch(url, init).toFuture
    promise.flatMap(_.text().toFuture)(using ExecutionContext.global)
  }
}
