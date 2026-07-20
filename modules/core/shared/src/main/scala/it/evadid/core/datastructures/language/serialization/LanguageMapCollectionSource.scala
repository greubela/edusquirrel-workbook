package it.evadid.core.datastructures.language.serialization

import it.evadid.core.datastructures.language.serialization.abstractions.ParsedTriples
import it.evadid.util.logging.Logger

import scala.concurrent.{ExecutionContext, Future}

case class LanguageMapCollectionSource(inputSources: Set[LanguageMapInputSource], ec: ExecutionContext) extends LanguageMapInputSource {

  given ExecutionContext = ec

  def loadTriples(logger: Logger, source: LanguageMapInputSource): Future[ParsedTriples] = {
    source.loadAllTriples(logger).recover {
      case (e: Exception) => {
        logger.logExceptionWarn(s"ignoring triples of source ${source} because of error", e)
        ParsedTriples(Set(), Set())
      }
    }
  }

  override def loadAllTriples(logger: Logger): Future[ParsedTriples] = {
    Future.traverse(inputSources)(source => loadTriples(logger, source))
      .map(tripleSet => ParsedTriples(tripleSet.flatMap(_.regularTriples), tripleSet.flatMap(_.universalTriples)))
  }
}
