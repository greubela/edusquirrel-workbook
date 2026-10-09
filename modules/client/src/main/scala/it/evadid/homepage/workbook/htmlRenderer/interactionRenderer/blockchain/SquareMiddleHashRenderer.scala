package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.blockchain

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.blockchain.*
import it.evadid.workbook.model.blockchain.SquareMiddleHash
import it.evadid.workbook.interaction.sync.UpdateImportance

case object SquareMiddleHashRenderer extends LineBasedRenderingFactory[SquareMiddleHashInteraction] {
  override protected def createRendering(e: SquareMiddleHashInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR).toAirstreamVar
    val disabledSignal = e.isDisabledState.toAirstreamVar.signal
    def textFor(key: String): Signal[String] = laminarHelper.plaintextStringSignal(s"blockchainworkbook/$key")
    def numberField(first: Boolean): Element = label(
      span(text <-- textFor(if first then "firstInput" else "secondInput")),
      input(typ := "text", maxLength := SquareMiddleHash.maxInputDigits,
        disabled <-- disabledSignal,
        controlled(value <-- state.signal.map(a => if first then a.first else a.second),
          onInput.mapToValue --> (value => state.update(a => if first then a.copy(first = value) else a.copy(second = value))))))
    val taskKey = e.task match {
      case ExploreSquareMiddleHash() => "exploreTask"
      case FindHashCollision() => "collisionTask"
      case _: FindHashPreimage => "preimageTask"
    }
    val target = e.task match { case FindHashPreimage(value) => value; case _ => "" }
    val dom = fieldSet(cls := "square-middle-hash",
      legend(text <-- laminarHelper.plaintextStringSignal(e.title)),
      p(text <-- textFor(taskKey).map(_.replace("{target}", target))),
      div(cls := "hash-controls", numberField(true),
        Option.when(!e.task.isInstanceOf[FindHashPreimage])(numberField(false)),
        button(typ := "button", disabled <-- disabledSignal, text <-- textFor("reset"),
          onClick --> (_ => state.set(e.initial)))),
      div(cls := "hash-results", table(
        caption(text <-- textFor("calculation")),
        thead(tr(th(text <-- textFor("input")), th(text <-- textFor("square")), th(text <-- textFor("hash")))),
        tbody(children <-- state.signal.map(a => e.results(a).map {
          case Some(result) => tr(td(result.input.toString), td(code(
            result.square.toString.take(result.start), mark(result.hash), result.square.toString.drop(result.start + 2))), td(code(result.hash)))
          case None => tr(td(colSpan := 3, text <-- textFor("invalid")))
        })))),
      p(cls := "hash-feedback", role := "status", aria.live := "polite",
        text <-- state.signal.map(a => if e.results(a).exists(_.isEmpty) then "invalid"
          else e.isCorrect(a) match {
            case None => "explorationSaved"
            case Some(true) => "correct"
            case Some(false) => "tryAgain"
          }).flatMapSwitch(textFor))
    )
    AtomarLineRendering.basicLine(e, dom)
  }
}
