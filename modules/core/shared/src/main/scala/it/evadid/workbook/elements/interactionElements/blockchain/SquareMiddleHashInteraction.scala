package it.evadid.workbook.elements.interactionElements.blockchain

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.model.blockchain.*
import upickle.default.*

sealed trait SquareMiddleHashTask derives ReadWriter
case class ExploreSquareMiddleHash() extends SquareMiddleHashTask derives ReadWriter
case class FindHashCollision() extends SquareMiddleHashTask derives ReadWriter
case class FindHashPreimage(target: String) extends SquareMiddleHashTask derives ReadWriter {
  require(target.matches("[0-9]{2}"), "A target hash must contain exactly two decimal digits")
}
case class SquareMiddleHashAnswer(first: String = "", second: String = "") derives ReadWriter {
  require(first.length <= SquareMiddleHash.maxInputDigits && second.length <= SquareMiddleHash.maxInputDigits,
    "Stored hash input is too long")
}
case class SquareMiddleHashInteraction(elementId: String, title: LanguageMapContentId,
    task: SquareMiddleHashTask = ExploreSquareMiddleHash(), initial: SquareMiddleHashAnswer = SquareMiddleHashAnswer())
    extends WorkbookInteractionElement[SquareMiddleHashAnswer] {
  override lazy val childrenOfThisElement = Nil
  override val defaultValue = initial
  override val serializerInteractionContent = Serializer.fromUpickleJson(summon[ReadWriter[SquareMiddleHashAnswer]])
  override val associatedFactory = SquareMiddleHashInteraction.factory
  def results(answer: SquareMiddleHashAnswer): List[Option[SquareMiddleHashResult]] = task match {
    case _: FindHashPreimage => List(SquareMiddleHash.parse(answer.first))
    case _ => List(SquareMiddleHash.parse(answer.first), SquareMiddleHash.parse(answer.second))
  }
  def isCorrect(answer: SquareMiddleHashAnswer): Option[Boolean] = task match {
    case ExploreSquareMiddleHash() => None
    case FindHashCollision() => Some((SquareMiddleHash.parse(answer.first), SquareMiddleHash.parse(answer.second)) match {
      case (Some(a), Some(b)) => a.input != b.input && a.hash == b.hash
      case _ => false
    })
    case FindHashPreimage(target) => Some(SquareMiddleHash.parse(answer.first).exists(_.hash == target))
  }
}
object SquareMiddleHashInteraction {
  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[SquareMiddleHashInteraction] {
    override protected val constructorFieldOrder = List("elementId", "title", "task", "initial")
    override def finishSerialization(base: WorkbookElementSerializable, e: SquareMiddleHashInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("title", e.title).withElementAddedAs("task", e.task).withElementAddedAs("initial", e.initial)
    override def finishDeserialization(e: WorkbookElementSerializable): SquareMiddleHashInteraction =
      SquareMiddleHashInteraction(e.elementId, e.getElementAs[LanguageMapContentId]("title"),
        e.getElementAs[SquareMiddleHashTask]("task"), e.getElementAs[SquareMiddleHashAnswer]("initial"))
  }
}
