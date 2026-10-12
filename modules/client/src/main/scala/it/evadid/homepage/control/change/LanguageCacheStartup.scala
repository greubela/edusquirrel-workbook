package it.evadid.homepage.control.change

import it.evadid.core.datastructures.language.serialization.abstractions.ParsedTriples
import it.evadid.homepage.control.change.LanguageMapStorageControl.STARTUP_STRATEGY
import it.evadid.homepage.control.change.LanguageMapStorageControl.STARTUP_STRATEGY.*

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

/** Separates startup readiness from the eventual language refresh. The injected
  * scheduler yields a browser task after a cache hit so refresh work cannot delay
  * rendering the cached UI. Cache misses still wait for the first remote load.
  */
private[homepage] object LanguageCacheStartup {
  def run(strategy: STARTUP_STRATEGY,
          readLocal: () => Future[ParsedTriples],
          installLocal: ParsedTriples => Unit,
          refresh: () => Future[?],
          scheduleRefresh: (() => Unit) => Unit,
          reportFailure: Throwable => Unit)(using ExecutionContext): Future[Unit] = {
    val ready = Promise[Unit]()
    if (strategy == CONTINUE_IMMEDIATELY) ready.trySuccess(())

    def refreshRemote(): Unit = {
      Try(refresh()).fold(Future.failed, identity).onComplete {
        case Success(_) => ready.trySuccess(())
        case Failure(error) =>
          reportFailure(error)
          // A later refresh failure must not fail an already successful startup.
          ready.tryFailure(error)
      }
    }

    Try(readLocal()).fold(Future.failed, identity).onComplete {
      case Success(triples) if triples.size > 0 =>
        try {
          installLocal(triples)
          if (strategy == CONTINUE_AFTER_LOCAL_CACHE_SUCCESS) ready.trySuccess(())
          if (strategy == CONTINUE_AFTER_FULL_LOAD) refreshRemote()
          else scheduleRefresh(() => refreshRemote())
        } catch {
          case NonFatal(error) => reportFailure(error); refreshRemote()
        }
      case Success(_) => refreshRemote()
      case Failure(error) => reportFailure(error); refreshRemote()
    }
    ready.future
  }
}
