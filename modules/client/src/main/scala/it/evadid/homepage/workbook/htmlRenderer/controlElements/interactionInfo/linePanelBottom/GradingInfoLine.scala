package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.linePanelBottom

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.abstractions.WorkbookInteractionUntyped
import it.evadid.workbook.meta.InteractionVariableGradingReporter

case class GradingInfoLine(interaction: WorkbookInteractionUntyped, gradingReporter: InteractionVariableGradingReporter[?, ?, ?]) extends HtmlAppElement{


  override def getDomElement(): L.Element = div(
    "some stuff about interaction"
  )
}
