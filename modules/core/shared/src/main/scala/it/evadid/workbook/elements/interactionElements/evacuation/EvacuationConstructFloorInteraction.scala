package it.evadid.workbook.elements.interactionElements.evacuation

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.model.evacuation.EvacuationFloorPlan
import upickle.default.*

/** Construction criteria only; they do not certify reachability or evacuation safety. */
case class EvacuationFloorRequirements(minPeople: Int = 1, minExits: Int = 1) derives ReadWriter {
  require(minPeople >= 0 && minExits >= 0, "Minimum counts must be non-negative")
  def isSatisfiedBy(floor: EvacuationFloorPlan): Boolean =
    floor.people.size >= minPeople && floor.exitCount >= minExits
}
case class EvacuationConstructFloorInteraction(elementId: String,
    initial: EvacuationFloorPlan = EvacuationFloorPlan.empty(),
    requirements: EvacuationFloorRequirements = EvacuationFloorRequirements())
    extends WorkbookInteractionElement[EvacuationFloorPlan] {
  override val defaultValue = initial
  override lazy val childrenOfThisElement = Nil
  override val serializerInteractionContent: Serializer[EvacuationFloorPlan] =
    Serializer.fromUpickleJson(summon[ReadWriter[EvacuationFloorPlan]])
  override val associatedFactory = EvacuationConstructFloorInteraction.factory
  def isPassed: Boolean = requirements.isSatisfiedBy(interactionVariable.currentValue)
}
object EvacuationConstructFloorInteraction {
  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[EvacuationConstructFloorInteraction] {
    override protected val constructorFieldOrder = List("elementId", "initial", "requirements")
    override def finishSerialization(base: WorkbookElementSerializable, e: EvacuationConstructFloorInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("initial", e.initial).withElementAddedAs("requirements", e.requirements)
    override def finishDeserialization(e: WorkbookElementSerializable): EvacuationConstructFloorInteraction =
      EvacuationConstructFloorInteraction(e.elementId, e.getElementAs[EvacuationFloorPlan]("initial"),
        e.getElementAs[EvacuationFloorRequirements]("requirements"))
  }
}
