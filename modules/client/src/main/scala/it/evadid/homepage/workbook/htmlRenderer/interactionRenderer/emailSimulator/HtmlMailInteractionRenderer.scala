package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.emailSimulator

import it.evadid.workbook.interaction.state.InteractionSignals.*


import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.emailSimulator.MailInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

case object HtmlMailInteractionRenderer extends LineBasedRenderingFactory[MailInteraction] {
  override protected def createRendering(fullInfo: FullInfo, element: MailInteraction): AtomarLineRendering =
    MailRendering.cardLine(fullInfo, element,
      fullInfo.interactions.boundVar(element, UpdateImportance.MAJOR),
      element.account, element.allowCompose)
}
