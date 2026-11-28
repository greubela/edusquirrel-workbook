package it.evadid.server.events.submission

import it.evadid.core.datastructures.file.{CopyrightInfo, LoadedFile, NamedDataFileDescription}
import it.evadid.core.util.InfoUtil
import it.evadid.distribution.commandTypes.MailCommands.{SendMailRequest, SendMailResponse}
import it.evadid.distribution.commandTypes.SubmissionCommands.{SubmitSessionEvent, SubmitSessionResponse}
import it.evadid.server.BlockingExecution
import it.evadid.server.account.AccountRows
import it.evadid.server.events.mail.MailSender
import it.evadid.util.logging.Logger
import it.evadid.workbook.session.SessionData
import jakarta.mail.internet.InternetAddress
import upickle.default.write

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.{Deflater, ZipEntry, ZipOutputStream}
import java.time.{Clock, Duration}
import scala.concurrent.Future
import scala.util.{Try, Using}

/** Handles a submitted session: the full copy goes to `archiveRecipient`, the summary to the learner's own address.
  * The endpoint needs no account, so [[SessionSubmission.submissionLimit]] bounds submissions per address.
  */
final class SessionSubmission(archiveRecipient: String, mail: MailSender,
                              limit: RateLimit[SessionSubmission.Sender] = SessionSubmission.submissionLimit()) {
  import SessionSubmission.*

  /** The archive copy must be delivered; a failed confirmation is reported in the response and logged. */
  private[server] def submit(event: SubmitSessionEvent, userIp: InetAddress, receivedAt: LocalDateTime, logger: Logger,
                             send: SendMailRequest => SendMailResponse): SubmitSessionResponse = {
    val session = event.sessionData.withoutCredentials
    require(limit.tryAcquire(Set(Sender.Mail(AccountRows.normalizedMail(session.userInfo.mail)), Sender.Address(userIp.getHostAddress))),
      "Too many submissions: each email address may submit once per minute. Please wait a moment and submit again.")
    val summary = SimpleSubmission.from(session, userIp, logger)
    require(send(archiveMail(archiveRecipient, session, summary, receivedAt)).sent, "The submission could not be delivered to the archive.")
    val confirmed = singleRecipient(session.userInfo.mail) match {
      case None =>
        logger.logWarn("The submission names no valid single email address; no confirmation was sent.")
        false
      case Some(recipient) => Try(send(confirmationMail(recipient, summary, receivedAt)).sent).fold(error => {
        logger.logExceptionWarn("The submission confirmation could not be sent", error)
        false
      }, identity)
    }
    SubmitSessionResponse(summary.submittedInteractions.size, confirmed)
  }

  def handle(event: SubmitSessionEvent, userIp: InetAddress, receivedAt: LocalDateTime, logger: Logger): Future[SubmitSessionResponse] =
    BlockingExecution(submit(event, userIp, receivedAt, logger, mail.send(_, logger)))
}

object SessionSubmission {
  /** Who submits: the claimed mail address and the address the request came from. */
  enum Sender {
    case Mail(address: String)
    case Address(ip: String)
  }

  /** One submission per mail address and minute. A school network often reaches the server through one address,
    * so a request address may submit for a whole class in that minute.
    */
  def submissionLimit(clock: Clock = Clock.systemUTC): RateLimit[Sender] = RateLimit(Duration.ofMinutes(1), {
    case Sender.Mail(_) => 1
    case Sender.Address(_) => 40
  }, clock)

  private val mailTimeFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

  /** One plain address only: the recipient is client-supplied on an endpoint that needs no account. */
  private[server] def singleRecipient(mail: String): Option[String] =
    Try(InternetAddress(mail.trim, true)).toOption.filter(_.getAddress == mail.trim).map(_.getAddress)

  private val fileDateFormat = DateTimeFormatter.ofPattern("yyyy_MM_dd")

  /** Name shared by a submission's files, e.g. `2026_11_28_WorkbookSubmitted_Erika_Musterfrau`. */
  private[server] def attachmentBaseName(summary: SimpleSubmission, receivedAt: LocalDateTime): String =
    s"${receivedAt.format(fileDateFormat)}_WorkbookSubmitted_${InfoUtil.fileNameSafe(summary.submittingUser.userName)}"

  /** `fileName` deflated into `<fileName>.zip`: sessions are mostly JSON and shrink to a fraction, keeping mails small. */
  private[server] def zipped(fileName: String, content: String): LoadedFile = {
    val buffer = ByteArrayOutputStream()
    Using.resource(ZipOutputStream(buffer)) { zip =>
      zip.setLevel(Deflater.BEST_COMPRESSION)
      zip.putNextEntry(ZipEntry(fileName))
      zip.write(content.getBytes(StandardCharsets.UTF_8))
      zip.closeEntry()
    }
    val bytes = buffer.toByteArray
    LoadedFile(NamedDataFileDescription(fileName + ".zip", bytes, CopyrightInfo.unknownCopyrightInfo, Some("application/zip")), bytes)
  }

  private def summaryAttachment(baseName: String, summary: SimpleSubmission): LoadedFile =
    zipped(baseName + "-summary.json", write(summary, indent = 2))

  private[server] def confirmationMail(recipient: String, summary: SimpleSubmission, receivedAt: LocalDateTime): SendMailRequest = {
    val workbook = summary.workbook.referencedId
    val received = summary.submittedAt.format(mailTimeFormat)
    val count = summary.submittedInteractions.size
    SendMailRequest(recipient, s"EduSquirrel: Abgabe „$workbook“ eingegangen / submission received",
      s"""Guten Tag ${summary.submittingUser.userName},
         |
         |Ihre Abgabe zum Arbeitsheft „$workbook“ (gespeichert $received) ist eingegangen.
         |Im Anhang (ZIP) finden Sie eine Zusammenfassung Ihrer $count bearbeiteten Aufgaben.
         |
         |Your submission for the workbook "$workbook" (saved $received) has been received.
         |The attached ZIP file summarizes your $count answered tasks.
         |""".stripMargin, List(summaryAttachment(attachmentBaseName(summary, receivedAt), summary)))
  }

  private[server] def archiveMail(archiveRecipient: String, session: SessionData, summary: SimpleSubmission, receivedAt: LocalDateTime): SendMailRequest = {
    val user = summary.submittingUser
    val baseName = attachmentBaseName(summary, receivedAt)
    SendMailRequest(archiveRecipient, s"EduSquirrel-Abgabe: ${summary.workbook.referencedId} – ${user.userName}",
      s"""Abgabe zum Arbeitsheft „${summary.workbook.referencedId}“, eingegangen ${receivedAt.format(mailTimeFormat)}.
         |Angegeben: ${user.userName} <${user.userMail}> (Konto ${session.userInfo.id}), IP ${user.userIp.getHostAddress}.
         |Bearbeitete Aufgaben: ${summary.submittedInteractions.size}.
         |
         |Anhänge (ZIP): vollständige Sitzungsdaten ($baseName.eva, entpackt über „Sitzung fortsetzen“ ladbar) und die Zusammenfassung.
         |""".stripMargin,
      List(zipped(baseName + ".eva", write(session)), summaryAttachment(baseName, summary)))
  }
}
