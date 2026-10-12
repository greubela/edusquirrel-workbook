package it.evadid.homepage.webElements.editor.code.MailEditor

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.{HtmlAppElement, FullscreenLifecycle}
import it.evadid.workbook.elements.interactionElements.emailSimulator.*

case class MailEditor(underlyingVar: Var[InboxStateScaffolding], account: String = "opa.jürgen@gmail.com",
                      allowCompose: Boolean = true) extends HtmlAppElement with FullscreenLifecycle {
  private val controller = new MailEditorState(underlyingVar, allowCompose)
  import controller.*
  private def labelText(key: String): Signal[String] = laminarHelper.plaintextStringSignal(s"emailSimulator/$key")
  private def action(key: String, enabled: Signal[Boolean])(run: => Unit): Element = button(
    typ := "button", text <-- labelText(key), disabled <-- enabled.map(!_), onClick --> (_ => run))
  private val hasSelection = selected.map(_.isDefined)
  private def editDraft(d: MailDraft): Element = form(
    cls := "mail-compose",
    onSubmit.preventDefault --> (_ => send(java.util.UUID.randomUUID().toString, account, java.time.LocalDateTime.now().toString)),
    h2(text <-- labelText("compose")),
    label(span(text <-- labelText("to")), input(value := d.to,
      onInput.mapToValue --> (v => draft.update(_.map(_.copy(to = v)))))),
    label(span(text <-- labelText("subject")), input(value := d.subject,
      onInput.mapToValue --> (v => draft.update(_.map(_.copy(subject = v)))))),
    label(span(text <-- labelText("body")), textArea(value := d.body,
      onInput.mapToValue --> (v => draft.update(_.map(_.copy(body = v)))))),
    d.attachment.map(a => p(text <-- labelText("attachment"), " ", a)).toList,
    div(button(typ := "submit", text <-- labelText("send")),
      button(typ := "button", text <-- labelText("cancel"), onClick --> (_ => cancelDraft())))
  )
  private def viewer(mail: Option[Mail]): Element = mail match {
    case None => div(cls := "mail-viewer", h2(text <-- labelText("noSelection")), p(text <-- labelText("chooseMail")))
    case Some(m) => div(cls := "mail-viewer",
      headerTag(h2(m.subject), p(strong(m.senderName), " <", m.sender, ">"),
        p(m.timestamp), p(text <-- labelText("to"), " ", (if (m.recipients.isEmpty) List(account) else m.recipients).mkString(", "))),
      MailBody.render(m, hoverUrl, notice),
      m.attachment.map(a => button(typ := "button", cls := "mail-attachment", text <-- labelText("attachment"), " ", a,
        onClick --> (_ => notice.set("attachmentSimulated")))).toList)
  }
  override def getDomElement(): Element = div(
    cls := "mail-simulator",
    headerTag(cls := "mail-toolbar",
      action("compose", Val(allowCompose))(beginDraft(MailDraft())),
      div(cls := "mail-folder-summary", strong(text <-- folder.signal.flatMapSwitch(labelText)),
        span(text <-- mails.combineWith(labelText("messageCount")).map((ms, label) => label.replace("{count}", ms.size.toString)))),
      action("reply", hasSelection.map(_ && allowCompose))(selectedNow.foreach(m => beginDraft(MailDraft.reply(m, MailBody.plainText(m))))),
      action("forward", hasSelection.map(_ && allowCompose))(selectedNow.foreach(m => beginDraft(MailDraft.forward(m, MailBody.plainText(m))))),
      action("archive", hasSelection)(moveSelected(MailFolder.Archive)),
      action("mark", hasSelection)(moveSelected(MailFolder.Marked)),
      action("delete", hasSelection)(moveSelected(MailFolder.Trash)),
      action("unread", hasSelection)(unread()),
      action("restore", selected.map(_.exists(_.realFolder != MailFolder.Inbox)))(moveSelected(MailFolder.Inbox))
    ),
    div(cls := "mail-workspace",
      navTag(cls := "mail-folders", aria.label <-- labelText("folders"),
        MailFolder.all.zip(List("📥", "📤", "★", "▣", "♲")).map { (f, icon) => button(
          typ := "button", cls.toggle("is-selected") <-- folder.signal.map(_ == f),
          aria.pressed <-- folder.signal.map(v => (v == f).toString), span(icon), span(text <-- labelText(f)),
          span(cls := "mail-count", text <-- underlyingVar.signal.map(_.inboxState.getMailsInFolder(f).size.toString)),
          onClick --> (_ => selectFolder(f))) }),
      div(cls := "mail-list", role := "list", children <-- mails.combineWith(selection.signal).map { (ms, chosen) =>
        if (ms.isEmpty) List(p(text <-- labelText("emptyFolder"))) else ms.map(m => button(
          typ := "button", cls := "mail-list-item", cls.toggle("is-unread") := !m.read,
          cls.toggle("is-selected") := chosen.contains(m.id), aria.pressed := chosen.contains(m.id).toString,
          span(cls := "mail-sender", (if (m.read) "" else "● ") + m.senderName),
          span(m.subject), timeTag(m.timestamp), onClick --> (_ => selectMail(m.id))))
      }),
      div(cls := "mail-reading-pane",
        child <-- draft.signal.map(_.isDefined).distinct.combineWith(selected).map { (composing, mail) =>
          if (composing) editDraft(draft.now().get) else viewer(mail)
        })
    ),
    footerTag(cls := "mail-status", role := "status", aria.live := "polite",
      span("🌐 ", text <-- hoverUrl.signal),
      span(text <-- notice.signal.flatMapSwitch(key => if (key.isEmpty) labelText("simulation") else labelText(key)))
    ),
    div(cls := "mail-feedback",
      action("check", underlyingVar.signal.map(_.inboxState.sortingResult.total > 0))(showFeedback.set(true)),
      child <-- showFeedback.signal.combineWith(underlyingVar.signal).map { (show, state) =>
        val r = state.inboxState.sortingResult
        if (!show) span() else p(
          text <-- labelText(if (!r.completed) "sortFirst" else if (r.passed) "passed" else "tryAgain"),
          s" (${r.correct}/${r.total})")
      })
  )
  override def dismissOnOutsideClick: Boolean = false
  override def onFullscreenClose(): Unit = { hoverUrl.set(""); cancelDraft() }
}
