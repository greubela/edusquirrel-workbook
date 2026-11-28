package it.evadid.workbook.interactions.evacuation

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement, *}
import it.evadid.workbook.serialization.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.history.InteractionVariableHistory
import upickle.default.*

import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
/** Construction criteria only; they do not certify reachability or evacuation safety. */
case class EvacuationFloorRequirements(minPeople: Int = 1, minExits: Int = 1) derives ReadWriter {
  require(minPeople >= 0 && minExits >= 0, "Minimum counts must be non-negative")
  def isSatisfiedBy(floor: EvacuationFloorPlan): Boolean =
    floor.people.size >= minPeople && floor.exitCount >= minExits
}
case class EvacuationConstructFloorInteraction(elementId: String,
    initial: EvacuationFloorPlan = EvacuationFloorPlan.empty(),
    requirements: EvacuationFloorRequirements = EvacuationFloorRequirements())
    extends WorkbookInteractionElement[EvacuationConstructFloorInteractionFormat.type] {

  override val defaultValue = initial
  override lazy val childrenOfThisElement = Nil

  override val associatedFactory = EvacuationConstructFloorInteraction.factory
  def isPassed(answer: InteractionStateType): Boolean = requirements.isSatisfiedBy(answer)

}
object EvacuationConstructFloorInteraction  {

  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[EvacuationConstructFloorInteraction] {
    override protected val constructorFieldOrder = List("elementId", "initial", "requirements")
    override def finishSerialization(base: WorkbookElementSerializable, e: EvacuationConstructFloorInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("initial", e.initial).withElementAddedAs("requirements", e.requirements)
    override def finishDeserialization(e: WorkbookElementSerializable): EvacuationConstructFloorInteraction =
      EvacuationConstructFloorInteraction(e.elementId, e.getElementAs[EvacuationFloorPlan]("initial"),
        e.getElementAs[EvacuationFloorRequirements]("requirements"))
  }
}
