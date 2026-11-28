package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.emailSimulator

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.MailEditor.MailEditor as Editor
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.interactions.emailSimulator.*

/** The workbook line shared by the mail interactions: an editor button and a folder summary of the inbox. */
object MailRendering {
  def cardLine(fullInfo: FullInfo, element: WorkbookElement, inbox: Var[InboxStateScaffolding], account: String, allowCompose: Boolean)
      : AtomarLineRendering = {
    val helper = LaminarRenderHelper(fullInfo)
    val open = HtmlButtonElement.withTextLabel(fullInfo, "basic/OpenEditor", _ =>
      fullInfo.displayControl.setFullscreen(Editor(fullInfo, inbox, account, allowCompose)))
    val preview = div(cls := "mail-preview",
      p(text <-- helper.plaintextStringSignal("emailSimulator/simulation")),
      ul(MailFolder.all.map(f => li(span(text <-- helper.plaintextStringSignal(s"emailSimulator/$f")), " · ",
        span(text <-- inbox.signal.map(_.inboxState.getMailsInFolder(f).size.toString))))),
      p(text <-- inbox.signal.map(s => { val r = s.inboxState.sortingResult; s"${r.sorted} / ${r.total}" }))
    )
    AtomarLineRendering.cardLine(fullInfo, element, List(
      ElementCard(fullInfo, LanguageMapContentId("basic/openEditor"), open.getDomElement()),
      ElementCard(fullInfo, LanguageMapContentId("emailSimulator/title"), preview)))
  }
}
