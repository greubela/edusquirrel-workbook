package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.laminar.api.L.*


case class RenderingAsTitle(titleSignal: Element, level: Int) extends AtomarLineRendering {

  val isInteraction: Boolean = false

  private def normalizedLevel: Int = math.max(1, math.min(6, level))

  private def headingElement(content: String): Element = normalizedLevel match {
    case 1 => h1(content)
    case 2 => h2(content)
    case 3 => h3(content)
    case 4 => h4(content)
    case 5 => h5(content)
    case _ => h6(content)
  }

  override lazy val render: Element = div(
    cls := lineCssStr + s" container-title container-title-level-$normalizedLevel",
    titleSignal
  )

  override lazy val elementsWithoutContainer: Signal[List[Element]] = Var(List(titleSignal)).signal
}

