package it.evadid.workbook.elements.interactionElements.emailSimulator

import upickle.default.*

case class Mail(
  id: String,
  sender: String,
  senderName: String,
  subject: String,
  body: String,
  realFolder: String,
  attachment: Option[String],
  timestamp: String,
  read: Boolean = false
) derives ReadWriter
