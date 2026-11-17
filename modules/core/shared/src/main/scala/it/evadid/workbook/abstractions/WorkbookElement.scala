package it.evadid.workbook.abstractions
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.abstractions.InteractionFormat.*
import it.evadid.workbook.abstractions.grading.WorkbookInteractionGrader
import it.evadid.workbook.abstractions.scaffolding.WorkbookInteractionScaffolder
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}

sealed trait WorkbookElement {
  val elementId: String
  //  assert(elementId.matches("[a-zA-Z0-9.-]+"))
  lazy val asRef = WorkbookElementReference(elementId, Option(this.getClass.getSimpleName))

  lazy val childrenOfThisElement: List[WorkbookElement]
  lazy val allInteractionsInThisInstance: List[WorkbookInteractionElement[InteractionFormat]] =
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

  def interactionFormat: F = v.value

  override lazy val allInteractionsInThisInstance: List[WorkbookInteractionElement[InteractionFormat]] =
    List(this.asInstanceOf[WorkbookInteractionElement[InteractionFormat]]) ++ childrenOfThisElement.collect { case x: WorkbookInteractionElement[InteractionFormat] => x }.flatMap(_.allInteractionsInThisInstance)
  lazy val isDisabledState: Boolean = false
  // needs to be lazy or defaultValue (from subclass) might not be inited!
  lazy val interactionVariable: InteractionVariable[F] = InteractionVariable[F](this)

  // Abstract type members that will be refined in implementations
  // Using Nothing as placeholder since F#InteractionStateType requires F to be concrete
  type InteractionStateType <: Nothing
  type GradingInfoType <: Nothing
  type ScaffoldingInfoType <: Nothing

  val defaultValue: InteractionStateType

  val gradingElements: List[WorkbookInteractionGrader[InteractionStateType, GradingInfoType]] = List()
  val scaffoldingElement: Option[WorkbookInteractionScaffolder[InteractionStateType, ScaffoldingInfoType]] = None
  // Removed: serializerInteractionContent now uses InteractionFormat's serializers directly via format.stateSerializer

  /** The format providing serializers for this interaction type.
   *
   * Override this in your interaction to provide type-level association
   * between the interaction and its format.
   */
  def format: InteractionFormat = interactionFormat
}
