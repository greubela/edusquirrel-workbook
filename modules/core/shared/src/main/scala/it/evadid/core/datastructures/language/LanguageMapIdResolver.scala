package it.evadid.core.datastructures.language

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.state.ObservableValue

import scala.concurrent.{ExecutionContext, Future}

trait LanguageMapIdResolver(val currentLanguage: ObservableValue[HumanLanguage]) {

  def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]]

  def resolveToString(id: LanguageMapContentId): Future[String] = {
    val fut: Future[(LanguageMap[HumanLanguage], HumanLanguage)] =
      resolveMap(id).zip(currentLanguage.currentValueOrWaitForUpdate)
   
    fut.map(tup => tup._1.getInLanguage(tup._2))(using ExecutionContext.global)
  }

  def resolveToStrings(ids: Seq[LanguageMapContentId]): Future[Map[LanguageMapContentId, String]] = {
    given ec: ExecutionContext = ExecutionContext.global

    val futList:
      Future[Seq[(LanguageMapContentId, String)]] =
      Future.traverse(ids)(curId => resolveToString(curId).map(curStr => (curId -> curStr))(using ExecutionContext.global))
    val res = futList.map(list => list.toMap)(using ExecutionContext.global)
    res
  }

}
