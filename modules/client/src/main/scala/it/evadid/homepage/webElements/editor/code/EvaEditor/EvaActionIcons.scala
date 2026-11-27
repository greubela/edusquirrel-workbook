package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import org.scalajs.dom

/** Inline icons of Eva's actions: drawn from embedded paths, so they need no image host or asset request. */
object EvaActionIcons {
  private def icon(paths: String*): ReactiveSvgElement[dom.SVGSVGElement] = svg.svg(
    svg.cls := "eva-editor__action-icon", svg.viewBox := "0 0 24 24", svg.fill := "none", svg.stroke := "currentColor",
    svg.strokeWidth := "2", svg.strokeLineCap := "round", svg.strokeLineJoin := "round", aria.hidden := true,
    paths.map(path => svg.path(svg.d := path)))

  def run: ReactiveSvgElement[dom.SVGSVGElement] = icon("M7 4.5v15l12-7.5Z")
  def stop: ReactiveSvgElement[dom.SVGSVGElement] = icon("M6.5 6.5h11v11h-11Z")
  def download: ReactiveSvgElement[dom.SVGSVGElement] = icon("M12 4v11", "m7 10 5 5 5-5", "M5 19h14")
}
