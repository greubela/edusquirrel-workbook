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

  @upickle.implicits.key("ScaffoldingFeedback")
  case class ScaffoldingFeedback(feedback: LanguageMap[HumanLanguage]) extends ScaffoldingStatus derives ReadWriter {
    def getInfoString: Either[LanguageMap[HumanLanguage], LanguageMapContentId] = Left(feedback)
  }

  /** Saved errors keep only the message (with its causes'), never a stack trace. */
  @upickle.implicits.key("ScaffoldingError")
  case class ScaffoldingError(message: String) extends ScaffoldingStatus derives ReadWriter {
    override def isError: Boolean = true

    def getInfoString: Either[LanguageMap[HumanLanguage], LanguageMapContentId] = Left(LanguageMap.universalMap("Error: " + message))
  }

  object ScaffoldingError {
    def apply(error: Throwable): ScaffoldingError = ScaffoldingError(SerializedException.messageOf(error))
  }

  @upickle.implicits.key("ScaffoldingRequestedButNotFinished")
  case class ScaffoldingRequestedButNotFinished() extends ScaffoldingStatus derives ReadWriter {

    def getInfoString: Either[LanguageMap[HumanLanguage], LanguageMapContentId] = Right(LanguageMapContentId("basic/scaffoldingLoading"))
  }
}