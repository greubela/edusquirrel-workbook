package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.pixel

import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.StringAsIsCodec
import com.raquo.laminar.keys.HtmlAttr
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.pixel.BinaryPixelInteraction
import it.evadid.workbook.model.pixel.*
import it.evadid.workbook.interaction.sync.UpdateImportance

case object BinaryPixelRenderer extends LineBasedRenderingFactory[BinaryPixelInteraction] {
  private val headerScope = new HtmlAttr[String]("scope", StringAsIsCodec)
  override protected def createRendering(e: BinaryPixelInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR).toAirstreamVar
    val disabledSignal = e.isDisabledState.toAirstreamVar.signal
    def textFor(key: String): Signal[String] = laminarHelper.plaintextStringSignal(s"digitalWorkbooks/$key")
    def grid(image: Signal[BinaryPixelImage], editable: Boolean): Element = div(cls := "pixel-grid-scroll", table(
      cls := "pixel-grid", caption(text <-- textFor(if editable then "pixelCanvas" else "pixelTarget")),
      thead(tr(th(), (0 until e.initial.columns).map(c => th(headerScope := "col", (c + 1).toString)))),
      tbody((0 until e.initial.rows).map(r => tr(th(headerScope := "row", (r + 1).toString),
        (0 until e.initial.columns).map { c =>
          val position = PixelPosition(r, c)
          val active = image.map(_.at(position))
          val label = textFor("pixelCoordinate").map(_.replace("{row}", (r + 1).toString).replace("{column}", (c + 1).toString))
          td(if editable then button(typ := "button", cls := "pixel-cell", cls.toggle("pixel-cell--on") <-- active,
            aria.label <-- label, aria.pressed <-- active.map(_.toString), disabled <-- disabledSignal,
            text <-- active.map(b => if b then "1" else "0"), onClick --> (_ => state.update(e.toggle(_, position))))
          else span(cls := "pixel-cell", cls.toggle("pixel-cell--on") <-- active, text <-- active.map(b => if b then "1" else "0")))
        }
      )))
    ))
    val dom = fieldSet(cls := "pixel-interaction",
      legend(text <-- laminarHelper.plaintextStringSignal(e.title)),
      p(text <-- textFor("pixelHelp")),
      div(cls := "pixel-canvases", grid(state.signal, true), e.expected.toList.map(target => grid(Val(target), false))),
      div(cls := "pixel-actions",
        button(typ := "button", disabled <-- disabledSignal, text <-- textFor("pixelReset"), onClick --> (_ => state.set(e.initial))),
        e.presets.map(preset => button(typ := "button", disabled <-- disabledSignal,
          text <-- laminarHelper.plaintextStringSignal(preset.label), onClick --> (_ => state.set(preset.image))))),
      e.probes.map(probe => p(cls := "pixel-probe", role := "status", aria.live := "polite",
        span(text <-- laminarHelper.plaintextStringSignal(probe.label)), ": ",
        span(text <-- state.signal.map(image => (probe.activeCount(image), probe.activates(image)))
          .combineWith(textFor("pixelProbeResult")).map { (count, output, template) =>
            template.replace("{count}", count.toString).replace("{threshold}", probe.threshold.toString)
              .replace("{output}", if output then "1" else "0")
          }))),
      p(cls := "pixel-feedback", role := "status", aria.live := "polite",
        text <-- state.signal.flatMapSwitch(image => e.matchingPixels(image) match {
          case Some(count) => textFor("pixelProgress").map(_.replace("{count}", count.toString).replace("{total}", image.pixels.size.toString))
          case None => textFor("pixelUngraded")
        }))
    )
    AtomarLineRendering.basicLine(e, dom)
  }
}
