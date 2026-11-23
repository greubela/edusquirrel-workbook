package it.evadid.server

import it.evadid.distribution.commandTypes.MailCommands.{SendMailRequest, SendMailResponse}
import it.evadid.util.JvmUtils
import it.evadid.util.logging.Logger
import jakarta.mail.*
import jakarta.mail.internet.{AddressException, InternetAddress, MimeMessage}

import java.util.Properties
import java.net.{ConnectException, SocketTimeoutException, UnknownHostException}
import javax.net.ssl.SSLException
import scala.concurrent.{ExecutionContext, Future}

object SendMailCommand {

  private[server] final case class MailConfig(
                                               email: String,
                                               password: String,
                                               host: String,
                                               port: String
                                             ) derives upickle.default.ReadWriter

  private def requiredEnv(name: String, envProvider: String => Option[String]): String =
    envProvider(name).map(_.trim).filter(_.nonEmpty).getOrElse(throw new IllegalStateException(s"required env property $name is not configured"))

  private[server] def readMailConfig(envProvider: String => Option[String]): MailConfig =
    MailConfig(
      email = requiredEnv("MAIL_EMAIL", envProvider),
      password = requiredEnv("MAIL_PW", envProvider),
      host = envProvider("MAIL_SMTP_HOST").map(_.trim).filter(_.nonEmpty).getOrElse("smtp.lima-city.de"),
      port = envProvider("MAIL_SMTP_PORT").map(_.trim).filter(_.nonEmpty).getOrElse("587")
    )

  private[server] def sendMail(request: SendMailRequest, logger: Logger, envProvider: String => Option[String]): SendMailResponse = {
    val config = readMailConfig(envProvider)
    logger.logInfo(s"sending mail to '${request.recipientMail}'")// with subject '${request.subject}' via ${config.host}:${config.port}")

    withDeliveryContext {
      val props = new Properties()
      props.put("mail.smtp.auth", "true")
      props.put("mail.smtp.starttls.enable", "true")
      props.put("mail.smtp.starttls.required", "true")
      props.put("mail.smtp.host", config.host)
      props.put("mail.smtp.port", config.port)
      props.put("mail.smtp.connectiontimeout", "10000")
      props.put("mail.smtp.timeout", "15000")
      props.put("mail.smtp.writetimeout", "15000")

      val session = Session.getInstance(
        props,
        new Authenticator {
          override def getPasswordAuthentication: PasswordAuthentication =
            PasswordAuthentication(config.email, config.password)
        }
      )

      val msg = MimeMessage(session)
      msg.setFrom(InternetAddress(config.email))
      msg.setRecipients(Message.RecipientType.TO, request.recipientMail)
      msg.setSubject(request.subject)
      msg.setText(request.content)

      Transport.send(msg)
      logger.logInfo(s"mail sent to '${request.recipientMail}'")
      SendMailResponse(None, sent = true)
    }
  }

  /** SMTP errors can contain recipients or message contents; only safe diagnostics cross the command boundary. */
  private[server] def withDeliveryContext[A](send: => A): A =
    try send
    catch case error: MessagingException =>
      def causes(current: Throwable, remaining: Int): Seq[Throwable] =
        current :: (if remaining == 0 then Nil else {
          val next = current match {
            case mail: MessagingException => Option(mail.getNextException).orElse(Option(mail.getCause))
            case other => Option(other.getCause)
          }
          next.filterNot(_ eq current).toList.flatMap(causes(_, remaining - 1))
        })
      val chain = causes(error, 8)
      val reason =
        if chain.exists(_.isInstanceOf[AuthenticationFailedException]) then "The SMTP server rejected authentication; check MAIL_EMAIL and MAIL_PW."
        else if chain.exists(_.isInstanceOf[SocketTimeoutException]) then "The SMTP connection timed out; check mail-server availability and outbound SMTP access."
        else if chain.exists(_.isInstanceOf[UnknownHostException]) then "The SMTP hostname could not be resolved; check MAIL_SMTP_HOST and server DNS."
        else if chain.exists(_.isInstanceOf[ConnectException]) then "The SMTP connection failed; check MAIL_SMTP_HOST, MAIL_SMTP_PORT and outbound SMTP access."
        else if chain.exists(_.isInstanceOf[SSLException]) then "SMTP TLS negotiation failed; check the mail-server certificate and STARTTLS support."
        else if error.isInstanceOf[AddressException] then "The sender or recipient email address is invalid."
        else if error.isInstanceOf[SendFailedException] then "The SMTP server rejected delivery; check sender/recipient addresses and mail-server policy."
        else "The SMTP exchange failed; check mail-server configuration and STARTTLS support."
      throw IllegalStateException(s"Could not send email: $reason (mail error: ${error.getClass.getSimpleName})")

  def handleSendMailRequest(request: SendMailRequest, logger: Logger): Future[SendMailResponse] = Future {
    sendMail(request, logger, JvmUtils.env)
  }(using ExecutionContext.global)

}
