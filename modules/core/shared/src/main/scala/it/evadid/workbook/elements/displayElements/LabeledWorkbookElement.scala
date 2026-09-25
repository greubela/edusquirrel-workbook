package it.evadid.workbook.elements.displayElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookElement}
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement.WorkbookLabel
import it.evadid.workbook.elements.structureElements.Workbook
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}
import upickle.default.*

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
        .withElementAddedAs[WorkbookElementReference]("baseElement", element.baseElement.asRef)
        .withElementAddedAs[WorkbookLabel]("label", element.label)
    }

    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): LabeledWorkbookElement[WorkbookElement] = {

      LabeledWorkbookElement(
        element.elementId,
        element.getAndResolveWorkbookElement[WorkbookElement]("baseElement", parsedElements),
        element.getElementAs[WorkbookLabel]("label")
      )
    }
  }


  case class WorkbookLabel(contentId: LanguageMapContentId, labelType: LabelType) derives ReadWriter

  sealed trait LabelType(val associatedCssString: String) derives ReadWriter

  case object SafetyLabel extends LabelType("instruction-safety");

  case object GoalLabel extends LabelType("instruction-goal");

  case object TaskLabel extends LabelType("instruction-task");

  case object HintLabel extends LabelType("instruction-hint")

}
