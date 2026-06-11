package it.evadid.workbook.serialization

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.elements.{Workbook, WorkbookSection}
import upickle.ReadWriter
import upickle.default.*

/**
 * Workbook serialization envelope that stores section bodies exactly once.
 *
 * The workbook's visible ordering is represented by section ids in sectionOrder.
 * The top-level sectionRegistry contains each SerializedWorkbookSection body once,
 * keyed by sectionId. Section dependencies are stored as ids inside each registry
 * entry and are resolved back to WorkbookSection instances during import.
 */
case class SerializedWorkbook(
                               workbookId: String,
                               workbookTitle: LanguageMapContentId,
                               sectionOrder: List[String],
                               availableLanguages: List[HumanLanguage],
                               sectionRegistry: Map[String, SerializedWorkbookSection]
                             ) {

  def toWorkbook(elementsByRef: Map[ElementRef, WorkbookElement]): Workbook = {
    val resolvedSectionsById = resolveSections(elementsByRef)
    val sections = sectionOrder.map { sectionId =>
      resolvedSectionsById.getOrElse(sectionId, throw new NoSuchElementException(s"Missing workbook section '${sectionId}' in section registry"))
    }

    Workbook(workbookId, workbookTitle, sections, availableLanguages)
  }

  private def resolveSections(elementsByRef: Map[ElementRef, WorkbookElement]): Map[String, WorkbookSection] = {
    val cache = scala.collection.mutable.Map.empty[String, WorkbookSection]

    def resolve(sectionId: String, resolving: Set[String]): WorkbookSection =
      cache.getOrElseUpdate(sectionId, {
        if (resolving.contains(sectionId)) {
          val cycle = (resolving.toList :+ sectionId).mkString(" -> ")
          throw new IllegalArgumentException(s"Cyclic workbook section dependency detected: ${cycle}")
        }

        val serialized = sectionRegistry.getOrElse(
          sectionId,
          throw new NoSuchElementException(s"Missing workbook section '${sectionId}' in section registry")
        )
        val dependencyIds = serialized.sectionsRequiredBeforeIds ++ serialized.sectionsRecommendedBeforeIds
        val dependencySections = dependencyIds.distinct.map(id => id -> resolve(id, resolving + sectionId)).toMap

        serialized.toWorkbookSection(elementsByRef, dependencySections)
      })

    sectionRegistry.keys.foreach(sectionId => resolve(sectionId, Set.empty))
    cache.toMap
  }

  def toJson(pretty: Boolean = false): String =
    if (pretty) write(this, indent = 2) else write(this)
}

object SerializedWorkbook {
  given languageMapContentIdReadWriter: ReadWriter[LanguageMapContentId] =
    SerializedWorkbookSection.languageMapContentIdReadWriter

  given humanLanguageReadWriter: ReadWriter[HumanLanguage] =
    readwriter[String].bimap[HumanLanguage](
      language => language.nameAbbr.toLowerCase,
      serialized => AppLanguage.humanLanguages.find(_.nameAbbr.equalsIgnoreCase(serialized)).getOrElse(
        throw new IllegalArgumentException(s"Unknown human language '${serialized}'")
      )
    )

  given readWriter: ReadWriter[SerializedWorkbook] = macroRW

  def fromWorkbook(
                    workbook: Workbook,
                    elementRefFor: WorkbookElement => ElementRef
                  ): SerializedWorkbook = {
    val allSections = collectReachableSections(workbook.sections)
    val sectionRegistry = allSections
      .map(section => section.sectionId -> SerializedWorkbookSection.fromWorkbookSection(section, elementRefFor))
      .toMap

    SerializedWorkbook(
      workbookId = workbook.workbookId,
      workbookTitle = workbook.workbookTitle,
      sectionOrder = workbook.sections.map(_.sectionId),
      availableLanguages = workbook.availableLanguages,
      sectionRegistry = sectionRegistry
    )
  }

  def fromJson(json: String): SerializedWorkbook = read[SerializedWorkbook](json)

  private def collectReachableSections(rootSections: List[WorkbookSection]): List[WorkbookSection] = {
    def collect(section: WorkbookSection, seenIds: Set[String]): List[WorkbookSection] =
      if (seenIds.contains(section.sectionId)) List.empty
      else {
        val dependencies = section.sectionsRequiredBefore ++ section.sectionsRecommendedBefore
        section :: dependencies.flatMap(dependency => collect(dependency, seenIds + section.sectionId))
      }

    rootSections.foldLeft((List.empty[WorkbookSection], Set.empty[String])) { case ((acc, seenIds), section) =>
      val additions = collect(section, seenIds)
      (acc ++ additions, seenIds ++ additions.map(_.sectionId))
    }._1
  }
}
