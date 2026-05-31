package it.evadid.homepage.workbook.htmlRenderer.basicRenderer

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Element
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.model.abstractions.{WorkbookElement, WorkbookElementGroup}

object HtmlExerciseContainerRenderer extends HtmlRenderFactory[WorkbookElementGroup[WorkbookElement]] {

  private def level = 1 // todo: calculate correctly 

  private def isMainContainer = true // todo: fix 

  private val normalizedLevel: Int = math.max(1, math.min(6, level))

  private val clsString = if (isMainContainer) s"container-exercise style-vbox container-level-$normalizedLevel" else s"container-sub container-level-$normalizedLevel"

  private def renderChildren(container: WorkbookElement): List[Element] = {
    container.childrenOfThisElement.map(HtmlRenderFactory.renderWorkbookElement).map(_.getDomElement())
  }

  override protected def createDomElement(workbookElement: WorkbookElementGroup[WorkbookElement]): L.Element = L.div(
    //L.cls := "container-exercise style-vbox",
    // L.cls := s"container-level-$normalizedLevel",
    L.cls := clsString,
    L.children <-- L.Var(renderChildren(workbookElement)).signal
  )


  /* stolen from sub
  override def getDomElement(): L.Element = L.div(
    L.cls := "workbook-element container-sub style-vbox",
    L.cls := s"container-level-$normalizedLevel",
    L.children <-- children.map(_.map(_.getDomElement()))
  )*/
}
