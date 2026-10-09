package it.evadid.homepage.webElements.editor.plot

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.elements.interactionElements.plot.CoordinatePlotInteraction
import it.evadid.workbook.model.plot.*

/** Inline editor: numeric controls also make the graph usable without a mouse. */
case class CoordinatePlotEditor(config: CoordinatePlotInteraction, answer: Var[PlotAnswer], locked: Var[Boolean]) extends HtmlAppElement {
  private def textFor(key: String) = laminarHelper.plaintextStringSignal(s"digitalWorkbooks/$key")
  private val xDraft = Var("")
  private val yDraft = Var("")
  private def parsePoint(x: String, y: String): Option[PlotPoint] =
    for (px <- PlotAnswer.parseNumber(x).filter(config.xAxis.contains); py <- PlotAnswer.parseNumber(y).filter(config.yAxis.contains))
      yield PlotPoint(px, py)
  private val draft = xDraft.signal.combineWith(yDraft.signal).map((x, y) => parsePoint(x, y))
  private def px(x: Double): Double = 60 + config.xAxis.fraction(x) * 520
  private def py(y: Double): Double = 320 - config.yAxis.fraction(y) * 300
  private def number(value: Double): String = BigDecimal(value).bigDecimal.stripTrailingZeros.toPlainString
  private val graph = svg.svg(svg.viewBox := "0 0 640 380", svg.cls := "coordinate-plot__graph",
    svg.titleTag(text <-- laminarHelper.plaintextStringSignal(config.title)),
    config.xAxis.ticks.map(x => svg.g(
      svg.line(svg.cls := "coordinate-plot__grid", svg.x1 := px(x).toString, svg.x2 := px(x).toString, svg.y1 := "20", svg.y2 := "320"),
      svg.text(svg.cls := "coordinate-plot__tick-x", svg.x := px(x).toString, svg.y := "342", number(x)))),
    config.yAxis.ticks.map(y => svg.g(
      svg.line(svg.cls := "coordinate-plot__grid", svg.x1 := "60", svg.x2 := "580", svg.y1 := py(y).toString, svg.y2 := py(y).toString),
      svg.text(svg.cls := "coordinate-plot__tick-y", svg.x := "50", svg.y := (py(y) + 5).toString, number(y)))),
    svg.polyline(svg.cls := "coordinate-plot__axes", svg.points := "60,20 60,320 580,320"),
    svg.text(svg.cls := "coordinate-plot__label-x", svg.x := "320", svg.y := "370", text <-- laminarHelper.plaintextStringSignal(config.xLabel)),
    children <-- answer.signal.map(a => {
      val line = if (a.connected && a.points.size >= 2) List(svg.polyline(svg.cls := "coordinate-plot__curve",
        svg.points := a.points.sortBy(_.x).map(p => s"${px(p.x)},${py(p.y)}").mkString(" "))) else Nil
      line ++ a.points.map(p => svg.circle(svg.cls := "coordinate-plot__point", svg.cx := px(p.x).toString, svg.cy := py(p.y).toString,
        svg.titleTag(s"${number(p.x)}, ${number(p.y)}")))
    }))

  override def getDomElement(): Element = fieldSet(cls := "coordinate-plot",
    legend(text <-- laminarHelper.plaintextStringSignal(config.title)),
    p(text <-- textFor("plotInstructions")),
    div(cls := "coordinate-plot__axis-label", text <-- laminarHelper.plaintextStringSignal(config.yLabel)), graph,
    div(cls := "coordinate-plot__controls",
      label(span(text <-- laminarHelper.plaintextStringSignal(config.xLabel)), input(typ := "text", disabled <-- locked.signal,
        controlled(value <-- xDraft.signal, onInput.mapToValue --> xDraft.writer))),
      label(span(text <-- laminarHelper.plaintextStringSignal(config.yLabel)), input(typ := "text", disabled <-- locked.signal,
        controlled(value <-- yDraft.signal, onInput.mapToValue --> yDraft.writer))),
      button(typ := "button", text <-- textFor("plotAdd"),
        disabled <-- draft.combineWith(answer.signal, locked.signal).map((p, a, l) => l || p.isEmpty ||
          (a.points.size >= PlotAnswer.maxPoints && !p.exists(point => a.points.exists(_.x == point.x)))),
        onClick --> (_ => if (!locked.now()) parsePoint(xDraft.now(), yDraft.now()).foreach(p => answer.update(config.put(_, p))))),
      label(cls := "coordinate-plot__connect", input(typ := "checkbox", disabled <-- locked.signal,
        controlled(checked <-- answer.signal.map(_.connected), onInput.mapToChecked --> (value =>
          if (!locked.now()) answer.update(_.copy(connected = value))))), span(text <-- textFor("plotConnect")))),
    p(role := "status", text <-- textFor("plotRange").map(_.replace("{xmin}", number(config.xAxis.min)).replace("{xmax}", number(config.xAxis.max))
      .replace("{ymin}", number(config.yAxis.min)).replace("{ymax}", number(config.yAxis.max)))),
    div(cls := "coordinate-plot__table", table(
      caption(text <-- textFor("plotPoints")),
      thead(tr(th(text <-- laminarHelper.plaintextStringSignal(config.xLabel)), th(text <-- laminarHelper.plaintextStringSignal(config.yLabel)),
        th(text <-- textFor("plotAction")))),
      tbody(children <-- answer.signal.map(_.points.sortBy(_.x).map(p => tr(td(number(p.x)), td(number(p.y)),
        td(button(typ := "button", disabled <-- locked.signal, text <-- textFor("plotRemove"),
          onClick --> (_ => if (!locked.now()) answer.update(_.remove(p.x))))))))))))
}
