package todomove.`export`.traits

import com.raquo.airstream.state.Var
import org.scalajs.dom.OffscreenCanvas

import java.time.LocalDateTime
import scala.collection.mutable
import scala.concurrent.Promise
import scala.scalajs.js.timers.SetTimeoutHandle

object WorkerTraits {

  enum WorkerState {
    case WORKER_STARTING
    case WORKER_READY(initRequested: Boolean, initSuccess: Boolean)
    case WORKER_TERMINATED
  }

  case class CommandState(id: String) {
    private var info: mutable.Map[String, String] = mutable.Map(
      "timestampReceived" -> LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_DATE_TIME),
    )
  }

  case class WorkerCommand(name: String, params: Map[String, String], canvas: Option[OffscreenCanvas] = None) {

  }

  case class ExecutionResult(
                              history: CommandHistory,
                              data: Map[String, String],
                              error: Option[Throwable],
                              stdOut: String,
                              stdErr: String)

  case class CommandHistory(
                             command: WorkerCommand,
                             timestampRequested: LocalDateTime,
                             timestampReceived: LocalDateTime,
                             timestampStarted: LocalDateTime,
                             timestampFinished: LocalDateTime,
                           )

  case class PendingTask(
                          requestId: String,
                          command: WorkerCommand,
                          promise: Promise[ExecutionResult],
                          timestampRequested: LocalDateTime
                        )
}