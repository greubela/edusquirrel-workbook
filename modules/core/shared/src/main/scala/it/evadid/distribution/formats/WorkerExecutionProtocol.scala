package it.evadid.distribution.formats

import it.evadid.distribution.command.ExecutionCommand
import upickle.default.ReadWriter

/** The worker transports the same response as the HTTP backend, with correlation IDs. */
object WorkerExecutionProtocol {
  case class Request(requestId: String, command: ExecutionCommand) derives ReadWriter
  case class Response(requestId: String, response: ExecutionClientResponse) derives ReadWriter
}
