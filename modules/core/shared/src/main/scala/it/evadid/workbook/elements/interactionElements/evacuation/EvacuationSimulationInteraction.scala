package it.evadid.workbook.elements.interactionElements.evacuation

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{DefaultSerializer, InteractionFormat}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement, WorkbookInteractionElementFull}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.model.evacuation.EvacuationExperiment
import upickle.default.*

import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
/** Saves the editable scenario/settings and explicitly recorded runs, not transient playback. */
case class EvacuationSimulationInteraction(elementId: String, initial: EvacuationExperiment = EvacuationExperiment.initial)
    extends WorkbookInteractionElement[EvacuationExperimentFormat.type] {

  override val defaultValue = initial
  override lazy val childrenOfThisElement = Nil

  override val associatedFactory = EvacuationSimulationInteraction.factory

}
object EvacuationSimulationInteraction  {

  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[EvacuationSimulationInteraction] {
    override protected val constructorFieldOrder = List("elementId", "initial")
    override def finishSerialization(base: WorkbookElementSerializable, e: EvacuationSimulationInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("initial", e.initial)
    override def finishDeserialization(e: WorkbookElementSerializable): EvacuationSimulationInteraction =
      EvacuationSimulationInteraction(e.elementId, e.getElementAs[EvacuationExperiment]("initial"))
  }
}
