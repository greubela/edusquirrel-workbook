package it.evadid.workbook.elements.interactionElements.emailSimulator

import upickle.default.*

case class MailSortingResult(total: Int, sorted: Int, correct: Int) derives upickle.default.ReadWriter {
  def completed: Boolean = total > 0 && sorted == total
  def passed: Boolean = completed && correct == total
}

case class InboxState(mailList: List[Mail],
                      mailFolders: Map[String, List[String]] = MailFolder.emptyIndex) derives ReadWriter {
  // Placement on each message is authoritative; stale indexes cannot duplicate messages.
  def getMailsInFolder(folder: String): List[Mail] = mailList.filter(_.realFolder == folder)
  def getMailById(id: String): Option[Mail] = mailList.find(_.id == id)
  def normalized: InboxState = InboxState.withMails(mailList)

  def updateMail(mail: Mail): InboxState = {
    if (!MailFolder.all.contains(mail.realFolder)) this
    else InboxState.withMails(if (getMailById(mail.id).isDefined)
      mailList.map(m => if (m.id == mail.id) mail else m) else mailList :+ mail)
  }
  def moveMail(id: String, target: String): InboxState =
    if (!MailFolder.all.contains(target)) this
    else getMailById(id).map(m => updateMail(m.copy(realFolder = target))).getOrElse(this)
  def markAsRead(id: String): InboxState = getMailById(id).map(m => updateMail(m.copy(read = true))).getOrElse(this)
  def markAsUnread(id: String): InboxState = getMailById(id).map(m => updateMail(m.copy(read = false))).getOrElse(this)
  def archiveMail(id: String): InboxState = moveMail(id, MailFolder.Archive)
  def deleteMail(id: String): InboxState = moveMail(id, MailFolder.Trash)
  // Marked is the action-needed folder, as in the original phishing simulator.
  def markMail(id: String): InboxState = moveMail(id, MailFolder.Marked)
  def unmarkMail(id: String): InboxState = getMailById(id).filter(_.realFolder == MailFolder.Marked)
    .map(_ => moveMail(id, MailFolder.Inbox)).getOrElse(this)
  def sortingResult: MailSortingResult = {
    val exerciseMails = mailList.filter(_.expectedFolder.isDefined)
    MailSortingResult(exerciseMails.size, exerciseMails.count(_.realFolder != MailFolder.Inbox),
      exerciseMails.count(m => m.expectedFolder.contains(m.realFolder)))
  }
  /** Sending is a local state change only. No transport or network service exists. */
  def sendDraft(draft: MailDraft, id: String, account: String, timestamp: String): Either[String, InboxState] =
    draft.validationError match {
      case Some(error) => Left(error)
      case None if id.trim.isEmpty || getMailById(id).isDefined => Left("duplicateId")
      case None => Right(updateMail(Mail(id, account, account, draft.subject.trim, draft.body,
        MailFolder.Sent, draft.attachment, timestamp, read = true, recipients = draft.recipientList)))
    }
}
object InboxState {
  def empty: InboxState = InboxState(Nil)
  def withMails(mails: List[Mail]): InboxState = {
    require(mails.map(_.id).distinct.size == mails.size, "Mail IDs must be unique")
    require(mails.forall(m => MailFolder.all.contains(m.realFolder)), "Unknown mail folder")
    require(mails.forall(_.expectedFolder.forall(f => List(MailFolder.Marked, MailFolder.Archive, MailFolder.Trash).contains(f))),
      "Expected folder must be Marked, Archive or Trash")
    InboxState(mails, MailFolder.all.map(f => f -> mails.filter(_.realFolder == f).map(_.id)).toMap)
  }
}
