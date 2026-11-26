package it.evadid.server

import it.evadid.core.datastructures.file.{CopyrightInfo, FileDescription, LoadedFile, NamedDataFileDescription}
import it.evadid.distribution.commandTypes.MailCommands.SendMailRequest
import jakarta.mail.{Message, Part, Session}
import jakarta.mail.internet.{MimeMessage, MimeMultipart}
import munit.FunSuite
import upickle.default.*

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.Properties
import scala.concurrent.Future

class SendMailAttachmentSpec extends FunSuite {

  private val session = Session.getInstance(Properties())

  private def file(name: String, bytes: Array[Byte]): LoadedFile = LoadedFile(new FileDescription {
    def copyrightInfo = CopyrightInfo.unknownCopyrightInfo
    def asUrlString = name
    def loadData(): Future[LoadedFile] = Future.successful(LoadedFile(this, bytes))
    def getChildrenFile(childName: String, copyright: CopyrightInfo): Option[FileDescription] = None
  }, bytes)

  /** Serializes the message to its SMTP wire form and parses it back, as a receiving mail client would. */
  private def delivered(request: SendMailRequest): MimeMessage = {
    val out = ByteArrayOutputStream()
    SendMailCommand.mailMessage(session, "sender@example.org", request).writeTo(out)
    MimeMessage(session, ByteArrayInputStream(out.toByteArray))
  }

  test("a mail without attachments stays a plain UTF-8 text message with sender, recipient and subject") {
    val message = delivered(SendMailRequest("a@example.org", "Grüße 学校", "Zeile 1\nZeile 2"))
    assertEquals(message.getFrom.map(_.toString).toList, List("sender@example.org"))
    assertEquals(message.getRecipients(Message.RecipientType.TO).map(_.toString).toList, List("a@example.org"))
    assertEquals(message.getSubject, "Grüße 学校")
    assert(message.isMimeType("text/plain"))
    assertEquals(message.getContent.toString.replace("\r\n", "\n"), "Zeile 1\nZeile 2")
  }

  test("attachments follow the text body as named parts with exact bytes and guessed or fallback content types") {
    val pdf = Array[Byte](37, 80, 68, 70, -1, 0, 13, 10)
    val binary = Array.tabulate[Byte](256)(_.toByte)
    val message = delivered(SendMailRequest("a@example.org", "Subject", "Grüße",
      List(file("Übung.pdf", pdf), file("dir/data.unknownext", binary))))
    assert(message.isMimeType("multipart/*"))
    val multipart = message.getContent.asInstanceOf[MimeMultipart]
    assertEquals(multipart.getCount, 3)
    val text = multipart.getBodyPart(0)
    assert(text.isMimeType("text/plain"))
    assertEquals(text.getContent, "Grüße")
    assertEquals(text.getDisposition, null)

    val pdfPart = multipart.getBodyPart(1)
    assertEquals(pdfPart.getFileName, "Übung.pdf")
    assertEquals(pdfPart.getDisposition, Part.ATTACHMENT)
    assert(pdfPart.isMimeType("application/pdf"))
    assertEquals(pdfPart.getInputStream.readAllBytes().toList, pdf.toList)

    val binaryPart = multipart.getBodyPart(2)
    assertEquals(binaryPart.getFileName, "data.unknownext")
    assert(binaryPart.isMimeType("application/octet-stream"))
    assertEquals(binaryPart.getInputStream.readAllBytes().toList, binary.toList)
  }

  test("attachments received through the command codec are sent unchanged") {
    val bytes = "a,b\n1,2".getBytes("UTF-8")
    val transported = read[SendMailRequest](write(SendMailRequest("a@example.org", "S", "B", List(file("table.csv", bytes)))))
    val attachment = delivered(transported).getContent.asInstanceOf[MimeMultipart].getBodyPart(1)
    assertEquals(attachment.getFileName, "table.csv")
    assertEquals(attachment.getInputStream.readAllBytes().toList, bytes.toList)
  }

  test("a nameless attachment, e.g. from a data URL, gets a fallback name and keeps its declared media type") {
    val bytes = "Grüße".getBytes("UTF-8")
    val dataUrlFile = file("data:text/plain;charset=utf-8;base64,R3LDvMOfZQ==", bytes)
    val misleadingName = NamedDataFileDescription("report.pdf", bytes, CopyrightInfo.unknownCopyrightInfo, Some("text/csv"))
    val parts = delivered(SendMailRequest("a@example.org", "S", "B", List(dataUrlFile, misleadingName))).getContent.asInstanceOf[MimeMultipart]
    val inline = parts.getBodyPart(1)
    assertEquals(inline.getFileName, SendMailCommand.unnamedAttachmentName)
    assert(inline.isMimeType("text/plain"))
    assertEquals(inline.getContent, "Grüße")
    assert(parts.getBodyPart(2).isMimeType("text/csv"))
    assertEquals(parts.getBodyPart(2).getFileName, "report.pdf")
  }
}
