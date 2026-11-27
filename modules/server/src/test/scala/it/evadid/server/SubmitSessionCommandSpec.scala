package it.evadid.server

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.MailCommands.{SendMailRequest, SendMailResponse}
import it.evadid.distribution.commandTypes.SubmissionCommands.{SubmitSessionEvent, SubmitSessionResponse, submitSessionCommand}
import it.evadid.server.commandHandler.sql.SqlLogCommands
import it.evadid.util.logging.BasicLogger
import it.evadid.workbook.abstractions.InteractionFormat.{ChoiceInteractionFormat, TextInteractionFormat}
import it.evadid.workbook.abstractions.grading.GradingStatus
import it.evadid.workbook.session.SessionData
import jakarta.mail.Session
import jakarta.mail.internet.MimeMultipart
import munit.FunSuite
import upickle.default.*

import java.net.InetAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDateTime
import java.util.Properties

class SubmitSessionCommandSpec extends FunSuite {
  import SubmissionFixtures.*

  private val address = InetAddress.getByName("2001:db8::7")
  private val receivedAt = LocalDateTime.of(2026, 11, 27, 10, 16)

  private def attachmentText(request: SendMailRequest, index: Int): String = String(request.attachments(index).data, UTF_8)

  /** Runs a submission against recorded mail delivery; `deliver` decides each mail's outcome. */
  private def submitted(session: SessionData, deliver: SendMailRequest => SendMailResponse = _ => SendMailResponse(None, sent = true))
      : (SubmitSessionResponse, Seq[SendMailRequest], BasicLogger) = {
    val logger = BasicLogger()
    val sent = Seq.newBuilder[SendMailRequest]
    val response = SubmitSessionCommand.submit(SubmitSessionEvent(session), address, receivedAt, logger,
      request => { sent += request; deliver(request) })
    (response, sent.result(), logger)
  }

  test("the summary lists each answered interaction of the session's workbook, in order, with its latest answer") {
    val summary = SimpleSubmission.from(session(), address, BasicLogger())
    assertEquals(summary.workbook, workbook.asRef)
    assertEquals(summary.submittingUser, SubmittingUser(student.name, student.mail, address))
    assertEquals(summary.submittedInteractions.map(_.interaction), Seq(essay.asRef, quiz.asRef), "unanswered and foreign histories are left out")
    val Seq(text, choice) = summary.submittedInteractions
    assertEquals(text.format, TextInteractionFormat)
    assertEquals(text.lastStateSerialized, write("final answer"))
    assertEquals(text.gradingStatus, None, "a text interaction has no graders")
    assertEquals(choice.format, ChoiceInteractionFormat)
    assertEquals(ChoiceInteractionFormat.stateSerializer.deserialize(choice.lastStateSerialized), quizAnswer)
    assertEquals(choice.gradingStatus, Some(GradingStatus.Correct))
    assertEquals(read[SimpleSubmission](write(summary)), summary)
  }

  test("an unreadable history is reported and left out without hiding the others") {
    val logger = BasicLogger()
    val summary = SimpleSubmission.from(sessionWithUnreadableEssay, address, logger)
    assertEquals(summary.submittedInteractions.map(_.interaction), Seq(quiz.asRef))
    assert(logger.getOut().contains("essay"), logger.getOut())
  }

  test("the archive receives the full credential-free session and the learner receives the summary") {
    val (response, mails, _) = submitted(session())
    assertEquals(response, SubmitSessionResponse(submittedInteractions = 2, confirmationSent = true))
    val Seq(archive, confirmation) = mails
    assertEquals(archive.recipientMail, SubmitSessionCommand.archiveRecipient)
    assertEquals(archive.attachments.map(_.description.filenameWithExtension), Seq(session().fileName,
      session().fileName.stripSuffix(".eva") + "-summary.json"))
    assertEquals(read[SessionData](attachmentText(archive, 0)), session().withoutCredentials)
    assertEquals(confirmation.recipientMail, student.mail)
    assertEquals(confirmation.attachments.size, 1)
    assertEquals(read[SimpleSubmission](attachmentText(confirmation, 0)), SimpleSubmission.from(session(), address, BasicLogger()))
    assert(confirmation.content.contains(student.name) && confirmation.content.contains(workbook.workbookId))
    mails.foreach { mail =>
      val text = mail.content + mail.attachments.indices.map(attachmentText(mail, _)).mkString
      assert(!text.contains("private-"), s"credentials leaked to ${mail.recipientMail}")
    }
  }

  test("both mails compose as UTF-8 MIME messages with JSON attachments") {
    val (_, mails, _) = submitted(session())
    mails.foreach { request =>
      val message = SendMailCommand.mailMessage(Session.getInstance(Properties()), "sender@example.invalid", request)
      message.saveChanges()
      val parts = message.getContent.asInstanceOf[MimeMultipart]
      assertEquals(parts.getCount, request.attachments.size + 1)
      (1 until parts.getCount).foreach(index => assert(parts.getBodyPart(index).getContentType.startsWith("application/json")))
    }
  }

  test("only one plain learner address receives a confirmation; the archive copy is still delivered") {
    Seq("student@example.org, victim@example.org", "Student <student@example.org>", "not an address", "").foreach { mail =>
      val (response, mails, logger) = submitted(session(mail))
      assertEquals(mails.map(_.recipientMail), Seq(SubmitSessionCommand.archiveRecipient), mail)
      assert(!response.confirmationSent)
      assert(logger.getOut().contains("no confirmation"), logger.getOut())
    }
  }

  test("a failed archive delivery fails the submission before any confirmation; a failed confirmation is reported") {
    val failedArchive = intercept[IllegalStateException](submitted(session(), request =>
      if request.recipientMail == SubmitSessionCommand.archiveRecipient then throw IllegalStateException("smtp down")
      else SendMailResponse(None, sent = true)))
    assertEquals(failedArchive.getMessage, "smtp down")
    intercept[IllegalArgumentException](submitted(session(), _ => SendMailResponse(None, sent = false)))
    val (response, mails, logger) = submitted(session(), request =>
      if request.recipientMail == student.mail then throw IllegalStateException("mailbox full") else SendMailResponse(None, sent = true))
    assertEquals(mails.size, 2)
    assertEquals(response, SubmitSessionResponse(2, confirmationSent = false))
    assert(logger.getOut().contains("confirmation"), logger.getOut())
  }

  test("the submission log stores the session without credentials and the diagnostic payload omits its contents") {
    val command = ExecutionCommand(submitSessionCommand.name, writeJs(SubmitSessionEvent(session())))
    val logged = SqlLogCommands.submittedSessionForLog(command)
    assertEquals(read[SessionData](logged), session().withoutCredentials)
    assert(!logged.contains("private-"))
    val diagnostic = SqlLogCommands.payloadForLog(command)
    Seq(student.name, student.mail, "final answer", "private-").foreach(secret => assert(!diagnostic.contains(secret), secret))
    intercept[Exception](SqlLogCommands.submittedSessionForLog(ExecutionCommand(submitSessionCommand.name, ujson.Obj())))
  }

  test("submissions are accepted without an account") {
    val handlers = BackendCommandHandler.handlersWithoutLogin(receivedAt, address)
    assert(handlers.canExecuteCommand(ExecutionCommand(submitSessionCommand.name, writeJs(SubmitSessionEvent(session())))))
    assert(!submitSessionCommand.requiresLogin)
  }
}
