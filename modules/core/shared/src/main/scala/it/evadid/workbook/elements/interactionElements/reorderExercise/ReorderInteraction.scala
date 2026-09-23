package it.evadid.workbook.elements.interactionElements.reorderExercise

import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import upickle.default.{ReadWriter, macroRW}

sealed trait ReorderInteraction[T] extends WorkbookInteractionElement[ReorderInteractionState[T]] { val elements: List[T] }
object ReorderInteraction {
  private given contentIdRW: ReadWriter[LanguageMapContentId] = LanguageMapContentId.serializer.uPickleReadWrite
  private val contentIds = Serializer.fromUpickleJson(summon[ReadWriter[List[LanguageMapContentId]]])
  private val strings = Serializer.fromUpickleJson(summon[ReadWriter[List[String]]])
  private val constraints = Serializer.fromUpickleJson(summon[ReadWriter[List[(Int, Int)]]])

  case class ReorderCodeInteraction(override val elementId: String, lines: List[String], programmingLanguage: ProgrammingLanguage, seed: Long = 0, hints: List[LanguageMapContentId] = List.empty, orderConstraints: List[(Int, Int)] = Nil) extends ReorderInteraction[String] {
    override val elements = lines
    override val defaultValue = ReorderInteractionState.initStateFromElementsAndSeed(lines, seed, Serializer.stringIO, ReorderType.CODELINES(programmingLanguage))
    override val serializerInteractionContent = defaultValue.serializer
    override lazy val childrenOfThisElement: List[WorkbookElement] = List()
    override def toSerializableType = toFactoryBase.withElementAdded("lines", lines)(strings).withElementAdded("programmingLanguage", programmingLanguage.name).withElementAdded("seed", seed.toString).withElementAdded("hints", hints)(contentIds).withElementAdded("orderConstraints", orderConstraints)(constraints)
  }
  object ReorderCodeInteraction { def fromFactory(f: WorkbookElementFactory): ReorderCodeInteraction = ReorderCodeInteraction(f.elementId, f.getElementAs("lines")(strings), it.evadid.core.datastructures.language.AppLanguage.programmingLanguages.find(_.name == f.getElementAsString("programmingLanguage")).getOrElse(throw IllegalArgumentException("Unknown programming language")), f.getElementAsString("seed").toLong, f.getElementAs("hints")(contentIds), f.getElementAs("orderConstraints")(constraints)) }

  case class ReorderMapIdInteraction(override val elementId: String, ids: List[LanguageMapContentId], seed: Long = 0) extends ReorderInteraction[LanguageMapContentId] {
    override val elements = ids
    override val defaultValue = ReorderInteractionState.initStateFromElementsAndSeed(ids, seed, LanguageMapContentId.serializer, ReorderType.LANGUAGE_MAP_IDS)
    override val serializerInteractionContent = defaultValue.serializer
    override lazy val childrenOfThisElement: List[WorkbookElement] = List()
    override def toSerializableType = toFactoryBase.withElementAdded("ids", ids)(contentIds).withElementAdded("seed", seed.toString)
  }
  object ReorderMapIdInteraction { def fromFactory(f: WorkbookElementFactory): ReorderMapIdInteraction = ReorderMapIdInteraction(f.elementId, f.getElementAs("ids")(contentIds), f.getElementAsString("seed").toLong) }
}
