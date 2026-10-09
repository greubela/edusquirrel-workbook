package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.blockchain

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.blockchain.BlockchainEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.blockchain.BlockchainInteraction

case object BlockchainRenderer extends LineBasedRenderingFactory[BlockchainInteraction] {
  override protected def createRendering(e: BlockchainInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl,
      it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR).toAirstreamVar
    val open = HtmlButtonElement.withTextLabel("basic/OpenEditor", _ =>
      fullInfo.displayControl.setFullscreen(BlockchainEditor(state, e.initial, e.difficultyZeros, e.isDisabledState.toAirstreamVar.signal)))
    val summary = p(cls := "blockchain-preview", role := "status",
      text <-- state.signal.map(_.validPrefixLength(e.difficultyZeros))
        .combineWith(laminarHelper.plaintextStringSignal("blockchainworkbook/blockProgress"))
        .map((count, template) => template.replace("{count}", count.toString).replace("{total}", e.initial.blocks.size.toString)
          .replace("{zeros}", e.difficultyZeros.toString)))
    AtomarLineRendering.cardLine(e, List(ElementCard(e.title, summary),
      ElementCard(it.evadid.core.datastructures.language.LanguageMapContentId("basic/openEditor"), open.getDomElement())))
  }
}
