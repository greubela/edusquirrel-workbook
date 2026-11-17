package it.evadid.workbook.elements.interactionElements.slideshow

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.InteractionFormat.SlideshowFormat
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookDisplayElement, WorkbookElement, WorkbookInteractionElement, *}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory

case class Slideshow(
                      override val elementId: String,
                      private val panels: Seq[WorkbookDisplayElement]
                    ) extends WorkbookInteractionElement[SlideshowFormat.type] {

  val panelSize: Int = panels.length

  def indexOf(element: WorkbookElement): Option[Int] = {
    indexOf(element.asRef)
  }

  def getPanel(index: Int): Option[WorkbookElement] = if(index < panels.length && index >= 0) Some(panels(index)) else None

  def indexOf(element: WorkbookElementReference): Option[Int] = {
    panels.zipWithIndex.filter(_._1.asRef.referencedId == element.referencedId).map(_._2).headOption

  }

  override val associatedFactory = Slideshow.factory

  override lazy val childrenOfThisElement: Seq[WorkbookElement] = panels

  override val defaultValue = SlideshowState(panels.map(_.asRef), Set.empty)


}

object Slideshow  {

  val factory: WorkbookElementFactory[Slideshow] = new WorkbookElementFactory[Slideshow] {
    override lazy val elementMapAndOrderForConstructorLike = Map(0 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("elementId", true)), 1 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("panels", false)))

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
