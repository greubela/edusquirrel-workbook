package it.evadid.server.events.mail

import it.evadid.core.datastructures.file.LoadedFile
import it.evadid.distribution.commandTypes.MailCommands.{SendMailRequest, SendMailResponse}
import it.evadid.server.{BlockingExecution, SafeFailure, ServerConfig}
import it.evadid.util.logging.Logger
import jakarta.mail.*
import jakarta.mail.internet.{AddressException, ContentType, InternetAddress, MimeBodyPart, MimeMessage, MimeMultipart}
import jakarta.mail.util.ByteArrayDataSource
import jakarta.activation.DataHandler

import java.util.Properties
import java.net.{ConnectException, SocketTimeoutException, URLConnection, UnknownHostException}
import javax.net.ssl.SSLException
import scala.concurrent.Future
import scala.util.Try

/** Delivers mail over authenticated STARTTLS SMTP; `config` is absent when the server has no mail account. */
final class MailSender(config: Option[ServerConfig.Mail]) {

  def send(request: SendMailRequest, logger: Logger): SendMailResponse = {
    val mail = config.getOrElse(throw IllegalStateException(
      "Could not send email: mail delivery is not configured; set MAIL_EMAIL and MAIL_PW."))
    logger.logInfo(s"sending mail with ${request.attachments.size} attachment(s) to '${request.recipientMail}'")
    MailSender.withDeliveryContext {
      Transport.send(MailSender.mailMessage(MailSender.session(mail), mail.sender, request))
      logger.logInfo(s"mail sent to '${request.recipientMail}'")
      SendMailResponse(None, sent = true)
    }
  }

  def handle(request: SendMailRequest, logger: Logger): Future[SendMailResponse] = BlockingExecution(send(request, logger))
}

object MailSender {
  private def session(mail: ServerConfig.Mail): Session = {
    val props = new Properties()
    props.put("mail.smtp.auth", "true")
    props.put("mail.smtp.starttls.enable", "true")
    props.put("mail.smtp.starttls.required", "true")
    props.put("mail.smtp.host", mail.host)
    props.put("mail.smtp.port", mail.port)
    props.put("mail.smtp.connectiontimeout", "10000")
    props.put("mail.smtp.timeout", "15000")
    props.put("mail.smtp.writetimeout", "15000")
    Session.getInstance(props, new Authenticator {
      override def getPasswordAuthentication: PasswordAuthentication = PasswordAuthentication(mail.sender, mail.password)
    })
  }

  /** UTF-8 message: plain text without attachments, otherwise the text body followed by one part per attachment. */
  private[server] def mailMessage(session: Session, sender: String, request: SendMailRequest): MimeMessage = {
    val message = MimeMessage(session)
    message.setFrom(InternetAddress(sender))
    message.setRecipients(Message.RecipientType.TO, request.recipientMail)
    message.setSubject(request.subject, "UTF-8")
    if request.attachments.isEmpty then message.setText(request.content, "UTF-8")
    else message.setContent(multipartContent(request))
    message
  }

  private def multipartContent(request: SendMailRequest): MimeMultipart = {
    val textPart = MimeBodyPart()
    textPart.setText(request.content, "UTF-8")
    val attachmentParts = request.attachments.zipWithIndex.map((file, index) => attachmentPart(file, index + 1))
    MimeMultipart((textPart +: attachmentParts)*)
  }

  /** Names are validated by [[SendMailRequest]]; client metadata is used as given or rejected, never altered. */
  private def attachmentPart(file: LoadedFile, position: Int): MimeBodyPart = {
    val part = MimeBodyPart()
    part.setDataHandler(DataHandler(ByteArrayDataSource(file.data, attachmentContentType(file, position))))
    part.setFileName(file.description.filenameWithExtension)
    part
  }

  /** The declared media type must be valid; without one, the type follows from the file name (RFC 2046 binary data
   * when the name does not identify a type). */
  private def attachmentContentType(file: LoadedFile, position: Int): String =
    file.description.mediaType match {
      case Some(declared) => Try(ContentType(declared).toString).toOption.flatMap(Option(_)).getOrElse(
        throw IllegalArgumentException(s"Could not send email: attachment $position declares an invalid media type."))
      case None => Option(URLConnection.guessContentTypeFromName(file.description.filenameWithExtension)).getOrElse("application/octet-stream")
    }

  /** SMTP errors can contain recipients or message contents; only safe diagnostics cross the command boundary. */
  private[server] def withDeliveryContext[A](send: => A): A = SafeFailure.guard {
    case error: MessagingException =>
      val chain = causes(error, 8)
      def caused[T <: Throwable](kind: Class[T]): Boolean = chain.exists(kind.isInstance)
      val reason =
        if caused(classOf[AuthenticationFailedException]) then "The SMTP server rejected authentication; check MAIL_EMAIL and MAIL_PW."
        else if caused(classOf[SocketTimeoutException]) then "The SMTP connection timed out; check mail-server availability and outbound SMTP access."
        else if caused(classOf[UnknownHostException]) then "The SMTP hostname could not be resolved; check MAIL_SMTP_HOST and server DNS."
        else if caused(classOf[ConnectException]) then "The SMTP connection failed; check MAIL_SMTP_HOST, MAIL_SMTP_PORT and outbound SMTP access."
        else if caused(classOf[SSLException]) then "SMTP TLS negotiation failed; check the mail-server certificate and STARTTLS support."
        else if error.isInstanceOf[AddressException] then "The sender or recipient email address is invalid."
        else if error.isInstanceOf[SendFailedException] then "The SMTP server rejected delivery; check sender/recipient addresses and mail-server policy."
        else "The SMTP exchange failed; check mail-server configuration and STARTTLS support."
      s"Could not send email: $reason (mail error: ${error.getClass.getSimpleName})"
  }(send)

  /** The exception followed by up to `remaining` nested or chained causes. */
  private def causes(current: Throwable, remaining: Int): Seq[Throwable] =
    current +: (if remaining == 0 then Nil else {
      val next = current match {
        case mail: MessagingException => Option(mail.getNextException).orElse(Option(mail.getCause))
        case other => Option(other.getCause)
      }
      next.filterNot(_ eq current).toList.flatMap(causes(_, remaining - 1))
    })
}
