package it.evadid.core.datastructures.language.serialization

import it.evadid.core.datastructures.language.serialization.abstractions.ParsedTriples
import it.evadid.util.logging.Logger

import scala.concurrent.{ExecutionContext, Future}

case class LanguageMapCollectionSource(inputSources: Set[LanguageMapInputSource], ec: ExecutionContext) extends LanguageMapInputSource {

  given ExecutionContext = ec

  def loadTriples(logger: Logger, source: LanguageMapInputSource): Future[ParsedTriples] = if(inputSources.nonEmpty){
    source.loadAllTriples(logger).recover {
      case (e: Exception) => {
        logger.logExceptionWarn(s"ignoring triples of source ${source} because of error", e)
        ParsedTriples(Set(), Set())
      }
    }
  }else Future.successful(ParsedTriples(Set(), Set()))

  override def loadAllTriples(logger: Logger): Future[ParsedTriples] = if(inputSources.nonEmpty){
    logger.logInfo(s"Starting Fetching LanguageMapIds in a collection with ${inputSources.size} elements (first: ${inputSources.head})")
    Future.traverse(inputSources)(source => loadTriples(logger, source))
      .map(tripleSet => ParsedTriples(tripleSet.flatMap(_.regularTriples), tripleSet.flatMap(_.universalTriples)))
  }else{
    logger.logWarn("Ignoring Input Source Collection (collection is empty)")
    Future.successful(ParsedTriples(Set(), Set()))
  }
}
