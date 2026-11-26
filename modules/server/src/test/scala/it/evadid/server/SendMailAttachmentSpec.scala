package it.evadid.server

import it.evadid.core.datastructures.file.{CopyrightInfo, FileDescription, LoadedFile}
import it.evadid.distribution.commandTypes.MailCommands.SendMailRequest
import jakarta.mail.Part
import jakarta.mail.internet.MimeBodyPart
import munit.FunSuite

import scala.concurrent.Future

class SendMailAttachmentSpec extends FunSuite {

  private def file(name: String, bytes: Array[Byte]): LoadedFile = LoadedFile(new FileDescription {
    def copyrightInfo = CopyrightInfo.unknownCopyrightInfo
    def asUrlString = name
    def loadData(): Future[LoadedFile] = Future.successful(LoadedFile(this, bytes))
    def getChildrenFile(childName: String, copyright: CopyrightInfo): Option[FileDescription] = None
  }, bytes)

  test("attachments follow the text body as named parts with guessed or fallback content types") {
    val pdf = Array[Byte](37, 80, 68, 70, -1, 0)
    val request = SendMailRequest("a@example.org", "Subject", "Grüße", List(file("report.pdf", pdf), file("data.unknownext", Array[Byte](1, 2))))
    val multipart = SendMailCommand.multipartContent(request)
    assertEquals(multipart.getCount, 3)
    val text = multipart.getBodyPart(0).asInstanceOf[MimeBodyPart]
    assertEquals(text.getContent, "Grüße")
    val attachment = multipart.getBodyPart(1)
    assertEquals(attachment.getFileName, "report.pdf")
    assertEquals(attachment.getDisposition, Part.ATTACHMENT)
    assertEquals(attachment.getDataHandler.getContentType, "application/pdf")
    assertEquals(attachment.getInputStream.readAllBytes().toList, pdf.toList)
    assertEquals(multipart.getBodyPart(2).getDataHandler.getContentType, "application/octet-stream")
  }
}
