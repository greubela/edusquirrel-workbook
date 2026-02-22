package workbook.workbookHtmlElements.basic

import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

case class HtmlWorkbookTitleLine(workbookInfoVar: Var[WorkbookInfo], workbookTitle: LanguageMap[HumanLanguage]) extends HtmlWorkbookElement {

  private def selectLanguage(language: HumanLanguage): Unit = {
    workbookInfoVar.update(curInfo => curInfo.copy(config = curInfo.config.copy(currentWorkbookLanguage = language)))
  }

  private val flagElements: Map[HumanLanguage, Element] = Map(
    AppLanguage.English -> div(
      onClick --> { _ => selectLanguage(AppLanguage.English) },
      HtmlWorkbookTitleLine.enFlag(30)
    ),

  )



  private val domElement: Element = div(
    cls := "workbook-title-line",
    h1(
      child <-- workbookInfoVar.signal.map(_.languageStringFromMap(workbookTitle)),
    ),
    div(
      cls := "select-language-line",
      children <-- {
        workbookInfoVar.signal.map(_.availableLanguages.map(curLang => {
          val childElement: Element = HtmlWorkbookTitleLine.flagImgMap(30)(curLang)
          div(
            onClick --> { _ => selectLanguage(curLang) },
            child <-- Var(childElement).signal
          )
        }))
      }

    )
  )

  override def getDomElement(): Element = domElement


}

object HtmlWorkbookTitleLine {


  private def flagImgMap(width: Double): Map[HumanLanguage, Element] = Map(
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
      src := "../resources/img/flags/esFlag.svg",
      styleAttr := "width:" + width + "px; height:" + (width/3*2) + "px;",
    )
  }



  private def dkFlag(width: Double): Element =
    svg.svg(
      svg.xmlns := "http://www.w3.org/2000/svg",
      svg.width := width + "",
      svg.height := width / 37 * 28 + "",
      svg.viewBox := "0 0 37 28",

      svg.path(
        svg.fill := "#c8102e",
        svg.d := "M0,0H37V28H0Z"
      ),

      svg.path(
        svg.stroke := "#fff",
        svg.strokeWidth := "4",
        svg.d := "M0,14h37M14,0v28"
      )
    )

  private def ukFlag(width: Double): Element = {
    svg.svg(
      svg.xmlns := "http://www.w3.org/2000/svg",
      svg.width := width + "",
      svg.height := width / 3 * 2 + "",
      svg.viewBox := "0 0 1200 800",
      svg.rect(
        svg.width := "1200",
        svg.height := "800",
        svg.fill := "#0057B7"
      ),

      svg.rect(
        svg.width := "1200",
        svg.height := "400",
        svg.y := "400",
        svg.fill := "#FFD700"
      )
    )
  }

  private def trFlag(width: Double): Element =
    svg.svg(
      svg.xmlns := "http://www.w3.org/2000/svg",
      svg.width := width + "",
      svg.height := width / 3 * 4 + "",
      svg.viewBox := "0 -30000 90000 60000",

      svg.path(
        svg.fill := "#e30a17",
        svg.d := "m0-30000h90000v60000H0z"
      ),

      svg.path(
        svg.fill := "#fff",
        svg.d := "m41750 0 13568-4408-8386 11541V-7133l8386 11541zm925 8021a15000 15000 0 1 1 0-16042 12000 12000 0 1 0 0 16042z"
      )
    )

  private def ruFlag(width: Double): Element =
    svg.svg(
      svg.xmlns := "http://www.w3.org/2000/svg",
      svg.viewBox := "0 0 9 6",
      svg.width := width + "",
      svg.height := width / 3 * 2 + "",

      svg.rect(
        svg.fill := "#fff",
        svg.width := "9",
        svg.height := "3"
      ),

      svg.rect(
        svg.fill := "#d52b1e",
        svg.y := "3",
        svg.width := "9",
        svg.height := "3"
      ),

      svg.rect(
        svg.fill := "#0039a6",
        svg.y := "2",
        svg.width := "9",
        svg.height := "2"
      )
    )

  private def frFlag(width: Double): Element =
    svg.svg(
      svg.xmlns := "http://www.w3.org/2000/svg",
      svg.viewBox := "0 0 900 600",
      svg.width := width + "",
      svg.height := width / 3 * 2 + "",
      svg.path(
        svg.fill := "#CE1126",
        svg.d := "M0 0h900v600H0"
      ),
      svg.path(
        svg.fill := "#fff",
        svg.d := "M0 0h600v600H0"
      ),
      svg.path(
        svg.fill := "#002654",
        svg.d := "M0 0h300v600H0"
      )
    )

  private def enFlag(width: Double): Element =
    svg.svg(
      svg.xmlns := "http://www.w3.org/2000/svg",
      svg.viewBox := "0 0 50 30",
      svg.width := width + "",
      svg.height := width / 10 * 6 + "",

      svg.clipPathTag(
        svg.idAttr := "t",
        svg.path(
          svg.d := "M25,15h25v15zv15h-25zh-25v-15zv-15h25z"
        )
      ),

      svg.path(
        svg.d := "M0,0v30h50v-30z",
        svg.fill := "#012169"
      ),

      svg.path(
        svg.d := "M0,0 50,30M50,0 0,30",
        svg.stroke := "#fff",
        svg.strokeWidth := "6"
      ),

      svg.path(
        svg.d := "M0,0 50,30M50,0 0,30",
        svg.clipPathAttr := "url(#t)",
        svg.stroke := "#C8102E",
        svg.strokeWidth := "4"
      ),

      svg.path(
        svg.d := "M-1 11h22v-12h8v12h22v8h-22v12h-8v-12h-22z",
        svg.fill := "#C8102E",
        svg.stroke := "#FFF",
        svg.strokeWidth := "2"
      )
    )

  private def deFlag(width: Double): Element = svg.svg(
    svg.cls := "language-flag",
    svg.viewBox := "0 0 5 3",
    svg.width := width + "",
    svg.height := width / 10 * 6 + "",
    svg.rect(
      svg.x := "0",
      svg.y := "0",
      svg.width := "5",
      svg.height := "3",
      svg.fill := "#000"
    ),
    svg.rect(
      svg.x := "0",
      svg.y := "1",
      svg.width := "5",
      svg.height := "1",
      svg.fill := "#D00"
    ),
    svg.rect(
      svg.x := "0",
      svg.y := "2",
      svg.width := "5",
      svg.height := "1",
      svg.fill := "#FFCE00"
    )
  )

}
