package `export`.traits

import com.raquo.airstream.state.Var

import java.time.LocalDateTime
import scala.collection.mutable
import scala.concurrent.Promise
import scala.scalajs.js.timers.SetTimeoutHandle

object WorkerTraits {
    
  case class CommandState(
                           id: String
                         ) {
    private var info: mutable.Map[String, String] = mutable.Map(
      "timestampEnqueued" -> LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_DATE_TIME),
    )
  }

  case class WorkerCommand(name: String, params: Map[String, String], timestampRequested: LocalDateTime) {

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
                             timestampEnqueued: LocalDateTime,
                             timestampStarted: LocalDateTime,
                             timestampFinished: LocalDateTime,
                           )

  case class PendingTask(
                          requestId: String,
                          promise: Promise[ExecutionResult],
                          timestampRequested: LocalDateTime
                        )
}