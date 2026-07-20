package it.evadid.core.datastructures.language.serialization

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, SpecialLanguage}
import it.evadid.core.datastructures.language.serialization.abstractions.{LanguageMapEntry, ParsedTriples}
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.util.logging.Logger

import scala.concurrent.{ExecutionContext, Future}

trait LanguageMapInputSourceAtomar[T <: AppLanguage] {

  def associatedLanguageMapName: String

  def associatedLanguage: T



  def loadKeyValuePairs(logger: Logger): Future[Map[String, String]]


}
