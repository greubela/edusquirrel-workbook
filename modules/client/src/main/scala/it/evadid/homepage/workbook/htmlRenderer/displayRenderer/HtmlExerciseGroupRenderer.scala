package it.evadid.homepage.workbook.htmlRenderer.displayRenderer

import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.structureElements.ExerciseGroup

object HtmlExerciseGroupRenderer extends LineBasedRenderingFactory[ExerciseGroup] {
  override protected def createRendering(workbookElement: ExerciseGroup): AtomarLineRendering =
    AtomarLineRendering.basicLine(workbookElement, div(
      cls := "workbook-exercise-group",
      workbookElement.elements.map(element => div(HtmlRenderFactory.render(element).rendering.getDomElement()))
    ))
}
