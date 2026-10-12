package it.evadid.workbook.elements.interactionElements.emailSimulator

import upickle.default.ReadWriter

case class MailDraft(to: String = "", subject: String = "", body: String = "", attachment: Option[String] = None) derives ReadWriter {
  def recipientList: List[String] = to.split("[,;]", -1).toList.map(_.trim)
  def validationError: Option[String] = {
    val valid = recipientList.forall(r => r.matches("[^\\s@,;<>]+@[^\\s@,;<>]+\\.[^\\s@,;<>]+"))
    if (!valid) Some("invalidRecipient")
    else if (subject.trim.isEmpty) Some("missingSubject")
    else if (body.trim.isEmpty) Some("missingBody")
    else None
  }
}
object MailDraft {
  private def prefix(value: String, prefix: String): String =
    if (value.toLowerCase.startsWith(prefix.toLowerCase)) value else prefix + " " + value
  def reply(mail: Mail, plainBody: String): MailDraft =
    MailDraft(mail.sender, prefix(mail.subject, "Re:"), s"\n\n${mail.senderName} <${mail.sender}>:\n$plainBody")
  def forward(mail: Mail, plainBody: String): MailDraft =
    MailDraft("", prefix(mail.subject, "Fwd:"), s"\n\n${mail.senderName} <${mail.sender}>:\n$plainBody", mail.attachment)
}
