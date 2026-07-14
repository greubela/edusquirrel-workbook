package it.evadid.homepage.workbook.legacy.htmlElements

import com.raquo.laminar.api.L.Element
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}

/** Wraps pre-built Laminar DOM as a workbook interaction (used during migration from legacy workbooks). */
case class HtmlEmbeddedDomInteraction(override val id: String, domElement: Element) extends WorkbookInteractionElement[String] {
  override val defaultValue: String = ""
  override val serializer: Serializer[String] = Serializer.stringIO
  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
}
