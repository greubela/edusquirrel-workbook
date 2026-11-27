package it.evadid.server

import it.evadid.core.datastructures.file.{CopyrightInfo, LoadedFile, NamedDataFileDescription}
import it.evadid.distribution.commandTypes.MailCommands.{SendMailRequest, SendMailResponse}
import it.evadid.distribution.commandTypes.SubmissionCommands.{SubmitSessionEvent, SubmitSessionResponse}
import it.evadid.util.JvmUtils
import it.evadid.util.logging.Logger
import it.evadid.workbook.session.SessionData
import jakarta.mail.internet.InternetAddress
import upickle.default.write

import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/** Handles a submitted session: the full copy goes to the archive address, the summary to the learner's own address. */
object SubmitSessionCommand {

  private[server] val archiveRecipient: String = "andre.greubel@hu-berlin.de"

  private val mailTimeFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

  /** One plain address only: the recipient is client-supplied on an endpoint that needs no account. */
  private[server] def singleRecipient(mail: String): Option[String] =
    Try(InternetAddress(mail.trim, true)).toOption.filter(_.getAddress == mail.trim).map(_.getAddress)

  private def jsonAttachment(fileName: String, json: String): LoadedFile = {
    val bytes = json.getBytes(StandardCharsets.UTF_8)
    LoadedFile(NamedDataFileDescription(fileName, bytes, CopyrightInfo.unknownCopyrightInfo, Some("application/json")), bytes)
  }

  private def summaryAttachment(session: SessionData, summary: SimpleSubmission): LoadedFile =
    jsonAttachment(session.fileName.stripSuffix(".eva") + "-summary.json", write(summary, indent = 2))

  private[server] def confirmationMail(recipient: String, session: SessionData, summary: SimpleSubmission): SendMailRequest = {
    val workbook = summary.workbook.referencedId
    val received = summary.submittedAt.format(mailTimeFormat)
    val count = summary.submittedInteractions.size
    SendMailRequest(recipient, s"EduSquirrel: Abgabe „$workbook“ eingegangen / submission received",
      s"""Guten Tag ${summary.submittingUser.userName},
         |
         |Ihre Abgabe zum Arbeitsheft „$workbook“ (gespeichert $received) ist eingegangen.
         |Im Anhang finden Sie eine Zusammenfassung Ihrer $count bearbeiteten Aufgaben.
         |
         |Your submission for the workbook "$workbook" (saved $received) has been received.
         |The attachment summarizes your $count answered tasks.
         |""".stripMargin, List(summaryAttachment(session, summary)))
  }

  private[server] def archiveMail(session: SessionData, summary: SimpleSubmission, receivedAt: LocalDateTime): SendMailRequest = {
    val user = summary.submittingUser
    SendMailRequest(archiveRecipient, s"EduSquirrel-Abgabe: ${summary.workbook.referencedId} – ${user.userName}",
      s"""Abgabe zum Arbeitsheft „${summary.workbook.referencedId}“, eingegangen ${receivedAt.format(mailTimeFormat)}.
         |Angegeben: ${user.userName} <${user.userMail}> (Konto ${session.userInfo.id}), IP ${user.userIp.getHostAddress}.
         |Bearbeitete Aufgaben: ${summary.submittedInteractions.size}.
         |
         |Anhänge: vollständige Sitzungsdaten (${session.fileName}, über „Sitzung fortsetzen“ ladbar) und die Zusammenfassung.
         |""".stripMargin,
      List(jsonAttachment(session.fileName, write(session)), summaryAttachment(session, summary)))
  }

  /** The archive copy must be delivered; a failed confirmation is reported in the response and logged. */
  private[server] def submit(event: SubmitSessionEvent, userIp: InetAddress, receivedAt: LocalDateTime, logger: Logger,
                             send: SendMailRequest => SendMailResponse): SubmitSessionResponse = {
    val session = event.sessionData.withoutCredentials
    val summary = SimpleSubmission.from(session, userIp, logger)
    require(send(archiveMail(session, summary, receivedAt)).sent, "The submission could not be delivered to the archive.")
    val confirmed = singleRecipient(session.userInfo.mail) match {
      case None =>
        logger.logWarn("The submission names no valid single email address; no confirmation was sent.")
        false
      case Some(recipient) => Try(send(confirmationMail(recipient, session, summary)).sent).fold(error => {
        logger.logExceptionWarn("The submission confirmation could not be sent", error)
        false
      }, identity)
    }
    SubmitSessionResponse(summary.submittedInteractions.size, confirmed)
  }

  def handle(event: SubmitSessionEvent, userIp: InetAddress, receivedAt: LocalDateTime, logger: Logger): Future[SubmitSessionResponse] =
    Future(submit(event, userIp, receivedAt, logger, SendMailCommand.sendMail(_, logger, JvmUtils.env)))(using ExecutionContext.global)
}
