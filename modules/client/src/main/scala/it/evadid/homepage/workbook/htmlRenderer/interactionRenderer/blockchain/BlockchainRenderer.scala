package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.blockchain

import it.evadid.workbook.interaction.state.InteractionSignals.*

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.editor.blockchain.BlockchainEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.blockchain.BlockchainInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR

case object BlockchainRenderer extends LineBasedRenderingFactory[BlockchainInteraction] {
  override protected def createRendering(fullInfo: FullInfo, e: BlockchainInteraction): AtomarLineRendering = {
    val state = fullInfo.interactions.boundVar(e, MAJOR)
    val summary = p(cls := "blockchain-preview", role := "status",
      text <-- state.signal.map(_.validPrefixLength(e.difficultyZeros))
        .combineWith(laminarHelper(fullInfo).plaintextStringSignal("blockchainworkbook/blockProgress"))
        .map((count, template) => template.replace("{count}", count.toString).replace("{total}", e.initial.blocks.size.toString)
          .replace("{zeros}", e.difficultyZeros.toString)))
    AtomarLineRendering.editorCardLine(fullInfo, e, ElementCard(fullInfo, e.title, summary),
      () => BlockchainEditor(fullInfo, state, e.initial, e.difficultyZeros, e.isDisabledState.toAirstreamVar.signal))
  }
}
