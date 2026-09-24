package it.evadid.workbook.elements.displayElements
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.WorkbookDisplayElement
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
case class CollapsibleInstructionElement(override val elementId: String, titleLabel: LanguageMapContentId, bodyContent: LanguageMapContentId, initiallyCollapsed: Boolean = true) extends WorkbookDisplayElement {
  override val associatedFactory = CollapsibleInstructionElement.factory

}
object CollapsibleInstructionElement {
  val factory = it.evadid.workbook.jsonFactory.WorkbookElementFactory.simple[CollapsibleInstructionElement](e => WorkbookElementSerializable(e.elementId, classOf[CollapsibleInstructionElement].getSimpleName, Map()).withContentIdAdded("titleLabel", e.titleLabel).withContentIdAdded("bodyContent", e.bodyContent).withElementAdded("initiallyCollapsed", e.initiallyCollapsed.toString), fromFactory)
 def fromFactory(f: WorkbookElementSerializable): CollapsibleInstructionElement = CollapsibleInstructionElement(f.elementId, f.getElementAsContentId("titleLabel"), f.getElementAsContentId("bodyContent"), f.getElementAsString("initiallyCollapsed").toBoolean) }
