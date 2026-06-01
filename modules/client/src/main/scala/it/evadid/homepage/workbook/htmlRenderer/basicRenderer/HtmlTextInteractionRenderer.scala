package it.evadid.homepage.workbook.htmlRenderer.basicRenderer

import com.raquo.laminar.api.L
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.model.interaction.WorkbookInteraction.TextInteractionBasic

object HtmlTextInteractionRenderer extends HtmlRenderFactory[TextInteractionBasic] {

  override protected def createDomElement(workbookElement: TextInteractionBasic): L.Element = ???
  
  
}
