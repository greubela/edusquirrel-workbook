package it.evadid.workbook.elements.displayElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.NoRefsElementFactory
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

case class CollapsibleInstructionElement(override val elementId: String, titleLabel: LanguageMapContentId, bodyContent: LanguageMapContentId, initiallyCollapsed: Boolean = true) extends WorkbookDisplayElement derives ReadWriter{
  override val associatedFactory: WorkbookElementFactory[CollapsibleInstructionElement] = CollapsibleInstructionElement.factory

}

object CollapsibleInstructionElement {
  val factory: NoRefsElementFactory[CollapsibleInstructionElement] = new NoRefsElementFactory[CollapsibleInstructionElement]() {

    override lazy val elementMapAndOrderForConstructorLike: Map[Int, List[ConstructorLikeSerializer.VariableDisplayConfig]] =
      Map(
        0 -> List(
          VariableDisplayConfig("elementId", true)),
        1 -> List(
          VariableDisplayConfig("initiallyCollapsed", false),
          VariableDisplayConfig("bodyContent", false)
        )
      )
    override lazy val writerJsonRegularRefBased: Writer[CollapsibleInstructionElement] = {
      CollapsibleInstructionElement.derived$ReadWriter
    }

    override def fromSerializedElement(f: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): CollapsibleInstructionElement = {
      CollapsibleInstructionElement(
        f.elementId,
        f.getElementAs[LanguageMapContentId]("titleLabel"),
        f.getElementAs[LanguageMapContentId]("bodyContent"),
        f.getOptionalElementAs[Boolean]("initiallyCollapsed", true))
    }
  }

}
