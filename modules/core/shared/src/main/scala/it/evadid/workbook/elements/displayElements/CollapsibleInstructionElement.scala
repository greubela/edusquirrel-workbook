package it.evadid.workbook.elements.displayElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.WorkbookDisplayElement
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable

case class CollapsibleInstructionElement(override val elementId: String, titleLabel: LanguageMapContentId, bodyContent: LanguageMapContentId, initiallyCollapsed: Boolean = true) extends WorkbookDisplayElement {
  override val associatedFactory: SimpleWorkbookElementFactory[CollapsibleInstructionElement] = CollapsibleInstructionElement.factory

}

object CollapsibleInstructionElement {
  val factory: SimpleWorkbookElementFactory[CollapsibleInstructionElement] = new SimpleWorkbookElementFactory[CollapsibleInstructionElement]() {

    override def finishSerialization(baseElement: WorkbookElementSerializable, e: CollapsibleInstructionElement): WorkbookElementSerializable = {
      baseElement.
        withContentIdAdded("titleLabel", e.titleLabel)
        .withContentIdAdded("bodyContent", e.bodyContent)
        .withElementAdded("initiallyCollapsed", e.initiallyCollapsed.toString)
    }

    override def finishDeserialization(f: WorkbookElementSerializable): CollapsibleInstructionElement = {
      CollapsibleInstructionElement(
        f.elementId,
        f.getElementAsContentId("titleLabel"),
        f.getElementAsContentId("bodyContent"),
        f.getElement("initiallyCollapsed").toBoolean)
    }
  }
}
