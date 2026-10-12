package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.content.DigitalWorkbookCatalog

/** The catalogue uses the same localized Laminar signals as the login and app shell.
  * Links open the existing entry pages, which load their own editor dependencies
  * and restore the current session. Browsing itself does not require login.
  */
case class HtmlSelectWorkbookElement() extends HtmlAppElement {
  private def textFor(key: String): Signal[String] = laminarHelper.plaintextStringSignal(key)

  private def card(entry: DigitalWorkbookCatalog.Entry): Element = {
    val imageUrl = if (entry.image.startsWith("https://")) entry.image
      else fullInfo.contentControl.fileFactory.relativeToTechnicalResources(entry.image).asUrlString
    figure(
      cls := "workbook-catalog-card",
      a(
        cls := "workbook-catalog-link",
        href := entry.target,
        target := (if (entry.isExternal) "_blank" else "_self"),
        rel := (if (entry.isExternal) "noopener noreferrer" else ""),
        img(src := imageUrl, alt <-- textFor(entry.titleKey)),
        div(
          cls := "workbook-catalog-caption",
          h3(text <-- textFor(entry.titleKey)),
          p(cls := "workbook-catalog-author", entry.author),
          p(cls := "workbook-catalog-description", text <-- textFor(entry.descriptionKey)),
          span(cls := "workbook-catalog-status", text <-- textFor(
            if (entry.inProgress) "workbookSelection/inProgress"
            else if (entry.isExternal) "workbookSelection/external"
            else "workbookSelection/standalone"))
        )
      )
    )
  }

  private lazy val domElement: Element = sectionTag(
    cls := "workbook-catalog",
    div(cls := "workbook-catalog-intro", p(text <-- textFor("workbookSelection/introduction"))),
    div(cls := "workbook-catalog-heading",
      h2(text <-- textFor("workbookSelection/digitalTitle")),
      p(text <-- textFor("workbookSelection/digitalDescription"))),
    div(cls := "workbook-catalog-grid", DigitalWorkbookCatalog.entries.filter(_.native.nonEmpty).map(card)),
    div(cls := "workbook-catalog-heading",
      h2(text <-- textFor("workbookSelection/standaloneTitle")),
      p(text <-- textFor("workbookSelection/standaloneDescription"))),
    div(cls := "workbook-catalog-grid", DigitalWorkbookCatalog.entries.filter(_.native.isEmpty).map(card)),
    p(cls := "workbook-catalog-owner", text <-- textFor("workbookSelection/owner"))
  )

  override def getDomElement(): Element = domElement
}
