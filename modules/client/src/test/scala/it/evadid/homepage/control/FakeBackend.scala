package it.evadid.homepage.control


import com.raquo.laminar.api.L.Var
import it.evadid.distribution.command.{ExecutionCommand, ExecutionCommandFactory, SerializedException}
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.distribution.formats.ExecutionClientResponse.ExecutionClientResponseTimestamps
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.Thenable.Implicits.*
import upickle.default.*

/** Replaces the browser's `fetch` with an in-memory backend. Commands with a registered answer get a regular
  * execution response; any other command gets HTTP 503, as when the backend is unavailable.
  */
final class FakeBackend {
  private given ExecutionContext = ExecutionContext.global
  /** Command names received, with the Authorization header the client sent. */
  val received = Var(Vector.empty[(String, String)])
  private val answers = Var(Map.empty[String, ExecutionCommand => Future[ExecutionClientResponse]])
  private val global = js.Dynamic.global.globalThis
  private val previous = global.fetch

  /** Answers `factory` commands with `answer(request)`; a failed future becomes an error response. */
  def answer[I: ReadWriter, O: ReadWriter](factory: ExecutionCommandFactory[I, O])(answer: I => Future[O]): Unit = {
    def handle(command: ExecutionCommand): Future[ExecutionClientResponse] = {
      val now = Instant.now()
      val stamps = ExecutionClientResponseTimestamps(now, now, now)
      def respond(outcome: scala.util.Try[O]): ExecutionClientResponse = outcome match {
        case scala.util.Success(value) => ExecutionClientResponse.forValue(stamps, value, command)
        case scala.util.Failure(error) => ExecutionClientResponse.forError(stamps, SerializedException(error), command)
      }
      Future(answer(read[I](command.json))).flatten.transform(outcome => scala.util.Success(respond(outcome)))
    }
    answers.update(_.updated(factory.name, handle))
  }

  def requests(name: String): Vector[String] = received.now().filter(_._1 == name).map(_._2)

  private def body(init: js.Dynamic): Future[String] = {
    val headers = init.headers.asInstanceOf[js.Dictionary[String]]
    if headers.get("Content-Encoding").contains("gzip") then {
      val stream = js.Dynamic.newInstance(js.Dynamic.global.Blob)(js.Array(init.body)).stream()
        .pipeThrough(js.Dynamic.newInstance(js.Dynamic.global.DecompressionStream)("gzip"))
      js.Dynamic.newInstance(js.Dynamic.global.Response)(stream).text().asInstanceOf[js.Promise[String]].toFuture
    } else Future.successful(init.body.asInstanceOf[String])
  }

  private def respond(text: String, status: Int): js.Dynamic =
    js.Dynamic.newInstance(js.Dynamic.global.Response)(text, js.Dynamic.literal(status = status))

  private val fetch: js.Function2[String, js.Dynamic, js.Promise[js.Dynamic]] = (_, init) => body(init).flatMap { text =>
    val command = read[ExecutionCommand](text)
    received.update(_ :+ (command.name -> init.headers.asInstanceOf[js.Dictionary[String]].getOrElse("Authorization", "")))
    answers.now().get(command.name) match {
      case Some(handler) => handler(command).map(response => respond(write(response), 200))
      case None => Future.successful(respond(ujson.write(ujson.Obj("error" -> s"no fake answer for ${command.name}")), 503))
    }
  }.toJSPromise

  /** Starts answering requests, with no registered answers or recorded requests yet. */
  def install(): Unit = {
    answers.set(Map.empty)
    received.set(Vector.empty)
    global.updateDynamic("fetch")(fetch)
  }
  def uninstall(): Unit = global.updateDynamic("fetch")(previous)
}
