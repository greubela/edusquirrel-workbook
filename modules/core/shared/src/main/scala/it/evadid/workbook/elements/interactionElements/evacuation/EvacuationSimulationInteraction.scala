package it.evadid.workbook.elements.interactionElements.evacuation

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{InteractionFormat, DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.model.evacuation.EvacuationExperiment
import upickle.default.*

/** Saves the editable scenario/settings and explicitly recorded runs, not transient playback. */
case class EvacuationSimulationInteraction(elementId: String, initial: EvacuationExperiment = EvacuationExperiment.initial)
    extends WorkbookInteractionElement[EvacuationExperiment] {

  override val defaultValue = initial
  override lazy val childrenOfThisElement = Nil
  override val serializerInteractionContent: Serializer[EvacuationExperiment] = Serializer.fromUpickleJson(summon[ReadWriter[EvacuationExperiment]])
  override val associatedFactory = EvacuationSimulationInteraction.factory
  override def format: InteractionFormat[EvacuationExperiment, Unit, Unit] = EvacuationSimulationInteraction
}
object EvacuationSimulationInteraction extends InteractionFormat[EvacuationExperiment, Unit, Unit] {
  override def stateSerializer: Serializer[EvacuationExperiment] = Serializer.fromImplicitRW
  override def historySerializer: Serializer[InteractionVariableHistory[EvacuationExperiment, Unit, Unit]] = Serializer.fromImplicitRW
  override def exerciseSerializer: Serializer[WorkbookInteractionElementFull[EvacuationExperiment, Unit, Unit]] = Serializer.fromImplicitRW
  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[EvacuationSimulationInteraction] {
    override protected val constructorFieldOrder = List("elementId", "initial")
    override def finishSerialization(base: WorkbookElementSerializable, e: EvacuationSimulationInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("initial", e.initial)
    override def finishDeserialization(e: WorkbookElementSerializable): EvacuationSimulationInteraction =
      EvacuationSimulationInteraction(e.elementId, e.getElementAs[EvacuationExperiment]("initial"))
  }
}
