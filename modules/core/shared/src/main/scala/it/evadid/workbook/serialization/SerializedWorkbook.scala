package it.evadid.workbook.serialization

import it.evadid.workbook.abstractions.WorkbookElement
import upickle.default.*

/** A root reference plus the serialized definitions needed to reconstruct its workbook graph. */
case class SerializedWorkbook(rootId: String, elements: Seq[WorkbookElementSerializable]) derives ReadWriter {
  def toElement: WorkbookElement = WorkbookElementFactory.parseAllAsMap(elements).getOrElse(rootId,
    throw new IllegalArgumentException(s"Missing workbook root: $rootId"))
}

object SerializedWorkbook {
  def fromElement(element: WorkbookElement): SerializedWorkbook =
    SerializedWorkbook(element.elementId, collect(List(element), Map.empty).valuesIterator.map(_.toSerialized).toSeq)

  private def collect(pending: Seq[WorkbookElement], known: Map[String, WorkbookElement]): Map[String, WorkbookElement] = pending match {
    case Seq() => known
    case element +: remaining => known.get(element.elementId) match {
      case Some(previous) =>
        require(previous == element, s"Conflicting workbook element id: ${element.elementId}")
        collect(remaining, known)
      case None =>
        val prerequisites = element match {
          case section: it.evadid.workbook.elements.structure.WorkbookSection =>
            section.metadata.sectionsRequiredBefore ++ section.metadata.sectionsRecommendedBefore
          case _ => Nil
        }
        collect(element.childrenOfThisElement ++ prerequisites ++ remaining, known.updated(element.elementId, element))
    }
  }

}
