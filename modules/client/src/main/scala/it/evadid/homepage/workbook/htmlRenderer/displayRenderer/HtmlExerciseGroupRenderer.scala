package it.evadid.homepage.workbook.htmlRenderer.displayRenderer

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.structure.ExerciseGroup

object HtmlExerciseGroupRenderer extends LineBasedRenderingFactory[ExerciseGroup] {
  override protected def createRendering(fullInfo: FullInfo, workbookElement: ExerciseGroup): AtomarLineRendering =
    AtomarLineRendering.basicLine(fullInfo, workbookElement, div(
      cls := "workbook-exercise-group",
      workbookElement.elements.map(element => div(HtmlRenderFactory.render(fullInfo, element).rendering.getDomElement()))
    ))
}
