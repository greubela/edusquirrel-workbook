package it.evadid.server

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.grading.GradingStatus
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.sync.model.InteractionHistoryJson
import it.evadid.workbook.serialization.WorkbookElementReference
import it.evadid.workbook.reports.GradingSummary
import it.evadid.workbook.session.SessionData
import upickle.default.*

import java.net.InetAddress
import java.time.Instant
import scala.util.Try

/** The learner's latest answer of one interaction; `gradingStatus` is empty for interactions without graders. */
case class SubmittedInteractionState(interaction: WorkbookElementReference, format: InteractionFormat,
                                     lastStateSerialized: String, gradingStatus: Option[GradingStatus]) derives ReadWriter

/** The identity claimed in the submitted session (unverified) and the address it was received from. */
case class SubmittingUser(userName: String, userMail: String, userIp: InetAddress)

object SubmittingUser {
  /** Addresses are written as literals, without name lookups; reading accepts the same literals. */
  private given ReadWriter[InetAddress] = readwriter[String].bimap(_.getHostAddress, InetAddress.getByName)
  given ReadWriter[SubmittingUser] = macroRW
}

/** Abbreviated submission sent to the learner: one entry per answered interaction, in workbook order. */
case class SimpleSubmission(submittedAt: Instant, workbook: WorkbookElementReference, submittingUser: SubmittingUser,
                            submittedInteractions: Seq[SubmittedInteractionState]) derives ReadWriter

object SimpleSubmission {

  /** Only histories of the session's own workbook count; an unreadable or mismatched history is logged and left out. */
  def from(session: SessionData, userIp: InetAddress, logger: Logger): SimpleSubmission = {
    val workbook = session.workbookInfo.loadedWorkbook
    val histories = session.knownUsageData.collect {
      case (context, history) if context.scenarioId == workbook.workbookId => context.keyForSerialisation -> history
    }
    val submitted = workbook.allInteractionsInThisInstance.distinctBy(_.elementId).flatMap { interaction =>
      histories.get(interaction.keyForSerialization).flatMap(stored =>
        Try(latestAnswer(interaction, stored)).fold(error => {
          logger.logExceptionWarn(s"Left the unreadable history of ${interaction.elementId} out of the submission summary", error)
          None
        }, identity))
    }
    SimpleSubmission(session.timestampStorage, workbook.asRef,
      SubmittingUser(session.userInfo.name, session.userInfo.mail, userIp), submitted)
  }

  /** The latest answer, with the combined status of the interaction's authored graders for exactly that answer. */
  private def latestAnswer[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], stored: InteractionHistoryJson): Option[SubmittedInteractionState] = {
    val history = stored.typedAs(interaction.interactionFormat)
    val graders = interaction.gradingElements
    history.latestAnswer.map { latest =>
      SubmittedInteractionState(interaction.asRef, interaction.interactionFormat,
        InteractionFormat.codecsFor(interaction.interactionFormat).stateSerializer.serialize(latest.value),
        Option.when(graders.nonEmpty)(GradingSummary(graders, history.gradingForState(graders, latest.value)).status))
    }
  }
}
