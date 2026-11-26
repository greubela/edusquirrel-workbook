package it.evadid.workbook.abstractions
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.abstractions.InteractionFormat.*
import it.evadid.workbook.abstractions.grading.WorkbookInteractionGrader
import it.evadid.workbook.abstractions.scaffolding.WorkbookInteractionScaffolder
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}

import it.evadid.workbook.jsonFactory.SerializedWorkbook

object WorkbookElement {
  given upickle.default.ReadWriter[WorkbookElement] =
    upickle.default.readwriter[SerializedWorkbook].bimap(SerializedWorkbook.fromElement, _.toElement)

  private[workbook] def subtypeCodec[T <: WorkbookElement: scala.reflect.ClassTag]: upickle.default.ReadWriter[T] =
    upickle.default.readwriter[WorkbookElement].bimap(element => element,
      element => summon[scala.reflect.ClassTag[T]].unapply(element)
        .getOrElse(throw new IllegalArgumentException(s"Unexpected workbook element: ${element.getClass.getSimpleName}")))
}

sealed trait WorkbookElement {
  val elementId: String
  //  assert(elementId.matches("[a-zA-Z0-9.-]+"))
  lazy val asRef = WorkbookElementReference(elementId, Option(this.getClass.getSimpleName))

  lazy val childrenOfThisElement: Seq[WorkbookElement]
  lazy val allInteractionsInThisInstance: Seq[WorkbookInteractionElement[? <: InteractionFormat]] =
    childrenOfThisElement.flatMap(_.allInteractionsInThisInstance)

  lazy val allChildrenFullSubtree: Seq[WorkbookElement] = childrenOfThisElement ++ childrenOfThisElement.flatMap(_.allChildrenFullSubtree)

  val associatedFactory: WorkbookElementFactory[? <: WorkbookElement]

  lazy val toSerialized: WorkbookElementSerializable = associatedFactory.toSerializableElementUnsafe(this)

  lazy val toStringRegularJson: String = WorkbookElementFactory.serializerRefBasedJson.serialize(this)
  lazy val toStringConstructorLike: String = WorkbookElementFactory.serializerConstructorLike.serialize(this)

}

trait WorkbookDisplayElement extends WorkbookElement {
  lazy val childrenOfThisElement: Seq[WorkbookElement] = List()
}


trait WorkbookStructureElement[T <: WorkbookElement] extends WorkbookElement {
  lazy val structureType: WorkbookStructuringType
  override lazy val childrenOfThisElement: Seq[WorkbookElement] = groupElements

  def groupElements: Seq[T]
}


trait WorkbookInteractionElement[F <: InteractionFormat](using v: ValueOf[F]) extends WorkbookElement {

  val interactionFormat: F = v.value

  override lazy val allInteractionsInThisInstance: Seq[WorkbookInteractionElement[? <: InteractionFormat]] =
    List(this) ++ childrenOfThisElement.collect { case x: WorkbookInteractionElement[? <: InteractionFormat] => x }.flatMap(_.allInteractionsInThisInstance)
  lazy val isDisabledState: it.evadid.core.datastructures.state.State[Boolean] = it.evadid.core.datastructures.state.State(false)
  // needs to be lazy or defaultValue (from subclass) might not be inited!
  lazy val interactionVariable: InteractionVariable[F] = InteractionVariable[F](this)

  // Type members that use the format's type members
  type InteractionStateType = InteractionFormat.State[F]
  type GradingInfoType = InteractionFormat.Grading[F]
  type ScaffoldingInfoType = InteractionFormat.Scaffolding[F]

  def validateState(value: InteractionStateType): Unit = ()

  /** Wrong parts of an answer, as indices whose meaning the interaction defines (an option, an editable cell, ...).
   * Parts left unanswered are not wrong. Renderers permanently mark the parts that a failed check found wrong, see
   * [[it.evadid.workbook.meta.CheckReport]]; interactions without such parts, like programming tasks, keep the default. */
  def wrongParts(answer: InteractionStateType): Set[Int] = Set.empty

  val defaultValue: InteractionStateType

  val gradingElements: Seq[WorkbookInteractionGrader[InteractionStateType, GradingInfoType]] = List()
  val scaffoldingElement: Option[WorkbookInteractionScaffolder[InteractionStateType, ScaffoldingInfoType]] = None

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
