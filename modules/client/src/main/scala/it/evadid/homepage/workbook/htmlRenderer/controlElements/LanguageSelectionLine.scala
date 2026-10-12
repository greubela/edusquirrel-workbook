package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.elements.structureElements.Workbook

private case class LanguageSelectionLine() extends HtmlAppElement {

  private def createDomForLanguageSelectionFlag(currentLanguage: HumanLanguage): Element = div(
    onClick --> { _ => fullInfo.usageControl.changeLanguage(currentLanguage) },
    LanguageSelectionLine.flagImgMap(30)(currentLanguage)
  )


  private val domElement = div(
    cls := "select-language-line",
    children <-- fullInfo.signals.availableLanguages.map(_.map(createDomForLanguageSelectionFlag))
  )

  override def getDomElement(): L.Element = domElement

}

object LanguageSelectionLine {

  def flagImgMap(width: Double): Map[HumanLanguage, Element] = Map(
    AppLanguage.German -> deFlag(width),
    AppLanguage.English -> enFlag(width),
    AppLanguage.Spanish -> esFlag(width),
    AppLanguage.Danish -> dkFlag(width),
    AppLanguage.Russian -> ruFlag(width),
    AppLanguage.Ukrainian -> ukFlag(width),
    AppLanguage.Turkish -> trFlag(width),
    AppLanguage.French -> frFlag(width)
  )

  private def esFlag(width: Double): Element = {
    img(
      src := HtmlFullWorkbookApp.fullInfo.contentControl.fileFactory.relativeToTechnicalResources("/img/flags/esFlag.svg").asUrlString,
      widthAttr := width.toInt,
      heightAttr := (width / 3 * 2).toInt,
    )
  }

  private def dkFlag(width: Double): Element =
    svg.svg(
      svg.xmlns := "http://www.w3.org/2000/svg",
      svg.width := s"$width",
      svg.height := s"${width / 37 * 28}",
      svg.viewBox := "0 0 37 28",

      svg.path(
        svg.cls := "flag-fill-british-red",
        svg.d := "M0,0H37V28H0Z"
      ),

      svg.path(
        svg.cls := "flag-stroke-white flag-stroke-width-4",
        svg.d := "M0,14h37M14,0v28"
      )
    )

  private def ukFlag(width: Double): Element = {
    svg.svg(
      svg.xmlns := "http://www.w3.org/2000/svg",
      svg.width := s"$width",
      svg.height := s"${width / 3 * 2}",
      svg.viewBox := "0 0 1200 800",
      svg.rect(
        svg.cls := "flag-fill-ukrainian-blue",
        svg.width := "1200",
        svg.height := "800",
      ),

      svg.rect(
        svg.cls := "flag-fill-ukrainian-yellow",
        svg.width := "1200",
        svg.height := "400",
        svg.y := "400",
      )
    )
  }

  private def trFlag(width: Double): Element =
    svg.svg(
      svg.xmlns := "http://www.w3.org/2000/svg",
      svg.width := s"$width",
      svg.height := s"${width / 4 * 3}",
      svg.viewBox := "0 -30000 90000 60000",

      svg.path(
        svg.cls := "flag-fill-turkish-red",
        svg.d := "m0-30000h90000v60000H0z"
      ),

      svg.path(
        svg.cls := "flag-fill-white",
        svg.d := "m41750 0 13568-4408-8386 11541V-7133l8386 11541zm925 8021a15000 15000 0 1 1 0-16042 12000 12000 0 1 0 0 16042z"
      )
    )

  private def ruFlag(width: Double): Element =
    svg.svg(
      svg.xmlns := "http://www.w3.org/2000/svg",
      svg.viewBox := "0 0 9 6",
      svg.width := s"$width",
      svg.height := s"${width / 3 * 2}",

      svg.rect(
        svg.cls := "flag-fill-white",
        svg.width := "9",
        svg.height := "3"
      ),

      svg.rect(
        svg.cls := "flag-fill-russian-red",
        svg.y := "3",
        svg.width := "9",
        svg.height := "3"
      ),

      svg.rect(
        svg.cls := "flag-fill-russian-blue",
        svg.y := "2",
        svg.width := "9",
        svg.height := "2"
      )
    )

  private def frFlag(width: Double): Element =
    svg.svg(
      svg.xmlns := "http://www.w3.org/2000/svg",
      svg.viewBox := "0 0 900 600",
      svg.width := s"$width",
      svg.height := s"${width / 3 * 2}",
      svg.path(
        svg.cls := "flag-fill-french-red",
        svg.d := "M0 0h900v600H0"
      ),
      svg.path(
        svg.cls := "flag-fill-white",
        svg.d := "M0 0h600v600H0"
      ),
      svg.path(
        svg.cls := "flag-fill-french-blue",
        svg.d := "M0 0h300v600H0"
      )
    )

  private def enFlag(width: Double): Element =
    svg.svg(
      svg.xmlns := "http://www.w3.org/2000/svg",
      svg.viewBox := "0 0 50 30",
      svg.width := s"$width",
      svg.height := s"${width / 10 * 6}",

      svg.clipPathTag(
        svg.idAttr := "t",
        svg.path(
          svg.d := "M25,15h25v15zv15h-25zh-25v-15zv-15h25z"
        )
      ),

      svg.path(
        svg.cls := "flag-fill-british-blue",
        svg.d := "M0,0v30h50v-30z",
      ),

      svg.path(
        svg.cls := "flag-stroke-white flag-stroke-width-6",
        svg.d := "M0,0 50,30M50,0 0,30",
      ),

      svg.path(
        svg.cls := "flag-stroke-british-red flag-stroke-width-4",
        svg.d := "M0,0 50,30M50,0 0,30",
        svg.clipPathAttr := "url(#t)",
      ),

      svg.path(
        svg.cls := "flag-fill-british-red flag-stroke-white flag-stroke-width-2",
        svg.d := "M-1 11h22v-12h8v12h22v8h-22v12h-8v-12h-22z",
      )
    )

  private def deFlag(width: Double): Element = svg.svg(
    svg.cls := "language-flag",
    svg.viewBox := "0 0 5 3",
    svg.width := s"$width",
    svg.height := s"${width / 10 * 6}",
    svg.rect(
      svg.cls := "flag-fill-black",
      svg.x := "0",
      svg.y := "0",
      svg.width := "5",
      svg.height := "3",
    ),
    svg.rect(
      svg.cls := "flag-fill-german-red",
      svg.x := "0",
      svg.y := "1",
      svg.width := "5",
      svg.height := "1",
    ),
    svg.rect(
      svg.cls := "flag-fill-german-yellow",
      svg.x := "0",
      svg.y := "2",
      svg.width := "5",
      svg.height := "1",
    )
  )
}
