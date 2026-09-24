package it.evadid.workbook.elements.interactionElements.reorderExercise

import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import upickle.default.{ReadWriter, macroRW}

sealed trait ReorderInteraction[T] extends WorkbookInteractionElement[ReorderInteractionState[T]] { val elements: List[T] }
object ReorderInteraction {
  private given contentIdRW: ReadWriter[LanguageMapContentId] = LanguageMapContentId.serializer.uPickleReadWrite
  private val contentIds = Serializer.fromUpickleJson(summon[ReadWriter[List[LanguageMapContentId]]])
  private val strings = Serializer.fromUpickleJson(summon[ReadWriter[List[String]]])
  private val constraints = Serializer.fromUpickleJson(summon[ReadWriter[List[(Int, Int)]]])

  case class ReorderCodeInteraction(override val elementId: String, lines: List[String], programmingLanguage: ProgrammingLanguage, seed: Long = 0, hints: List[LanguageMapContentId] = List.empty, orderConstraints: List[(Int, Int)] = Nil) extends ReorderInteraction[String] {
    override val associatedFactory = ReorderCodeInteraction.factory
    override val elements = lines
    override val defaultValue = ReorderInteractionState.initStateFromElementsAndSeed(lines, seed, Serializer.stringIO, ReorderType.CODELINES(programmingLanguage))
    override val serializerInteractionContent = defaultValue.serializer
    override lazy val childrenOfThisElement: List[WorkbookElement] = List()
   }
  object ReorderCodeInteraction {
    val factory = it.evadid.workbook.jsonFactory.WorkbookElementFactory.simple[ReorderCodeInteraction](e => WorkbookElementSerializable(e.elementId, classOf[ReorderCodeInteraction].getSimpleName, Map()).withElementAdded("lines", e.lines)(strings).withElementAdded("programmingLanguage", e.programmingLanguage.name).withElementAdded("seed", e.seed.toString).withElementAdded("hints", e.hints)(contentIds).withElementAdded("orderConstraints", e.orderConstraints)(constraints), fromFactory)
    def fromFactory(f: WorkbookElementSerializable): ReorderCodeInteraction = ReorderCodeInteraction(f.elementId, f.getElementAs("lines")(strings), it.evadid.core.datastructures.language.AppLanguage.programmingLanguages.find(_.name == f.getElementAsString("programmingLanguage")).getOrElse(throw IllegalArgumentException("Unknown programming language")), f.getElementAsString("seed").toLong, f.getElementAs("hints")(contentIds), f.getElementAs("orderConstraints")(constraints))
  }

  case class ReorderMapIdInteraction(override val elementId: String, ids: List[LanguageMapContentId], seed: Long = 0) extends ReorderInteraction[LanguageMapContentId] {
    override val associatedFactory = ReorderMapIdInteraction.factory
    override val elements = ids
    override val defaultValue = ReorderInteractionState.initStateFromElementsAndSeed(ids, seed, LanguageMapContentId.serializer, ReorderType.LANGUAGE_MAP_IDS)
    override val serializerInteractionContent = defaultValue.serializer
    override lazy val childrenOfThisElement: List[WorkbookElement] = List()
    }
  object ReorderMapIdInteraction {
    val factory = it.evadid.workbook.jsonFactory.WorkbookElementFactory.simple[ReorderMapIdInteraction](e => WorkbookElementSerializable(e.elementId, classOf[ReorderMapIdInteraction].getSimpleName, Map()).withElementAdded("ids", e.ids)(contentIds).withElementAdded("seed", e.seed.toString), fromFactory)
    def fromFactory(f: WorkbookElementSerializable): ReorderMapIdInteraction = ReorderMapIdInteraction(f.elementId, f.getElementAs("ids")(contentIds), f.getElementAsString("seed").toLong)
  }
}
