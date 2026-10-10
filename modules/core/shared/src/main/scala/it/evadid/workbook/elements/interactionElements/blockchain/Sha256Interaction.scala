package it.evadid.workbook.elements.interactionElements.blockchain

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.model.blockchain.Sha256
import upickle.default.*

sealed trait Sha256Task derives ReadWriter

case class CompareSha256() extends Sha256Task derives ReadWriter

case class FindSha256Prefix(zeros: Int = 1) extends Sha256Task derives ReadWriter {
  require(zeros >= 1 && zeros <= 4, "Teaching challenges allow one to four leading hexadecimal zeros")
}

case class Sha256Answer(first: String = "", second: String = "") derives ReadWriter {
  require(first.length <= Sha256Interaction.maxTextLength && second.length <= Sha256Interaction.maxTextLength,
    "SHA-256 exercise input is too long")
}

case class Sha256Interaction(elementId: String, title: LanguageMapContentId,
                             task: Sha256Task = CompareSha256(), initial: Sha256Answer = Sha256Answer())
  extends WorkbookInteractionElement[Sha256Answer] derives upickle.default.ReadWriter {
  override lazy val childrenOfThisElement = Nil
  override val defaultValue = initial
  override val serializerInteractionContent = Serializer.fromUpickleJson(summon[ReadWriter[Sha256Answer]])
  override val associatedFactory = Sha256Interaction.factory

  def hashes(answer: Sha256Answer): List[String] = task match {
    case CompareSha256() => List(Sha256.text(answer.first), Sha256.text(answer.second))
    case _: FindSha256Prefix => List(Sha256.text(answer.first))
  }

  def isCorrect(answer: Sha256Answer): Option[Boolean] = task match {
    case CompareSha256() => None
    case FindSha256Prefix(zeros) => Some(Sha256.text(answer.first).startsWith("0" * zeros))
  }
}

object Sha256Interaction {
  val maxTextLength = 4096
  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[Sha256Interaction] {
    override protected val constructorFieldOrder = List("elementId", "title", "task", "initial")

    override def finishSerialization(base: WorkbookElementSerializable, e: Sha256Interaction): WorkbookElementSerializable =
      base.withElementAddedAs("title", e.title).withElementAddedAs("task", e.task).withElementAddedAs("initial", e.initial)

    override def finishDeserialization(e: WorkbookElementSerializable): Sha256Interaction =
      Sha256Interaction(e.elementId, e.getElementAs[LanguageMapContentId]("title"),
        e.getElementAs[Sha256Task]("task"), e.getElementAs[Sha256Answer]("initial"))
  }
}
