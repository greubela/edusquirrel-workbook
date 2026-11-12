package it.evadid.core.datastructures.language.control

import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.state.observable.ObservableValue

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

trait LanguageMapIdResolver(private val currentLanguage: ObservableValue[HumanLanguage]) {

  private given ExecutionContext = ExecutionContext.global

  def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]]

  def resolveToString(id: LanguageMapContentId): Future[String] = {
    resolveMap(id).zip(currentLanguage.currentValueOrWaitForUpdate).map { (translations, language) =>
      val selected = if translations.availableLanguages.contains(language) then language else English
      translations.getInLanguage(selected)
    }
  }

  def languageFuture(): Future[HumanLanguage] = currentLanguage.currentValueOrWaitForUpdate

  def resolveAll(ids: Seq[LanguageMapContentId]): (Future[(Map[LanguageMapContentId, String], HumanLanguage)]) = {
    resolveToStrings(ids).flatMap(resMap => languageFuture().map(lang => (resMap, lang)))
  }

  private def resolveEntry(id: LanguageMapContentId): Future[Option[(LanguageMapContentId, String)]] =
    Try(resolveToString(id)).fold(Future.failed, identity).map(text => Some(id -> text)).recover {
      case NonFatal(error) =>
        println(s"Could not resolve language map $id: ${error.getMessage}")
        None
    }

  def resolveToStrings(ids: Seq[LanguageMapContentId]): Future[Map[LanguageMapContentId, String]] =
    Future.traverse(ids)(resolveEntry).map(_.flatten.toMap)
}
