package it.evadid.homepage.workbook.content


import com.raquo.laminar.api.L
import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.control.info.{AllWorkbookInfo, FullInfo, WorkbookConfig}
import it.evadid.homepage.workbook.htmlRenderer.*
import it.evadid.workbook.model.abstractions.*
import it.evadid.workbook.model.elements.*
import it.evadid.workbook.model.interaction.*
import it.evadid.workbook.model.interaction.basic.*
import todomove.datastructures.web.file.FileFactory

trait WorkbookFactory {

  def availableLanguages: List[HumanLanguage] = List(English, German) // todo remove default value

  def defaultSectionActiveNr: Int = 0

  def estimatedDurations: Map[WorkbookInteraction[?], Double] = Map() // todo remove default value

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

  def workbookId: String

  /*
  Control
   */
  protected def createTextInput(id: String = nextId()): WorkbookInteraction[String] = {
    WorkbookInteraction.TextInteractionBasic(id)
  }

  /*
  Structure
   */
  protected def workbook(langIdWorkbookTitle: String, sections: List[WorkbookSection]): Workbook = {
    Workbook(workbookId, LanguageMapContentId(langIdWorkbookTitle), sections, availableLanguages)
  }

  protected def section(sectionId: String, langIdSectionTitle: String, sectionContent: List[WorkbookElement]): WorkbookSection = {
    //val sectionTitleElement = LangMapContentIdType(titleMapId, WorkbookIdBasedContent(TypeOfTextContent.PLAINTEXT, RoleInWorkbook.SECTION_TITLE))
    WorkbookSection(sectionId, LanguageMapContentId(langIdSectionTitle), sectionContent)
  }

  protected def container(langIdContainerLabel: String, elements: List[WorkbookElement]): WorkbookElementGroup[WorkbookElement] = {
    //val containerTitle = LangMapContentBasedElement(LanguageMapContentId(langIdContainerLabel), LangMapContentIdType(TypeOfTextContent.PLAINTEXT, RoleInWorkbook.CONTAINER_TITLE))
    //WorkbookElementGroup(List(containerTitle) ++ elements, Some(WorkbookGroupType.EXERCISE_CONTAINER))
    ExerciseContainer(LanguageMapContentId(langIdContainerLabel), elements)
  }

  /*
  Basic Lines
   */

  protected def instructionPlaintext(langIdContent: String): WorkbookElement =
    LangMapContentBasedElement(LanguageMapContentId(langIdContent), LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextContent.PLAINTEXT))

  protected def instructionHtml(langIdContent: String): WorkbookElement =
    LangMapContentBasedElement(LanguageMapContentId(langIdContent), LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextContent.HTML))
  //HtmlInstructionElement.fromUnsafeHtmlLanguageMapId(fullInfo, textMapId)

  protected def instructionMarkdown(langIdContent: String): WorkbookElement =
    LangMapContentBasedElement(LanguageMapContentId(langIdContent), LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextContent.MARKDOWN))
  //HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, textMapId)

  def image(imageLocation: FileDescription): ImageElement = {
    ImageElement(imageLocation)
    //LangIdBasedContent(imageLocation.fullPath, LangIdBasedContent(TypeOfTextContent.URL, RoleInWorkbook.IMAGE))
    //pseudoElement(HtmlImageElement(imageLocation, fullInfo).getDomSignal)
  }

  def image(imageName: String, imgType: String = "png"): ImageElement = {
    val fileDesc: FileDescription = FileFactory.relativeToResourceFolder("workbookresources/embroidery/images/" + imageName + "." + imgType)
    image(fileDesc)
  }

  protected def checklist(langIdCheckboxLabel: String, elementId: String = nextId()): WorkbookInteraction[Boolean] = {
    LabeledCheckboxInteraction(elementId, LanguageMapContentId(langIdCheckboxLabel))
  }


}
