package it.evadid.workbook.interactions.evacuation

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement, *}
import it.evadid.workbook.serialization.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.model.history.InteractionVariableHistory
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
