package it.evadid.workbook.elements.interactionElements.slideshow

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}

import it.evadid.core.util.io.serializer.DefaultSerializer.given

case class Slideshow(
                      override val elementId: String,
                      private val panels: List[WorkbookDisplayElement]
                    ) extends WorkbookInteractionElement[SlideshowState] derives upickle.default.ReadWriter {

  val panelSize: Int = panels.length

  def indexOf(element: WorkbookElement): Option[Int] = indexOf(element.asRef)

  def getPanel(index: Int): Option[WorkbookElement] = panels.lift(index)

  def indexOf(element: WorkbookElementReference): Option[Int] =
    panels.iterator.zipWithIndex.collectFirst {
      case (panel, index) if panel.elementId == element.referencedId => index
    }

  override val associatedFactory = Slideshow.factory

  override lazy val childrenOfThisElement: List[WorkbookElement] = panels

  override val defaultValue = SlideshowState(panels.map(_.asRef), Set.empty)

  override val serializerInteractionContent: Serializer[SlideshowState] = defaultValue.serializer()

}

object Slideshow {
  val factory: WorkbookElementFactory[Slideshow] = new WorkbookElementFactory[Slideshow] {
    override lazy val elementMapAndOrderForConstructorLike = Map(
      0 -> List(VariableDisplayConfig("elementId", true)),
      1 -> List(VariableDisplayConfig("panels", false))
    )

    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] =
      element.getElementsAs[WorkbookElementReference]("panels").map(_.referencedId).toSet

    override def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable): Seq[WorkbookElementSerializable] = Seq.empty

    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): Slideshow =
      Slideshow(element.elementId, element.getAndResolveWorkbookElements("panels", parsedElements))

    override def toSerializableElement(element: Slideshow): WorkbookElementSerializable =
      toFactoryBase(element)
        .withElementsAddedAs[WorkbookElementReference]("panels", element.panels.map(_.asRef))
  }
}
