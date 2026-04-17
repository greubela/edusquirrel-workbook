package content

import com.raquo.laminar.api.L
import datastructures.web.file.FileDescription
import workbook.htmlElements.basic.{HtmlImageElement, HtmlPlaintextInstructionElement, HtmlUnsafeHtmlInstructionElement}
import workbook.htmlElements.container.HtmlExerciseContainer
import workbook.htmlElements.interactions.{HtmlBasicCheckboxInteraction, HtmlBasicTextInteraction}
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.{Workbook, WorkbookSection}
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}

trait WorkbookFactory {

  private var id = 0

  protected def nextId(prefix: String = "auto-id"): String = {
    id = id + 1
    prefix + "-" + id
  }

  def workbookInfo: AllWorkbookInfo

  def workbookInfoVar: L.Var[WorkbookInfo] = workbookInfo.workbookInfoVar

  def createWorkbook: Workbook

  protected def createTextInput(id: String = nextId()): HtmlWorkbookElement = {
    HtmlBasicTextInteraction(workbookInfo, id)
  }

  protected def container(elements: List[HtmlWorkbookElement]): HtmlExerciseContainer = {
    HtmlExerciseContainer(workbookInfo, elements)
  }

  protected def section(titleIdMap: String, exercises: List[HtmlExerciseContainer]): WorkbookSection = {
    WorkbookSection(
      workbookInfo,
      titleIdMap,
      exercises
    )
  }

  def image(imageLocation: FileDescription): HtmlWorkbookElement = pseudoElement(HtmlImageElement(imageLocation, workbookInfo).getDomSignal)

  protected def workbook(titleMapId: String, sections: List[WorkbookSection]): Workbook = Workbook(workbookInfo, titleMapId, sections)

  protected def instructionPlaintext(textMapId: String): HtmlWorkbookElement = HtmlPlaintextInstructionElement(workbookInfo, textMapId)
  protected def instructionHtml(textMapId: String): HtmlWorkbookElement = HtmlUnsafeHtmlInstructionElement(workbookInfo, textMapId)

  protected def checklist(labelMapId: String, elementIdd: String = nextId()): HtmlWorkbookElement =
      HtmlBasicCheckboxInteraction(
        workbookInfo = workbookInfo,
        id = elementIdd,
        labelLanguageMapId = labelMapId
      )


  private def pseudoElement(dom: L.Signal[L.Element]): HtmlWorkbookElement = new HtmlWorkbookElement {
    override def workbookInfo: AllWorkbookInfo = WorkbookFactory.this.workbookInfo

    override def getDomElement(): L.Element = L.div(L.cls := "workbook-element exercise-instruction", L.child <-- dom)
  }

  private def pseudoElement(dom: L.Element): HtmlWorkbookElement = new HtmlWorkbookElement {
    override def workbookInfo: AllWorkbookInfo = WorkbookFactory.this.workbookInfo

    override def getDomElement(): L.Element = L.div(L.cls := "workbook-element exercise-instruction", dom)
  }

}
