package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.facades.BrowserLoading
import org.scalajs.dom

/** Local assets: tabs remain usable without third-party image hosts. */
object EvaLanguageIcons {
  private def asset(path: String): ReactiveSvgElement[dom.SVGSVGElement] = svg.svg(
    svg.cls := "eva-editor__language-icon", svg.viewBox := "0 0 24 24",
    svg.image(svg.xlinkHref := new dom.URL(path, BrowserLoading.siteRoot).href, svg.width := "24", svg.height := "24"))

  def python: ReactiveSvgElement[dom.SVGSVGElement] = asset("resources/icons/python.svg")
  def snap: ReactiveSvgElement[dom.SVGSVGElement] = asset("resources/programs/20260704Snap/src/snap_logo_sm.png")
}
