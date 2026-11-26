package it.evadid.homepage.webElements.editor.code.worker

import com.raquo.airstream.state.Var
import it.evadid.distribution.command.ExecutionCommandFactory

import scala.collection.immutable.VectorMap
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Results of one worker command per key: identical requests share one Future, the latest `capacity` keys are
  * remembered, and a failed request is forgotten so that the next one retries.
  */
final class SharedWorkerCache[K, Req, Res](
    command: ExecutionCommandFactory[Req, Res],
    request: K => Req,
    worker: SharedWorker,
    capacity: Int = 32
) {
  private val entries = Var(VectorMap.empty[K, Future[Res]])

  def apply(key: K): Future[Res] = entries.now().getOrElse(key, {
    val result = Promise[Res]()
    entries.update(current => (current + (key -> result.future)).takeRight(capacity))
    worker.send(command, request(key)).onComplete { outcome =>
      // Forget a failure before anyone sees it, so that their retry sends a new request.
      if outcome.isFailure then
        entries.update(current => if current.get(key).contains(result.future) then current - key else current)
      result.complete(outcome)
    }
    result.future
  })
}
