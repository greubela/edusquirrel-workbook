package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.emailSimulator

import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.emailSimulator.{InboxState, Mail, MailEditor, InboxStateScaffolding}
import it.evadid.workbook.interaction.sync.UpdateImportance

case object HtmlMailEditorRenderer extends LineBasedRenderingFactory[MailEditor] {

  override protected def createRendering(workbookElement: MailEditor): AtomarLineRendering = {
    val boundVar = workbookElement.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR).toAirstreamVar

    val mailListVar: Var[List[Mail]] = boundVar.signal.map(_.inboxState.mailList).toVar(List())

    val folderCards = List(
      createFolderCard("Posteingang", mailListVar, "inbox"), 
      createFolderCard("Markiert", mailListVar, "star"), 
      createFolderCard("Archiv", mailListVar, "archive"), 
      createFolderCard("Papierkorb", mailListVar, "trash")
    )

    val inboxCard = ElementCard(
      LanguageMapContentId("emailSimulator/inbox"), 
      div(
        cls := "mail-editor__container",
        div(
          cls := "mail-editor__folders",
          folderCards.map(_.getDomElement())
        ),
        div(
          cls := "mail-editor__mail-list",
          mailListVar.signal.map { mails =>
            div(
              mails.map { mail =>
                createMailCard(mail, boundVar)
              }
            )
          }
        )
      )
    )

    AtomarLineRendering.cardLine(workbookElement, List(inboxCard))
  }

  def createFolderCard(folderName: String, mailListVar: Var[List[Mail]], icon: String): ElementCard = {
    val folderMailCount = mailListVar.signal.map(_.size)
    
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
          span(cls := "mail-folder-count", folderMailCount.map(_.toString))
        )
      )
    )
  }

  def createMailCard(mail: Mail, boundVar: Var[InboxStateScaffolding]): ElementCard = {
    val mailCard = div(
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
      ),
      div(
        cls := "mail-card__actions",
        button(
          cls := "mail-card__action mail-card__action--read",
          "Markieren"
        ),
        button(
          cls := "mail-card__action mail-card__action--archive",
          "Archivieren"
        ),
        button(
          cls := "mail-card__action mail-card__action--delete",
          "Löschen"
        )
      )
    )

    ElementCard(
      LanguageMapContentId(s"emailSimulator/mail_${mail.id}"), 
      mailCard
    )
  }
}
