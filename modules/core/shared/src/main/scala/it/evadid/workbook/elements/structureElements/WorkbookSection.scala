package it.evadid.workbook.elements.structureElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.workbook.abstractions.WorkbookStructuringType.SECTION
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookStructureElement, WorkbookStructuringType}
import it.evadid.workbook.elements.structureElements.WorkbookSection.WorkbookSectionMetadata
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}
import upickle.default.*

case class WorkbookSection
(
  elementId: String,
  metadata: WorkbookSectionMetadata,
  sectionContent: List[WorkbookElement],
) extends WorkbookStructureElement[WorkbookElement] {

  override val associatedFactory = WorkbookSection.factory

  override val groupElements: List[WorkbookElement] = sectionContent

  override lazy val structureType: WorkbookStructuringType = SECTION

  val sectionId: String = elementId

}

object WorkbookSection {
  val factory: WorkbookElementFactory[WorkbookSection] = new WorkbookElementFactory[WorkbookSection] {
    override lazy val elementMapAndOrderForConstructorLike = Map(0 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("elementId", true)), 1 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("metadata", false)), 2 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("requiredBefore", false)), 3 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("recommendedBefore", false)), 4 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("content", false)))

    private def references(element: WorkbookElementSerializable, key: String) =
      element.getElementsAs[WorkbookElementReference](key)

    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] =
      (references(element, "content") ++ references(element, "requiredBefore") ++ references(element, "recommendedBefore"))
        .map(_.referencedId).toSet

    override def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable): Seq[WorkbookElementSerializable] = Seq.empty

    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): WorkbookSection =
      WorkbookSection(
        element.elementId,
        element.getElementAs[WorkbookSectionMetadata]("metadata"),
        element.getAndResolveWorkbookElements("content", parsedElements),
      )

    override def toSerializableElement(element: WorkbookSection): WorkbookElementSerializable =
      toFactoryBase(element)
        .withElementAddedAs("metadata", element.metadata)
        .withElementsAddedAs("requiredBefore", element.metadata.sectionsRequiredBefore.map(_.asRef))
        .withElementsAddedAs("recommendedBefore", element.metadata.sectionsRecommendedBefore.map(_.asRef))
        .withElementsAddedAs[WorkbookElementReference]("content", element.sectionContent.map(_.asRef))
  }

   val referencingJsonSerializer: ReadWriter[WorkbookSection] = new Serializer[WorkbookSection]() {
    override def serialize(obj: WorkbookSection): String = {
      val serialized = WorkbookElementSerializable(obj.elementId, obj.getClass.getSimpleName, Map.empty)
        .withElementAddedAs("metadata", obj.metadata)
        .withElementsAddedAs("content", obj.sectionContent.map(_.asRef))
      write(serialized)(using WorkbookElementSerializable.regularSerializer)
    }

    override def deserialize(str: String): WorkbookSection = ???
  }.uPickleReadWrite

  val constructorSerializer: Serializer[WorkbookSection] = new ConstructorLikeSerializer[WorkbookSection] {
    override implicit val regularSerializer: Serializer[WorkbookSection] = Serializer.fromUpickleJson(WorkbookSection.referencingJsonSerializer)
    override val constructorName: String = WorkbookSection.this.getClass.getSimpleName

    override val elementMapAndOrder: Map[Int, List[VariableDisplayConfig]] = {
      {
        Map(
          0 -> List(VariableDisplayConfig("elementId", true)),
          1 -> List(VariableDisplayConfig("metadata", true)),
          2 -> List(VariableDisplayConfig("sectionContent", false))
        )
      }
    }
  }

  given ReadWriter[WorkbookSectionMetadata] = new Serializer[WorkbookSectionMetadata]() {
    override def serialize(obj: WorkbookSectionMetadata): String = {
      write(ujson.Obj(
        "sectionTitle" -> write(obj.sectionTitle),
        "requiredBefore" -> write(obj.sectionsRequiredBefore.map(_.asRef)),
        "recommendedBefore" -> write(obj.sectionsRecommendedBefore.map(_.asRef))
      ))
    }

    override def deserialize(str: String): WorkbookSectionMetadata = ???
  }.uPickleReadWrite

  case class WorkbookSectionMetadata
  (
    sectionTitle: LanguageMapContentId,
    sectionsRequiredBefore: List[WorkbookSection] = List(),
    sectionsRecommendedBefore: List[WorkbookSection] = List()
  ) // todo: sections as ref

}
