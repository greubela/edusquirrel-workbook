package it.evadid.workbook.serialization

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.elements.structureElements.WorkbookSection
import it.evadid.workbook.model.abstractions.WorkbookElement
import upickle.ReadWriter
import upickle.default.*

/**
 * Stable reference to a workbook element body stored outside a section.
 *
 * Sections keep only these references so section dependency metadata never
 * duplicates nested element bodies.
 */
case class ElementRef(elementId: String) extends AnyVal

object ElementRef {
  given readWriter: ReadWriter[ElementRef] =
    readwriter[String].bimap[ElementRef](_.elementId, ElementRef.apply)
}

/**
 * Serialized shape for WorkbookSection.
 *
 * Dependency sections are represented by ids only. The section body is the list
 * of top-level element references that belongs to this section, while the
 * referenced element payloads live in the caller-owned element registry.
 */
case class SerializedWorkbookSection(
                                      sectionId: String,
                                      sectionTitle: LanguageMapContentId,
                                      sectionContentRefs: List[ElementRef],
                                      sectionsRequiredBeforeIds: List[String],
                                      sectionsRecommendedBeforeIds: List[String]
                                    ) {

  def toWorkbookSection(
                         elementsByRef: Map[ElementRef, WorkbookElement],
                         sectionsById: Map[String, WorkbookSection]
                       ): WorkbookSection = {
    val sectionContent = sectionContentRefs.map { ref =>
      elementsByRef.getOrElse(ref, throw new NoSuchElementException(s"Missing workbook element for section '${sectionId}' ref '${ref.elementId}'"))
    }
    val requiredBefore = sectionsRequiredBeforeIds.map { dependencyId =>
      sectionsById.getOrElse(dependencyId, throw new NoSuchElementException(s"Missing required section dependency '${dependencyId}' for section '${sectionId}'"))
    }
    val recommendedBefore = sectionsRecommendedBeforeIds.map { dependencyId =>
      sectionsById.getOrElse(dependencyId, throw new NoSuchElementException(s"Missing recommended section dependency '${dependencyId}' for section '${sectionId}'"))
    }

    WorkbookSection(sectionId, sectionTitle, sectionContent, requiredBefore, recommendedBefore)
  }

  def toJson(pretty: Boolean = false): String =
    if (pretty) write(this, indent = 2) else write(this)
}

object SerializedWorkbookSection {
  given languageMapContentIdReadWriter: ReadWriter[LanguageMapContentId] =
    readwriter[String].bimap[LanguageMapContentId](_.fullId, LanguageMapContentId.apply)

  given readWriter: ReadWriter[SerializedWorkbookSection] = macroRW

  def fromWorkbookSection(
                           section: WorkbookSection,
                           elementRefFor: WorkbookElement => ElementRef
                         ): SerializedWorkbookSection =
    SerializedWorkbookSection(
      sectionId = section.sectionId,
      sectionTitle = section.sectionTitle,
      sectionContentRefs = section.sectionContent.map(elementRefFor),
      sectionsRequiredBeforeIds = section.sectionsRequiredBefore.map(_.sectionId),
      sectionsRecommendedBeforeIds = section.sectionsRecommendedBefore.map(_.sectionId)
    )

  def fromJson(json: String): SerializedWorkbookSection = read[SerializedWorkbookSection](json)
}
