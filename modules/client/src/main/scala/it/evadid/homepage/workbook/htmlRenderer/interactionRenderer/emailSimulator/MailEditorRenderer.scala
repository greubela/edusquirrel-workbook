package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.emailSimulator

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.MailEditor.{MailEditor as Editor}
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.emailSimulator.*
import it.evadid.workbook.interaction.sync.UpdateImportance

case object HtmlMailEditorRenderer extends LineBasedRenderingFactory[MailEditor] {
  override protected def createRendering(element: MailEditor): AtomarLineRendering = {
    val state = element.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR).toAirstreamVar
    val open = HtmlButtonElement.withTextLabel("basic/OpenEditor", _ =>
      fullInfo.displayControl.setFullscreen(Editor(state, element.account, element.allowCompose)))
    val helper = it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper.singleton
    val preview = div(cls := "mail-preview",
      p(text <-- helper.plaintextStringSignal("emailSimulator/simulation")),
      ul(MailFolder.all.map(f => li(span(text <-- helper.plaintextStringSignal(s"emailSimulator/$f")), " · ",
        span(text <-- state.signal.map(_.inboxState.getMailsInFolder(f).size.toString))))),
      p(text <-- state.signal.map(s => { val r = s.inboxState.sortingResult; s"${r.sorted} / ${r.total}" }))
    )
    AtomarLineRendering.cardLine(element, List(
      ElementCard(LanguageMapContentId("basic/openEditor"), open.getDomElement()),
      ElementCard(LanguageMapContentId("emailSimulator/title"), preview)))
  }
}
