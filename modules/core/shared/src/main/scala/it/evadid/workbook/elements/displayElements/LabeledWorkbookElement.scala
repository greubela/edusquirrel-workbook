package it.evadid.workbook.elements.displayElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookElement}
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement.WorkbookLabel
import it.evadid.workbook.elements.structureElements.Workbook
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}

case class LabeledWorkbookElement[T <: WorkbookElement](override val elementId: String, baseElement: T, label: WorkbookLabel) extends WorkbookDisplayElement {
  override lazy val childrenOfThisElement: List[WorkbookElement] = List(baseElement)

  override val associatedFactory: WorkbookElementFactory[? <: WorkbookElement] = LabeledWorkbookElement.factory
}

object LabeledWorkbookElement {

  lazy val factory: WorkbookElementFactory[LabeledWorkbookElement[WorkbookElement]] = new WorkbookElementFactory[LabeledWorkbookElement[WorkbookElement]](){
    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] =
      Set(element.getElementAsWorkbookReference("baseElement").referencedId)

    override def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable): Seq[WorkbookElementSerializable] = List()

    override def toSerializableElement(element: LabeledWorkbookElement[WorkbookElement]): WorkbookElementSerializable = {
      toFactoryBase(element)
        .withReferenceAdded("baseElement", element.baseElement.asRef)
        .withContentIdAdded("label", element.label.contentId)
        .withElementAdded("labelType", element.label.labelType.toString)
    }

    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): LabeledWorkbookElement[WorkbookElement] = {
      val kind = element.getOptionalElementAsString("labelType", "HintLabel") match {
        case "SafetyLabel" => SafetyLabel;
        case "GoalLabel" => GoalLabel;
        case "TaskLabel" => TaskLabel;
        case "HintLabel" => HintLabel
      };
      LabeledWorkbookElement(
        element.elementId,
        element.getAndResolveWorkbookElement[WorkbookElement]("baseElement", parsedElements),
        WorkbookLabel(element.getElementAsContentId("label"), kind)
      )
    }
  }

  case class WorkbookLabel(contentId: LanguageMapContentId, labelType: LabelType);

  sealed trait LabelType(val associatedCssString: String);

  case object SafetyLabel extends LabelType("instruction-safety");

  case object GoalLabel extends LabelType("instruction-goal");

  case object TaskLabel extends LabelType("instruction-task");

  case object HintLabel extends LabelType("instruction-hint")

}
