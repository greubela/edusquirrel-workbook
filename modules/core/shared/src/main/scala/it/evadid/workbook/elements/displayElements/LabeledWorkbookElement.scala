package it.evadid.workbook.elements.displayElements
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookElement}
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement.WorkbookLabel
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
case class LabeledWorkbookElement[T <: WorkbookElement](override val elementId: String, baseElement: WorkbookElement, label: WorkbookLabel) extends WorkbookDisplayElement {
 override lazy val childrenOfThisElement = List(baseElement)
 override def toSerializableType = toFactoryBase.withSerializedElementAdded("baseElement", baseElement).withContentIdAdded("label", label.contentId).withElementAdded("labelType", label.labelType.toString)
}
object LabeledWorkbookElement { case class WorkbookLabel(contentId: LanguageMapContentId, labelType: LabelType); sealed trait LabelType(val associatedCssString: String); case object SafetyLabel extends LabelType("instruction-safety"); case object GoalLabel extends LabelType("instruction-goal"); case object TaskLabel extends LabelType("instruction-task"); case object HintLabel extends LabelType("instruction-hint")
 def fromFactory(f: WorkbookElementFactory): LabeledWorkbookElement[WorkbookElement] = { val kind = f.getElementAsString("labelType") match { case "SafetyLabel" => SafetyLabel; case "GoalLabel" => GoalLabel; case "TaskLabel" => TaskLabel; case "HintLabel" => HintLabel }; LabeledWorkbookElement(f.elementId, f.getElementAsSerializedElement("baseElement"), WorkbookLabel(f.getElementAsContentId("label"), kind)) }
}
