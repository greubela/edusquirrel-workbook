package it.evadid.homepage.workbook.htmlRenderer.structureRenderer

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, RenderingAsContainerTitle}
import it.evadid.homepage.workbook.htmlRenderer.{HtmlRenderFactory, HtmlWorkbookElement}
import it.evadid.workbook.elements.structureElements.ExerciseContainer

object HtmlExerciseContainerRenderer extends HtmlRenderFactory[ExerciseContainer] {

  private def level = 1 // todo: calculate correctly 

  private def isMainContainer = true // todo: fix 

  private val normalizedLevel: Int = math.max(1, math.min(6, level))

  private val clsStringContainer = if (isMainContainer) s"container-exercise style-vbox container-level-$normalizedLevel" else s"container-sub container-level-$normalizedLevel"
  private val clsStringTitle = s"workbook-element container-title-level-$normalizedLevel"

  private def renderAllChildren(container: ExerciseContainer): List[Element] = {
    val title = AtomarLineRendering.exerciseContainerTitleLine(container.containerTitle).render
    val rest = container.childrenOfThisElement.map(HtmlRenderFactory.renderWorkbookElement).map(_.getDomElement())
    List(title) ++ rest
  }

  protected def createDomElement(workbookElement: ExerciseContainer): Element = div(
    //L.cls := "container-exercise style-vbox",
    // L.cls := s"container-level-$normalizedLevel",
    cls := clsStringContainer,
    children <-- Var(renderAllChildren(workbookElement)).signal
  )


  /* stolen from sub
  override def getDomElement(): L.Element = L.div(
    L.cls := "workbook-element container-sub style-vbox",
    L.cls := s"container-level-$normalizedLevel",
    L.children <-- children.map(_.map(_.getDomElement()))
  )*/

  override def renderAppElement(workbookElement: ExerciseContainer): HtmlWorkbookElement[ExerciseContainer, HtmlAppElement] = {

    val appElement = HtmlAppElement(createDomElement(workbookElement))
    HtmlWorkbookElement[ExerciseContainer, HtmlAppElement](fullInfo, workbookElement, appElement)

  }
}
