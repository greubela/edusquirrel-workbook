package it.evadid.workbook.interactions.blockchain

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement, *}
import it.evadid.workbook.serialization.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.history.InteractionVariableHistory
import upickle.default.*

import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
case class BlockchainInteraction(elementId: String, title: LanguageMapContentId, initial: TeachingChain, difficultyZeros: Int = 1)
  extends WorkbookInteractionElement[BlockchainInteractionFormat.type] {

  TeachingChain.validateDifficulty(difficultyZeros)
  override lazy val childrenOfThisElement = Nil
  override val defaultValue = initial

  override def validateState(value: TeachingChain): Unit = { validate(value); () }

  private def validate(value: TeachingChain): TeachingChain = {
    require(value.blocks.size == initial.blocks.size, "Stored chain must preserve the exercise's block count")
    value
  }

  override val associatedFactory = BlockchainInteraction.factory

  def isCorrect(value: TeachingChain): Boolean =
    value.blocks.size == initial.blocks.size && value.validPrefixLength(difficultyZeros) == value.blocks.size
}

object BlockchainInteraction  {

  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[BlockchainInteraction] {
    override protected val constructorFieldOrder = List("elementId", "title", "initial", "difficultyZeros")

    override def finishSerialization(base: WorkbookElementSerializable, e: BlockchainInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("title", e.title).withElementAddedAs("initial", e.initial).withElementAddedAs("difficultyZeros", e.difficultyZeros)

    override def finishDeserialization(e: WorkbookElementSerializable): BlockchainInteraction =
      BlockchainInteraction(e.elementId, e.getElementAs[LanguageMapContentId]("title"),
        e.getElementAs[TeachingChain]("initial"), e.getElementAs[Int]("difficultyZeros"))
  }
}
