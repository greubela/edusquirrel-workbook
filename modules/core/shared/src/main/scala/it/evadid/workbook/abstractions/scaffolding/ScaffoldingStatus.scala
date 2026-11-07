package it.evadid.workbook.abstractions.scaffolding

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.distribution.command.SerializedException
import upickle.default.*

sealed trait ScaffoldingStatus derives ReadWriter {

}

object ScaffoldingStatus {

  case class ScaffoldingFeedback(feedback: LanguageMap[HumanLanguage]) extends ScaffoldingStatus derives ReadWriter

  case class ScaffoldingError(err: SerializedException) extends ScaffoldingStatus derives ReadWriter

  case class ScaffoldingRequestedButNotFinished() extends ScaffoldingStatus
}