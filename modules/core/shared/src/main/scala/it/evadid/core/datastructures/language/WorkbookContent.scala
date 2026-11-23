package it.evadid.core.datastructures.language

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.WorkbookContent.*
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}

sealed trait WorkbookContent derives ReadWriter {

  def getInLanguage(resolver: LanguageMapIdResolver)(implicit ec: ExecutionContext): Future[String]

  def merge(other: WorkbookContent): WorkbookContent = {
    if (this == other) this else
      (this, other).match {
        case (LanguageMapBasedContent(map1), LanguageMapBasedContent(map2)) => LanguageMapBasedContent(LanguageMap.merge(map1, map2))
        case (LanguageMapBasedContent(map), _) => this
        case (_, LanguageMapBasedContent(map)) => other
        case (IdBasedContent(id), _) => this
        case (_, IdBasedContent(id)) => other
        case (_, _) => this
      }
  }

}


object WorkbookContent {


  def apply(languageMap: LanguageMap[HumanLanguage]): WorkbookContent = {
    LanguageMapBasedContent(languageMap)
  }

  def apply(str: String): WorkbookContent = {
    println("[UGLY WARN] LanguageMapContent: Hardcoded string!")
    HardcodedContent(str)
  }

  def apply(id: LanguageMapContentId): WorkbookContent = {
    IdBasedContent(id)
  }

  case class LanguageMapBasedContent(languageMap: LanguageMap[HumanLanguage]) extends WorkbookContent derives ReadWriter {
    override def getInLanguage(resolver: LanguageMapIdResolver)(implicit ec: ExecutionContext): Future[String] =
      resolver.languageFuture().map(lang => languageMap.getInLanguage(lang))
  }

  case class HardcodedContent(str: String) extends WorkbookContent derives ReadWriter {
    override def getInLanguage(resolver: LanguageMapIdResolver)(implicit ec: ExecutionContext): Future[String] = Future {
      str
    }
  }

  case class IdBasedContent(id: LanguageMapContentId) extends WorkbookContent derives ReadWriter {
    override def getInLanguage(resolver: LanguageMapIdResolver)(implicit ec: ExecutionContext): Future[String] = {
      resolver.resolveToString(id)
    }
  }

}
