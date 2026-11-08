package it.evadid.workbook.abstractions.scaffolding

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.distribution.command.SerializedException
import upickle.default.*

sealed trait ScaffoldingStatus derives ReadWriter {

  def isError: Boolean = false
  def getInfoString: Either[LanguageMap[HumanLanguage], LanguageMapContentId]
}

object ScaffoldingStatus {

  case class ScaffoldingFeedback(feedback: LanguageMap[HumanLanguage]) extends ScaffoldingStatus derives ReadWriter {
    def getInfoString: Either[LanguageMap[HumanLanguage], LanguageMapContentId] = Left(feedback)
  }

  case class ScaffoldingError(err: SerializedException) extends ScaffoldingStatus derives ReadWriter {
    override def isError: Boolean = true

    def getInfoString: Either[LanguageMap[HumanLanguage], LanguageMapContentId] = Left(LanguageMap.universalMap("Error: " + err.msg))
  }

  case class ScaffoldingRequestedButNotFinished() extends ScaffoldingStatus derives ReadWriter {

    def getInfoString: Either[LanguageMap[HumanLanguage], LanguageMapContentId] = Right(LanguageMapContentId("basic/scaffoldingLoading"))
  }
}