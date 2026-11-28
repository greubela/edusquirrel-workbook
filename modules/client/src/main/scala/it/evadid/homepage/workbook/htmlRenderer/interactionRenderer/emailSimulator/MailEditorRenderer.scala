package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.emailSimulator

import it.evadid.workbook.control.InteractionSignals.*


import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.interactions.emailSimulator.MailEditor
import it.evadid.workbook.sync.UpdateImportance

case object HtmlMailEditorRenderer extends LineBasedRenderingFactory[MailEditor] {
  override protected def createRendering(fullInfo: FullInfo, element: MailEditor): AtomarLineRendering =
    MailRendering.cardLine(fullInfo, element,
      fullInfo.interactions.boundVar(element, UpdateImportance.MAJOR),
      element.account, element.allowCompose)
}
