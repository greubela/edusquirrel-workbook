package it.evadid.workbook.elements.interactionElements.blockchain

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{InteractionFormat, DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.model.blockchain.TeachingChain
import upickle.default.*

case class BlockchainInteraction(elementId: String, title: LanguageMapContentId, initial: TeachingChain, difficultyZeros: Int = 1)
  extends WorkbookInteractionElement[TeachingChain] {

  TeachingChain.validateDifficulty(difficultyZeros)
  override lazy val childrenOfThisElement = Nil
  override val defaultValue = initial

  private def validate(value: TeachingChain): TeachingChain = {
    require(value.blocks.size == initial.blocks.size, "Stored chain must preserve the exercise's block count")
    value
  }

  override val serializerInteractionContent = Serializer.fromUpickleJson(summon[ReadWriter[TeachingChain]]).map(validate, validate)
  override val associatedFactory = BlockchainInteraction.factory
  override def format: InteractionFormat[TeachingChain, Unit, Unit] = BlockchainInteraction.format

  def isCorrect(value: TeachingChain): Boolean =
    value.blocks.size == initial.blocks.size && value.validPrefixLength(difficultyZeros) == value.blocks.size
}

object BlockchainInteraction extends InteractionFormat[TeachingChain, Unit, Unit] {
  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[BlockchainInteraction] {
    override protected val constructorFieldOrder = List("elementId", "title", "initial", "difficultyZeros")

    override def finishSerialization(base: WorkbookElementSerializable, e: BlockchainInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("title", e.title).withElementAddedAs("initial", e.initial).withElementAddedAs("difficultyZeros", e.difficultyZeros)

    override def finishDeserialization(e: WorkbookElementSerializable): BlockchainInteraction =
      BlockchainInteraction(e.elementId, e.getElementAs[LanguageMapContentId]("title"),
        e.getElementAs[TeachingChain]("initial"), e.getElementAs[Int]("difficultyZeros"))
  }

  override def stateSerializer: Serializer[TeachingChain] = Serializer.fromImplicitRW
  override def historySerializer: Serializer[InteractionVariableHistory[TeachingChain, Unit, Unit]] = Serializer.fromImplicitRW
  override def exerciseSerializer: Serializer[WorkbookInteractionElementFull[TeachingChain, Unit, Unit]] = Serializer.fromImplicitRW
}
