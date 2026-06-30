package it.evadid.core.datastructures.language

import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage}
import it.evadid.core.datastructures.state.observable.ObservableValue

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait LanguageMapIdResolver(private val currentLanguage: ObservableValue[HumanLanguage]) {

  private given ExecutionContext = ExecutionContext.global

  def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]]

  def resolveToString(id: LanguageMapContentId): Future[String] = {
    resolveMap(id).zip(currentLanguage.currentValueOrWaitForUpdate).map(tup => {
      if (tup._1.availableLanguages.contains(tup._2)) tup._1.getInLanguage(tup._2)
      else (tup._1.getInLanguage(English))
    })
  }

  def languageFuture(): Future[HumanLanguage] = currentLanguage.currentValueOrWaitForUpdate

  def resolveAll(ids: Seq[LanguageMapContentId]): (Future[(Map[LanguageMapContentId, String], HumanLanguage)]) = {
    resolveToStrings(ids).flatMap(resMap => languageFuture().map(lang => (resMap, lang)))
  }

  def resolveToStrings(ids: Seq[LanguageMapContentId]): Future[Map[LanguageMapContentId, String]] = {
    val resMapOp: Future[Seq[(LanguageMapContentId, Option[String])]] = Future.traverse(ids)(id => resolveToString(id).transform {
      case Success(str) => Success(Some(str))
      case Failure(err) =>
        println("[UGLY ERROR AT LanguageMapIdReserver]: " + err.getMessage)
        Success(None)
    }.map(id -> _))

    resMapOp.map(_.filter(_._2.nonEmpty).map(tup => tup._1 -> tup._2.get)).map(_.toMap)

  }

}
