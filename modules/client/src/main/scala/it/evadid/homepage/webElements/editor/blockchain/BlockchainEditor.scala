package it.evadid.homepage.webElements.editor.blockchain

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.basic.LabelSelection
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.interactions.blockchain.*
import org.scalajs.dom

case class BlockchainEditor(fullInfo: FullInfo, state: Var[TeachingChain], initial: TeachingChain, zeros: Int, locked: Signal[Boolean])
    extends HtmlAppElement {
  private val invalidNonceDrafts = Var(Map.empty[Int, String])
  private val mining = new BlockchainMiningController(state, zeros, callback => {
    val timer = dom.window.setTimeout(() => callback(), 0)
    () => dom.window.clearTimeout(timer)
  })
  private def textFor(key: String): Signal[String] = laminarHelper(fullInfo).plaintextStringSignal(s"blockchainworkbook/$key")
  private def blockCard(index: Int): Element = articleTag(
    cls := "blockchain-block",
    cls <-- state.signal.map(chain => if chain.validPrefixLength(zeros) > index then "blockchain-block--valid" else "blockchain-block--invalid"),
    h3(text <-- textFor("blockNumber").map(_.replace("{index}", index.toString))),
    label(span(text <-- textFor("blockData")), textArea(maxLength := TeachingChain.maxDataLength,
      disabled <-- locked, controlled(value <-- state.signal.map(_.blocks(index).data),
        onInput.mapToValue --> (value => {
          mining.cancel()
          state.update(chain => chain.update(index, chain.blocks(index).copy(data = value)))
        })))),
    label(span(text <-- textFor("blockNonce")), input(typ := "text", maxLength := 10, disabled <-- locked,
      aria.invalid <-- invalidNonceDrafts.signal.map(_.contains(index).toString),
      value <-- state.signal.combineWith(invalidNonceDrafts.signal).map((chain, drafts) => drafts.getOrElse(index, chain.blocks(index).nonce.toString)),
      onInput.mapToValue --> (value => {
        mining.cancel()
        TeachingChain.parseNonce(value) match {
          case Some(nonce) =>
            invalidNonceDrafts.update(_ - index)
            state.update(chain => chain.update(index, chain.blocks(index).copy(nonce = nonce)))
          case None => invalidNonceDrafts.update(_.updated(index, value))
        }
      }))),
    p(cls := "blockchain-error", role := "alert",
      text <-- LabelSelection(invalidNonceDrafts.signal, Seq("blockNonceInvalid"), textFor)(drafts =>
        Option.when(drafts.contains(index))("blockNonceInvalid"))),
    dl(dt(text <-- textFor("blockPrevious")), dd(code(cls := "blockchain-previous", text <-- state.signal.map(_.previousHash(index)))),
      dt(text <-- textFor("blockHash")), dd(code(cls := "blockchain-hash", text <-- state.signal.map(_.hashes(index))))),
    p(cls := "blockchain-block-status", role := "status", text <-- LabelSelection(state.signal, Seq("blockValid", "blockBlocked", "blockInvalid"),
      textFor)(chain => Some(if chain.validPrefixLength(zeros) > index then "blockValid"
        else if chain.proofs(zeros)(index) then "blockBlocked" else "blockInvalid"))),
    button(typ := "button", text <-- textFor("blockMine"),
      disabled <-- locked.combineWith(mining.running.signal, invalidNonceDrafts.signal)
        .map((disabled, busy, drafts) => disabled || busy || drafts.contains(index)),
      onClick --> (_ => mining.start(index))))

  override def getDomElement(): Element = div(cls := "blockchain-editor",
    onMountCallback(ctx => locked.changes.foreach(value => if value then mining.cancel())(using ctx.owner)),
    onUnmountCallback(_ => mining.cancel()),
    h2(text <-- textFor("blockEditorTitle")),
    p(text <-- textFor("blockEditorExplanation").map(_.replace("{zeros}", zeros.toString))),
    div(cls := "blockchain-actions",
      button(typ := "button", text <-- textFor("blockReset"), disabled <-- locked,
        onClick --> (_ => { mining.cancel(); invalidNonceDrafts.set(Map.empty); state.set(initial) })),
      button(typ := "button", text <-- textFor("blockStop"), disabled <-- locked.combineWith(mining.running.signal).map((disabled, busy) => disabled || !busy),
        onClick --> (_ => mining.cancel()))),
    p(cls := "blockchain-mining-status", role := "status", aria.live := "polite",
      text <-- LabelSelection.formatted(mining.message.signal.combineWith(mining.attempts.signal), BlockchainMiningController.messages, textFor)(
        (message, attempts) => Some(message -> Seq("attempts" -> attempts.toString)))),
    div(cls := "blockchain-blocks", initial.blocks.indices.toList.map(blockCard)))
  override def dismissOnOutsideClick: Boolean = false
}
