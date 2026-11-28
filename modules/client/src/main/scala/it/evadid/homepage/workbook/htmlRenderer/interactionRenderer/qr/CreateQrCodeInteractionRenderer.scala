package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.qr

import it.evadid.homepage.control.model.FullInfo

import it.evadid.core.datastructures.language.LanguageMapContentId

import it.evadid.homepage.webElements.editor.qr.{QrCodeEditor, QrCodeView}
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.qr.CreateQrCodeInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

case object CreateQrCodeInteractionRenderer extends LineBasedRenderingFactory[CreateQrCodeInteraction] {
  override protected def createRendering(fullInfo: FullInfo, element: CreateQrCodeInteraction): AtomarLineRendering = {
    val state = fullInfo.interactions.boundVar(element, UpdateImportance.MAJOR)
    AtomarLineRendering.editorCardLine(fullInfo, element, ElementCard(fullInfo, LanguageMapContentId("basic/qrTitle"), QrCodeView.preview(fullInfo, state.signal, element.requirements)),
      () => QrCodeEditor(fullInfo, state, element.requirements), editorFirst = true)
  }
}
