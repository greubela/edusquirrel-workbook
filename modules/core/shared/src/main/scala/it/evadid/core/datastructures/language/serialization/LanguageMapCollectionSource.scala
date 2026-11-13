package it.evadid.core.datastructures.language.serialization

import it.evadid.core.datastructures.language.serialization.abstractions.ParsedTriples
import it.evadid.util.logging.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

case class LanguageMapCollectionSource(inputSources: Set[LanguageMapInputSource], ec: ExecutionContext) extends LanguageMapInputSource {

  given ExecutionContext = ec

  private def emptyTriples: ParsedTriples = ParsedTriples(Set.empty, Set.empty)

  def loadTriples(logger: Logger, source: LanguageMapInputSource): Future[ParsedTriples] =
    if inputSources.isEmpty then Future.successful(emptyTriples)
    else Try(source.loadAllTriples(logger)).fold(Future.failed, identity).recover {
      case NonFatal(error) =>
        logger.logExceptionWarn(s"Ignoring triples of source $source because of error", error)
        emptyTriples
    }

  override def loadAllTriples(logger: Logger): Future[ParsedTriples] = {
    if inputSources.isEmpty then {
      logger.logWarn("Ignoring input source collection (collection is empty)")
      Future.successful(emptyTriples)
    } else {
      logger.logInfo(s"Fetching language maps from ${inputSources.size} sources")
      Future.traverse(inputSources)(source => loadTriples(logger, source)).map { sources =>
        ParsedTriples(sources.flatMap(_.regularTriples), sources.flatMap(_.universalTriples))
      }
    }
  }
}
