package it.evadid.workbook.elements.interactionElements.emailSimulator

import upickle.default.*

/** realFolder is the learner's current placement; expectedFolder is the exercise answer. */
case class Mail(id: String, sender: String, senderName: String, subject: String,
                body: String, realFolder: String, attachment: Option[String], timestamp: String,
                read: Boolean = false, expectedFolder: Option[String] = None,
                bodyHtml: Boolean = false, recipients: Seq[String] = Nil) derives ReadWriter

object MailFolder {
  val Inbox = "Posteingang"
  val Sent = "Gesendet"
  val Marked = "Markiert"
  val Archive = "Archiv"
  val Trash = "Papierkorb"
  val all: Seq[String] = List(Inbox, Sent, Marked, Archive, Trash)
  def emptyIndex: Map[String, Seq[String]] = all.map(_ -> List.empty[String]).toMap
}
