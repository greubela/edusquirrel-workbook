package it.evadid.workbook.elements.displayElements
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.WorkbookDisplayElement
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
case class CollapsibleInstructionElement(override val elementId: String, titleLabel: LanguageMapContentId, bodyContent: LanguageMapContentId, initiallyCollapsed: Boolean = true) extends WorkbookDisplayElement {
 override def toSerializableType: WorkbookElementFactory = toFactoryBase.withContentIdAdded("titleLabel", titleLabel).withContentIdAdded("bodyContent", bodyContent).withElementAdded("initiallyCollapsed", initiallyCollapsed.toString)
}
object CollapsibleInstructionElement { def fromFactory(f: WorkbookElementFactory): CollapsibleInstructionElement = CollapsibleInstructionElement(f.elementId, f.getElementAsContentId("titleLabel"), f.getElementAsContentId("bodyContent"), f.getElementAsString("initiallyCollapsed").toBoolean) }
