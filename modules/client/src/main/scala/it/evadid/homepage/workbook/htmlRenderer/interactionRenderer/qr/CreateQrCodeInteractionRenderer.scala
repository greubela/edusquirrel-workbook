package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.qr

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.qr.{QrCodeEditor, QrCodeView}
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.qr.CreateQrCodeInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

case object CreateQrCodeInteractionRenderer extends LineBasedRenderingFactory[CreateQrCodeInteraction] {
  override protected def createRendering(element: CreateQrCodeInteraction): AtomarLineRendering = {
    val state = element.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR).toAirstreamVar
    // Construct on opening, so draft controls always start with the currently restored state.
    val open = HtmlButtonElement.withTextLabel("basic/OpenEditor", _ =>
      fullInfo.displayControl.setFullscreen(QrCodeEditor(state, element.requirements)))
    AtomarLineRendering.cardLine(element, List(
      ElementCard(LanguageMapContentId("basic/openEditor"), open.getDomElement()),
      ElementCard(LanguageMapContentId("basic/qrTitle"), QrCodeView.preview(state.signal, element.requirements))
    ))
  }
}
