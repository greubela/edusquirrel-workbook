package content

import com.raquo.laminar.api.L
import datastructures.web.file.FileDescription
import it.evadid.core.datastructures.language.AppLanguage.*
import workbook.htmlElements.basic.*
import workbook.htmlElements.container.HtmlExerciseContainer
import workbook.htmlElements.interactions.{HtmlBasicCheckboxInteraction, HtmlBasicTextInteraction}
import workbook.model.abstractions.{HtmlWorkbookElement, WorkbookInteraction}
import workbook.model.info.*
import workbook.model.{Workbook, WorkbookSection}
trait WorkbookFactory {


  def availableLanguages: List[HumanLanguage] = List(English, German) // todo remove default value
  def defaultSectionActiveNr: Int  = 0
  def estimatedDurations: Map[WorkbookInteraction[?], Double] = Map()  // todo remove default value

  def createEverything: AllWorkbookInfo = {
    val workbook = createWorkbook
    val section: Option[WorkbookSection] = workbook.sections.lift(defaultSectionActiveNr)
    //println("section active: " + section + "(" + workbook.sections.size + ")")
    val config = WorkbookConfig(section)
    AllWorkbookInfo(workbook, config, estimatedDurations)
  }


  private var id = 0

  protected def nextId(prefix: String = "auto-id"): String = {
    id = id + 1
    prefix + "-" + id
  }

  def fullInfo: FullInfo

  def createWorkbook: Workbook

  protected def createTextInput(id: String = nextId()): WorkbookInteraction[String] = {
    HtmlBasicTextInteraction(fullInfo, id)
  }

  protected def container(elements: List[HtmlWorkbookElement]): HtmlExerciseContainer = {
    HtmlExerciseContainer(fullInfo, elements)
  }

  protected def section(titleIdMap: String, exercises: List[HtmlExerciseContainer]): WorkbookSection = {
    WorkbookSection(
      fullInfo,
      titleIdMap,
      exercises
    )
  }

  def image(imageLocation: FileDescription): HtmlWorkbookElement = pseudoElement(HtmlImageElement(imageLocation, fullInfo).getDomSignal)

  protected def workbook(titleMapId: String, sections: List[WorkbookSection]): Workbook = Workbook(fullInfo, titleMapId, sections, availableLanguages)

  protected def instructionPlaintext(textMapId: String): HtmlWorkbookElement = HtmlInstructionElement.fromPlaintextLanguageMapId(fullInfo, textMapId)

  protected def instructionHtml(textMapId: String): HtmlWorkbookElement = HtmlInstructionElement.fromUnsafeHtmlLanguageMapId(fullInfo, textMapId)

  protected def instructionMarkdown(textMapId: String): HtmlInstructionElement = HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, textMapId)

  protected def instructionLabeledPair(titleMapId: String, bodyMapId: String, cssClass: String = "instruction-pair"): HtmlInstructionLabeledPair =
    HtmlInstructionLabeledPair(fullInfo, titleMapId, bodyMapId, cssClass)

  protected def checklist(labelMapId: String, elementIdd: String = nextId()): HtmlWorkbookElement =
    HtmlBasicCheckboxInteraction(
      fullInfo = fullInfo,
      id = elementIdd,
      labelLanguageMapId = labelMapId
    )


  protected def pseudoElement(dom: L.Signal[L.Element]): HtmlWorkbookElement = new HtmlWorkbookElement {
    override def fullInfo: FullInfo = WorkbookFactory.this.fullInfo

    override def getDomElement(): L.Element = L.div(L.cls := "workbook-element exercise-instruction", L.child <-- dom)
  }

  protected def pseudoElement(dom: L.Element): HtmlWorkbookElement = new HtmlWorkbookElement {
    override def fullInfo: FullInfo = WorkbookFactory.this.fullInfo

    override def getDomElement(): L.Element = L.div(L.cls := "workbook-element exercise-instruction", dom)
  }

}
