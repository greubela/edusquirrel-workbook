package it.evadid.workbook.elements.interactionElements.text

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

case class UnicodeComparisonAnswer(first: String = "", second: String = "") derives ReadWriter {
  require(first.length <= UnicodeComparisonInteraction.maxTextLength && second.length <= UnicodeComparisonInteraction.maxTextLength)
}
/** Exploration, not a domain-trust verdict or an IDNA/URL parser. */
case class UnicodeComparisonInteraction(elementId: String, title: LanguageMapContentId,
    initial: UnicodeComparisonAnswer = UnicodeComparisonAnswer()) extends WorkbookInteractionElement[UnicodeComparisonAnswer] {
  override lazy val childrenOfThisElement = Nil
  override val defaultValue = initial
  override val serializerInteractionContent = Serializer.fromUpickleJson(summon[ReadWriter[UnicodeComparisonAnswer]])
  override val associatedFactory = UnicodeComparisonInteraction.factory
}
object UnicodeComparisonInteraction {
  val maxTextLength = 256
  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[UnicodeComparisonInteraction] {
    override protected val constructorFieldOrder = List("elementId", "title", "initial")
    override def finishSerialization(base: WorkbookElementSerializable, e: UnicodeComparisonInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("title", e.title).withElementAddedAs("initial", e.initial)
    override def finishDeserialization(e: WorkbookElementSerializable): UnicodeComparisonInteraction =
      UnicodeComparisonInteraction(e.elementId, e.getElementAs[LanguageMapContentId]("title"), e.getElementAs[UnicodeComparisonAnswer]("initial"))
  }
}
