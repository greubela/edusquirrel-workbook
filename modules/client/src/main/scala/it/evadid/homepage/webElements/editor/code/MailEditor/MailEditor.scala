package it.evadid.homepage.webElements.editor.code.MailEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.editor.abstractions.SimpleWebEditor
import it.evadid.homepage.webElements.editor.config.WebEditorConfig
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.elements.interactionElements.emailSimulator.{InboxStateScaffolding, Mail, InboxState}
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}

case class MailEditor(
  override val underlyingVar: Var[InboxStateScaffolding],
  override val config: Val[WebEditorConfig] = Val(WebEditorConfig.defaultConfig)
) extends SimpleWebEditor[InboxStateScaffolding, WebEditorConfig] {

  override def getDomElement(): Element = domElement

  private val domElement: Element = createMailEditor(underlyingVar.signal)

  def createMailEditor(stateSignal: Signal[InboxStateScaffolding]): Element = {
    div(
      cls := "mail-editor-container",
      div(
        cls := "mail-editor__header",
        h3(text <-- stateSignal.map(_.inboxState.mailList.size).map(count => s"E-Mail Editor (${count} messages)"))
      ),
      div(
        cls := "mail-editor__content",
        child <-- stateSignal.map { state =>
          renderInbox(state.inboxState)
        }
      )
    )
  }

  def renderInbox(inboxState: InboxState): Element = {
    val folderCards = List(
      createFolderCard("Posteingang", inboxState, "inbox"), 
      createFolderCard("Markiert", inboxState, "star"), 
      createFolderCard("Archiv", inboxState, "archive"), 
      createFolderCard("Papierkorb", inboxState, "trash")
    )

    div(
      cls := "mail-editor__inbox",
      div(
        cls := "mail-editor__folders",
        folderCards.map(_.getDomElement())
      ),
      div(
        cls := "mail-editor__mail-list",
        inboxState.mailList.map { mail =>
          createMailCard(mail).getDomElement()
        }
      )
    )
  }

  def createFolderCard(folderName: String, inboxState: InboxState, icon: String): ElementCard = {
    val folderMails = inboxState.getMailsInFolder(folderName)
    
    ElementCard(
      LanguageMapContentId(s"emailSimulator/folder_${folderName}"), 
      div(
        cls := "mail-folder-card",
        div(
          cls := "mail-folder-icon",
          i(cls := s"material-icons", icon)
        ),
        div(
          cls := "mail-folder-info",
          span(cls := "mail-folder-name", folderName),
          span(cls := "mail-folder-count", folderMails.size.toString)
        )
      )
    )
  }

  def createMailCard(mail: Mail): ElementCard = {
    ElementCard(
      LanguageMapContentId(s"emailSimulator/mail_${mail.id}"), 
      div(
        cls := "mail-card",
        div(
          cls := "mail-card__header",
          span(cls := "mail-card__sender", mail.senderName),
          span(cls := "mail-card__subject", mail.subject)
        ),
        div(
          cls := "mail-card__body",
          span(cls := "mail-card__preview", mail.body.take(100))
        ),
        div(
          cls := "mail-card__footer",
          span(cls := "mail-card__timestamp", mail.timestamp)
        )
      )
    )
  }
}
