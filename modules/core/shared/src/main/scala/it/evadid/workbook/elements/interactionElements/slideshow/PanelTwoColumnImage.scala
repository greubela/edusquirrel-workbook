package it.evadid.workbook.elements.interactionElements.slideshow

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookElement}
import it.evadid.workbook.elements.displayElements.WorkbookImageElement
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}

case class PanelTwoColumnImage(
                                override val elementId: String,
                                image: WorkbookImageElement,
                                leftLabel: LanguageMapContentId,
                                rightLabel: LanguageMapContentId,
                                leftBody: LanguageMapContentId,
                                rightBody: LanguageMapContentId
                              ) extends WorkbookDisplayElement derives upickle.default.ReadWriter {
  override val associatedFactory: WorkbookElementFactory[PanelTwoColumnImage] = PanelTwoColumnImage.factory
  override lazy val childrenOfThisElement: List[WorkbookElement] = List(image)
}

object PanelTwoColumnImage {
  val factory: WorkbookElementFactory[PanelTwoColumnImage] = new WorkbookElementFactory[PanelTwoColumnImage] {
    override lazy val elementMapAndOrderForConstructorLike = Map(0 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("elementId", true)), 1 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("image", false)), 2 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("leftLabel", false)), 3 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("rightLabel", false)), 4 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("leftBody", false)), 5 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("rightBody", false)))

    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = Set(element.getElementAs[WorkbookElementReference]("image").referencedId)

    override def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable): Seq[WorkbookElementSerializable] = Seq.empty

    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): PanelTwoColumnImage =
      PanelTwoColumnImage(element.elementId, element.getAndResolveWorkbookElement("image", parsedElements), element.getElementAs[LanguageMapContentId]("leftLabel"), element.getElementAs[LanguageMapContentId]("rightLabel"), element.getElementAs[LanguageMapContentId]("leftBody"), element.getElementAs[LanguageMapContentId]("rightBody"))

    override def toSerializableElement(element: PanelTwoColumnImage): WorkbookElementSerializable =
      toFactoryBase(element).withElementAddedAs[WorkbookElementReference]("image", element.image.asRef).withElementAddedAs("leftLabel", element.leftLabel).withElementAddedAs("rightLabel", element.rightLabel).withElementAddedAs("leftBody", element.leftBody).withElementAddedAs("rightBody", element.rightBody)
  }

}
