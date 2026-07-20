package it.evadid.homepage.workbook.legacy.interactionPlugins.gpt

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.homepage.control.model.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import org.scalajs.dom.{SVGLinearGradientElement, SVGSVGElement}

case class HtmlGptGrader(textInteraction: WorkbookInteractionElement[String]) extends HtmlAppElement {

  private val submitButton = HtmlButtonElement.withSvgContent(HtmlGptGrader.gradingButtonSvg, event => {


    println("grading not implemented yet :( ")

    ??? // todo 
    /*val llmResponse = AccessLLM("https://ypcgzj23.trafficplex.cloud/chat").sendRequest(
      MessengerChatCompletionRequest("write a short poem about a raven", MessengerModel(List()))
    )
    println("started streamed response: >>>" + llmResponse + "<<<")*/

  })

  override def getDomElement(): L.Element = submitButton.getDomElement()
}

object HtmlGptGrader {

  private def createGradingGradient(id: String): ReactiveSvgElement[SVGLinearGradientElement] =
    svg.linearGradient(
      svg.idAttr := id,
      svg.x1 := "4",
      svg.x2 := "20",
      svg.y1 := "0",
      svg.y2 := "0",
      svg.gradientUnits := "userSpaceOnUse",
      svg.stop(
        svg.offsetAttr := "0",
        svg.stopColor := "#00ff00",
      ),
      svg.stop(
        svg.offsetAttr := "0.5",
        svg.stopColor := "#ffff00",
      ),
      svg.stop(
        svg.offsetAttr := "1",
        svg.stopColor := "#ff0000",
      )
    )

  def gradingButtonSvg: ReactiveSvgElement[SVGSVGElement] = {
    svg.svg(
      createGradingGradient("gradient-fill-show"),
      svg.cls := "button-grading button-grading-start",
      svg.viewBox := "0 0 24 24",
      svg.fill := "none",
      svg.path(
        svg.cls := "button-borderpath",
        svg.d := "M3 12C3 4.5885 4.5885 3 12 3C19.4115 3 21 4.5885 21 12C21 19.4115 19.4115 21 12 21C4.5885 21 3 19.4115 3 12Z"
      ),
      svg.path(
        svg.d := "M12 8L12 16",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      ),
      svg.path(
        svg.d := "M15 11L12.087 8.08704V8.08704C12.039 8.03897 11.961 8.03897 11.913 8.08704V8.08704L9 11",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      )
    )
  }


}