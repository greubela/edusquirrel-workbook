package it.evadid.homepage.webElements.editor.code.MailEditor

import com.raquo.laminar.api.L.*
import it.evadid.workbook.elements.interactionElements.emailSimulator.*

/** UI decisions are independent of DOM, fullscreen and synchronization services. */
class MailEditorState(val state: Var[InboxStateScaffolding], val allowCompose: Boolean) {
  val folder = Var(MailFolder.Inbox)
  val selection = Var(Option.empty[String])
  val draft = Var(Option.empty[MailDraft])
  val notice = Var("")
  val hoverUrl = Var("")
  val showFeedback = Var(false)
  val mails: Signal[List[Mail]] = state.signal.combineWith(folder.signal).map((s, f) => s.inboxState.getMailsInFolder(f))
  val selected: Signal[Option[Mail]] = mails.combineWith(selection.signal).map((ms, id) => ms.find(m => id.contains(m.id)))
  def selectedNow: Option[Mail] = state.now().inboxState.getMailsInFolder(folder.now()).find(m => selection.now().contains(m.id))
  def selectFolder(value: String): Unit = if (MailFolder.all.contains(value)) {
    folder.set(value); selection.set(None); hoverUrl.set("")
  }
  def selectMail(id: String): Unit = if (state.now().inboxState.getMailsInFolder(folder.now()).exists(_.id == id)) {
    selection.set(Some(id)); state.update(s => s.copy(inboxState = s.inboxState.markAsRead(id))); hoverUrl.set("")
  }
  def moveSelected(target: String): Unit = selectedNow.foreach { m =>
    if (MailFolder.all.contains(target)) {
      state.update(s => s.copy(inboxState = s.inboxState.moveMail(m.id, target)))
      selection.set(None); hoverUrl.set("")
    }
  }
  def unread(): Unit = selectedNow.foreach(m => state.update(s => s.copy(inboxState = s.inboxState.markAsUnread(m.id))))
  def beginDraft(value: MailDraft): Unit = if (allowCompose) { draft.set(Some(value)); notice.set("") }
  def cancelDraft(): Unit = { draft.set(None); notice.set("") }
  def send(id: String, account: String, timestamp: String): Boolean = {
    if (!allowCompose) false
    else draft.now() match {
      case None => false
      case Some(d) => state.now().inboxState.sendDraft(d, id, account, timestamp) match {
        case Left(error) => notice.set(error); false
        case Right(updated) =>
          state.set(InboxStateScaffolding(updated)); draft.set(None); folder.set(MailFolder.Sent)
          selection.set(Some(id)); notice.set("sentLocally"); true
      }
    }
  }
}
