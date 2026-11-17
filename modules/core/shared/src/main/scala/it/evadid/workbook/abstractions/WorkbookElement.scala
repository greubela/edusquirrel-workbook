package it.evadid.workbook.abstractions
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.abstractions.InteractionFormat.*
import it.evadid.workbook.abstractions.grading.WorkbookInteractionGrader
import it.evadid.workbook.abstractions.scaffolding.WorkbookInteractionScaffolder
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}

object WorkbookElement {
  private case class Snapshot(rootId: String, elements: List[WorkbookElementSerializable]) derives upickle.default.ReadWriter

  private def collect(pending: List[WorkbookElement], known: Map[String, WorkbookElement]): Map[String, WorkbookElement] = pending match {
    case Nil => known
    case element :: remaining => known.get(element.elementId) match {
      case Some(previous) =>
        require(previous == element, s"Conflicting workbook element id: ${element.elementId}")
        collect(remaining, known)
      case None =>
        val prerequisites = element match {
          case section: it.evadid.workbook.elements.structureElements.WorkbookSection =>
            section.metadata.sectionsRequiredBefore ++ section.metadata.sectionsRecommendedBefore
          case _ => Nil
        }
        collect(element.childrenOfThisElement ++ prerequisites ++ remaining, known.updated(element.elementId, element))
    }
  }

  given upickle.default.ReadWriter[WorkbookElement] = upickle.default.readwriter[Snapshot].bimap(
    element => Snapshot(element.elementId, collect(List(element), Map.empty).valuesIterator.map(_.toSerialized).toList),
    snapshot => WorkbookElementFactory.parseAllAsMap(snapshot.elements).getOrElse(snapshot.rootId,
      throw new IllegalArgumentException(s"Missing workbook root: ${snapshot.rootId}")))

  private[workbook] def subtypeCodec[T <: WorkbookElement: scala.reflect.ClassTag]: upickle.default.ReadWriter[T] =
    upickle.default.readwriter[WorkbookElement].bimap(element => element,
      element => summon[scala.reflect.ClassTag[T]].unapply(element)
        .getOrElse(throw new IllegalArgumentException(s"Unexpected workbook element: ${element.getClass.getSimpleName}")))
}

sealed trait WorkbookElement {
  val elementId: String
  //  assert(elementId.matches("[a-zA-Z0-9.-]+"))
  lazy val asRef = WorkbookElementReference(elementId, Option(this.getClass.getSimpleName))

  lazy val childrenOfThisElement: List[WorkbookElement]
  lazy val allInteractionsInThisInstance: List[WorkbookInteractionElement[? <: InteractionFormat]] =
    childrenOfThisElement.flatMap(_.allInteractionsInThisInstance)

  lazy val allChildrenFullSubtree: List[WorkbookElement] = childrenOfThisElement ++ childrenOfThisElement.flatMap(_.allChildrenFullSubtree)

  val associatedFactory: WorkbookElementFactory[? <: WorkbookElement]

  lazy val toSerialized: WorkbookElementSerializable = associatedFactory.toSerializableElementUnsafe(this)

  lazy val toStringRegularJson: String = WorkbookElementFactory.serializerRefBasedJson.serialize(this)
  lazy val toStringConstructorLike: String = WorkbookElementFactory.serializerConstructorLike.serialize(this)

}

trait WorkbookDisplayElement extends WorkbookElement {
  lazy val childrenOfThisElement: List[WorkbookElement] = List()
}


trait WorkbookStructureElement[T <: WorkbookElement] extends WorkbookElement {
  lazy val structureType: WorkbookStructuringType
  override lazy val childrenOfThisElement: List[WorkbookElement] = groupElements

  def groupElements: List[T]
}


trait WorkbookInteractionElement[F <: InteractionFormat](using v: ValueOf[F]) extends WorkbookElement {

  val interactionFormat: F = v.value

  override lazy val allInteractionsInThisInstance: List[WorkbookInteractionElement[? <: InteractionFormat]] =
    List(this) ++ childrenOfThisElement.collect { case x: WorkbookInteractionElement[? <: InteractionFormat] => x }.flatMap(_.allInteractionsInThisInstance)
  lazy val isDisabledState: it.evadid.core.datastructures.state.State[Boolean] = it.evadid.core.datastructures.state.State(false)
  // needs to be lazy or defaultValue (from subclass) might not be inited!
  lazy val interactionVariable: InteractionVariable[F] = InteractionVariable[F](this)

  // Type members that use the format's type members
  type InteractionStateType = InteractionFormat.State[F]
  type GradingInfoType = InteractionFormat.Grading[F]
  type ScaffoldingInfoType = InteractionFormat.Scaffolding[F]

  def validateState(value: InteractionStateType): Unit = ()

  val defaultValue: InteractionStateType

  val gradingElements: List[WorkbookInteractionGrader[InteractionStateType, GradingInfoType]] = List()
  val scaffoldingElement: Option[WorkbookInteractionScaffolder[InteractionStateType, ScaffoldingInfoType]] = None
  // Removed: serializerInteractionContent now uses InteractionFormat's serializers directly via format.stateSerializer

  /** The format providing serializers for this interaction type.
   *
   * Override this in your interaction to provide type-level association
   * between the interaction and its format.
   */
  def format: F = interactionFormat
}

object WorkbookInteractionElement {
  given [F <: InteractionFormat](using value: ValueOf[F]): upickle.default.ReadWriter[WorkbookInteractionElement[F]] =
    upickle.default.readwriter[WorkbookElement].bimap(element => element, {
      case element: WorkbookInteractionElement[?] if element.interactionFormat == value.value =>
        element.asInstanceOf[WorkbookInteractionElement[F]]
      case element => throw new IllegalArgumentException(s"Expected interaction format ${value.value.name}, got ${element.getClass.getSimpleName}")
    })
}

object WorkbookDisplayElement {
  given upickle.default.ReadWriter[WorkbookDisplayElement] = WorkbookElement.subtypeCodec
}
object WorkbookStructureElement {
  given [T <: WorkbookElement]: upickle.default.ReadWriter[WorkbookStructureElement[T]] = WorkbookElement.subtypeCodec
}
