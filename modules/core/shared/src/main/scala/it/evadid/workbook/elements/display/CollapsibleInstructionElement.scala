package it.evadid.workbook.elements.display

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookElement}
import it.evadid.workbook.serialization.WorkbookElementFactory.NoRefsElementFactory
import it.evadid.workbook.serialization.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

case class CollapsibleInstructionElement(override val elementId: String, titleLabel: LanguageMapContentId, bodyContent: LanguageMapContentId, initiallyCollapsed: Boolean = true) extends WorkbookDisplayElement {
  override val associatedFactory: WorkbookElementFactory[CollapsibleInstructionElement] = CollapsibleInstructionElement.factory

}

object CollapsibleInstructionElement {
  val factory: NoRefsElementFactory[CollapsibleInstructionElement] = new NoRefsElementFactory[CollapsibleInstructionElement]() {

    override lazy val elementMapAndOrderForConstructorLike: Map[Int, Seq[VariableDisplayConfig]] =
      Map(
        0 -> List(
          VariableDisplayConfig("elementId", true)),
        1 -> List(
          VariableDisplayConfig("initiallyCollapsed", false),
          VariableDisplayConfig("bodyContent", false)
        )
      )
    override def toSerializableElement(element: CollapsibleInstructionElement): WorkbookElementSerializable = 
      toFactoryBase(element).withElementAddedAs("titleLabel", element.titleLabel).withElementAddedAs("bodyContent", element.bodyContent)
        .withElementAddedAs("initiallyCollapsed", element.initiallyCollapsed)

    override def fromSerializedElement(f: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): CollapsibleInstructionElement = {
      CollapsibleInstructionElement(
        f.elementId,
        f.getElementAs[LanguageMapContentId]("titleLabel"),
        f.getElementAs[LanguageMapContentId]("bodyContent"),
        f.getOptionalElementAs[Boolean]("initiallyCollapsed", true))
    }
  }

}
