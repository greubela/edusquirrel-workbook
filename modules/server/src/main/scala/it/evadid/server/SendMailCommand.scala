package it.evadid.server

import it.evadid.distribution.commandTypes.MailCommands.{SendMailRequest, SendMailResponse}
import it.evadid.util.{JvmUtils, Logger}
import jakarta.mail.*
import jakarta.mail.internet.{InternetAddress, MimeMessage}

import java.util.Properties
import scala.concurrent.{ExecutionContext, Future}

object SendMailCommand {

  private[server] final case class MailConfig(
                                               email: String,
                                               password: String,
                                               host: String,
                                               port: String
                                             )

  private def requiredEnv(name: String, envProvider: String => Option[String]): String =
    envProvider(name).map(_.trim).filter(_.nonEmpty).getOrElse(throw new IllegalStateException(s"$name is not configured"))

  private[server] def readMailConfig(envProvider: String => Option[String]): MailConfig =
    MailConfig(
      email = requiredEnv("MAIL_EMAIL", envProvider),
      password = requiredEnv("MAIL_PW", envProvider),
      host = envProvider("MAIL_SMTP_HOST").map(_.trim).filter(_.nonEmpty).getOrElse("smtp.lima-city.de"),
      port = envProvider("MAIL_SMTP_PORT").map(_.trim).filter(_.nonEmpty).getOrElse("587")
    )

  private[server] def sendMail(request: SendMailRequest, logger: Logger, envProvider: String => Option[String]): SendMailResponse = {
    val config = readMailConfig(envProvider)
    logger.logInfo(s"sending mail to '${request.recipientMail}' with subject '${request.subject}' via ${config.host}:${config.port}")

    val props = new Properties()
    props.put("mail.smtp.auth", "true")
    props.put("mail.smtp.starttls.enable", "true")
    props.put("mail.smtp.host", config.host)
    props.put("mail.smtp.port", config.port)

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
    SendMailResponse(sent = true)
  }

  def handleSendMailRequest(request: SendMailRequest, logger: Logger): Future[SendMailResponse] = Future {
    sendMail(request, logger, JvmUtils.env)
  }(using ExecutionContext.global)

}
