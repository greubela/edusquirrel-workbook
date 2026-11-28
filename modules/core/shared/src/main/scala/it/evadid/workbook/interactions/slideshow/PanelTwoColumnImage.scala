package it.evadid.workbook.interactions.slideshow

import it.evadid.workbook.serialization.WorkbookElementFactory.constructorLayout

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookElement}
import it.evadid.workbook.elements.display.WorkbookImageElement
import it.evadid.workbook.serialization.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}

case class PanelTwoColumnImage(
                                override val elementId: String,
                                image: WorkbookImageElement,
                                leftLabel: LanguageMapContentId,
                                rightLabel: LanguageMapContentId,
                                leftBody: LanguageMapContentId,
                                rightBody: LanguageMapContentId
                              ) extends WorkbookDisplayElement derives upickle.default.ReadWriter {
  override val associatedFactory: WorkbookElementFactory[PanelTwoColumnImage] = PanelTwoColumnImage.factory
  override lazy val childrenOfThisElement: Seq[WorkbookElement] = List(image)
}

object PanelTwoColumnImage {
  val factory: WorkbookElementFactory[PanelTwoColumnImage] = new WorkbookElementFactory[PanelTwoColumnImage] {
    override lazy val elementMapAndOrderForConstructorLike = constructorLayout("elementId", "image", "leftLabel", "rightLabel", "leftBody", "rightBody")

    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = Set(element.getElementAs[WorkbookElementReference]("image").referencedId)

    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): PanelTwoColumnImage =
      PanelTwoColumnImage(element.elementId, element.getAndResolveWorkbookElement("image", parsedElements), element.getElementAs[LanguageMapContentId]("leftLabel"), element.getElementAs[LanguageMapContentId]("rightLabel"), element.getElementAs[LanguageMapContentId]("leftBody"), element.getElementAs[LanguageMapContentId]("rightBody"))

    override def toSerializableElement(element: PanelTwoColumnImage): WorkbookElementSerializable =
      toFactoryBase(element).withElementAddedAs[WorkbookElementReference]("image", element.image.asRef).withElementAddedAs("leftLabel", element.leftLabel).withElementAddedAs("rightLabel", element.rightLabel).withElementAddedAs("leftBody", element.leftBody).withElementAddedAs("rightBody", element.rightBody)
  }

}
