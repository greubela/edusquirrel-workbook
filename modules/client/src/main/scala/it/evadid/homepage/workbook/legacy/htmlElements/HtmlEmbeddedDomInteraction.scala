package it.evadid.homepage.workbook.legacy.htmlElements

import com.raquo.laminar.api.L.Element
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory

/** Wraps pre-built Laminar DOM as a workbook interaction (used during migration from legacy workbooks). */
case class HtmlEmbeddedDomInteraction(override val elementId: String, domElement: Element) extends WorkbookInteractionElement[String] {
  // TODO: A live Laminar DOM node closes over browser state and event handlers and therefore has no
  // faithful data representation; replace this legacy wrapper with declarative workbook elements.
  override val associatedFactory = WorkbookElementFactory.unsupportedFactory[HtmlEmbeddedDomInteraction](getClass.getSimpleName)
  override val defaultValue: String = ""
  override val serializerInteractionContent: Serializer[String] = Serializer.stringIO
  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
}
