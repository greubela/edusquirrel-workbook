package it.evadid.workbook.elements.interactionElements.emailSimulator

case class InboxState(
  mailList: List[Mail],
  mailFolders: Map[String, List[String]] = Map(
    "Posteingang" -> List(),
    "Gesendet" -> List(),
    "Markiert" -> List(),
    "Archiv" -> List(),
    "Papierkorb" -> List()
  )
) {
  def getMailsInFolder(folder: String): List[Mail] = {
    mailFolders.getOrElse(folder, List()).flatMap(id => mailList.find(_.id == id))
  }
  
  def getMailById(id: String): Option[Mail] = mailList.find(_.id == id)
  
  def updateMail(newMail: Mail): InboxState = {
    val updatedList = mailList.filterNot(_.id == newMail.id) :+ newMail
    copy(mailList = updatedList)
  }
  
  def moveMail(mailId: String, targetFolder: String): InboxState = {
    val updatedMailFolders = mailFolders.updated(
      targetFolder,
      mailFolders.getOrElse(targetFolder, List()) :+ mailId
    )
    copy(mailFolders = updatedMailFolders)
  }
  
  def markAsRead(mailId: String): InboxState = {
    mailList.find(_.id == mailId).map { mail =>
      updateMail(mail.copy(read = true))
    }.getOrElse(this)
  }
  
  def markAsUnread(mailId: String): InboxState = {
    mailList.find(_.id == mailId).map { mail =>
      updateMail(mail.copy(read = false))
    }.getOrElse(this)
  }
  
  def archiveMail(mailId: String): InboxState = moveMail(mailId, "Archiv")
  
  def deleteMail(mailId: String): InboxState = moveMail(mailId, "Papierkorb")
  
  def markMail(mailId: String): InboxState = moveMail(mailId, "Markiert")
  
  def unmarkMail(mailId: String): InboxState = {
    val updatedMailFolders = mailFolders.updated("Markiert", mailFolders.getOrElse("Markiert", List()).filter(_ != mailId))
    copy(mailFolders = updatedMailFolders)
  }
}

object InboxState {
  def empty: InboxState = InboxState(List())
  
  def withMails(mails: List[Mail]): InboxState = InboxState(
    mailList = mails,
    mailFolders = Map(
      "Posteingang" -> mails.map(_.id),
      "Gesendet" -> List(),
      "Markiert" -> List(),
      "Archiv" -> List(),
      "Papierkorb" -> List()
    )
  )
}
