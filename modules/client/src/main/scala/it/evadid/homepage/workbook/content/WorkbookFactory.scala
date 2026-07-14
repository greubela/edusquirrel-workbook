package it.evadid.homepage.workbook.content


import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.control.model.*
import it.evadid.workbook.abstractions.{LangMapContentIdType, RoleInWorkbook, TypeOfTextDisplay, WorkbookElement, WorkbookInteractionElement, WorkbookStructureElement}
import it.evadid.workbook.elements.displayElements.ImageElement.FileBasedImageElement
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement.{LabelType, WorkbookLabel}
import it.evadid.workbook.elements.displayElements.*
import it.evadid.workbook.elements.interactionElements.basic.*
import it.evadid.workbook.elements.interactionElements.codeTaskToggle.{CodeTaskToggleInteraction, SketchDownloadInteraction}
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction
import it.evadid.workbook.elements.interactionElements.sortingExercise.{SortingInteraction, SortingItem}
import it.evadid.workbook.elements.interactionElements.sortingReasonExercise.{SortingReasonInteraction, SortingReasonItem}
import it.evadid.workbook.elements.structureElements.*
import todomove.datastructures.web.file.FileFactory

trait WorkbookFactory {

  def availableLanguages: List[HumanLanguage] = List(English, German) // todo remove default value

  def defaultSectionActiveNr: Int = 0

  def estimatedDurations: Map[WorkbookInteractionElement[?], Double] = Map() // todo remove default value

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
  protected def createTextInput(id: String = nextId()): WorkbookInteractionElement[String] = {
    TextInteraction(id)
  }

  /*
  Structure
   */
  protected def workbook(langIdWorkbookTitle: String, sections: List[WorkbookSection]): Workbook = {
    Workbook(workbookId, LanguageMapContentId(langIdWorkbookTitle), sections, availableLanguages)
  }

  protected def section(sectionId: String, langIdSectionTitle: String, sectionContent: List[WorkbookElement]): WorkbookSection = {
    //val sectionTitleElement = LangMapContentIdType(titleMapId, WorkbookIdBasedContent(TypeOfTextDisplay.PLAINTEXT, RoleInWorkbook.SECTION_TITLE))
    WorkbookSection(sectionId, LanguageMapContentId(langIdSectionTitle), sectionContent)
  }

  protected def container(langIdContainerLabel: String, elements: List[WorkbookElement]): WorkbookStructureElement[WorkbookElement] = {
    //val containerTitle = LangMapContentBasedElement(LanguageMapContentId(langIdContainerLabel), LangMapContentIdType(TypeOfTextDisplay.PLAINTEXT, RoleInWorkbook.CONTAINER_TITLE))
    //WorkbookElementGroup(List(containerTitle) ++ elements, Some(WorkbookGroupType.EXERCISE_CONTAINER))
    ExerciseContainer(LanguageMapContentId(langIdContainerLabel), elements)
  }

  /*
  Basic Lines
   */

  protected def instructionPlaintext(langIdContent: String): WorkbookElement =
    DisplayLangMapContent(LanguageMapContentId(langIdContent), LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.PLAINTEXT))

  protected def instructionHtml(langIdContent: String): WorkbookElement =
    DisplayLangMapContent(LanguageMapContentId(langIdContent), LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.HTML))
  //HtmlInstructionElement.fromUnsafeHtmlLanguageMapId(fullInfo, textMapId)

  protected def instructionMarkdown(langIdContent: String): WorkbookElement =
    DisplayLangMapContent(LanguageMapContentId(langIdContent), LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.MARKDOWN))
  //HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, textMapId)

  def image(imageLocation: FileDescription): ImageElement = {
    FileBasedImageElement(imageLocation)
    //LangIdBasedContent(imageLocation.fullPath, LangIdBasedContent(TypeOfTextDisplay.URL, RoleInWorkbook.IMAGE))
    //pseudoElement(HtmlImageElement(imageLocation, fullInfo).getDomSignal)
  }

  protected def labeledInstruction(titleMapId: String, bodyMapId: String, labelType: LabelType): LabeledWorkbookElement[WorkbookElement] = {
    val instruction: WorkbookElement = instructionHtml(bodyMapId)
    LabeledWorkbookElement[WorkbookElement](instruction, WorkbookLabel(LanguageMapContentId(titleMapId), labelType))
  }

  protected def instructionLabeledPair(titleMapId: String, bodyMapId: String, labelType: LabelType): LabeledWorkbookElement[WorkbookElement] =
    labeledInstruction(titleMapId, bodyMapId, labelType)

  protected def instructionCollapsibleHint(titleMapId: String, bodyMapId: String, initiallyCollapsed: Boolean = true): CollapsibleInstructionElement =
    CollapsibleInstructionElement(LanguageMapContentId(titleMapId), LanguageMapContentId(bodyMapId), initiallyCollapsed)

  def image(imageName: String, imgType: String = "png"): ImageElement = {
    val fileDesc: FileDescription = FileFactory.relativeToResourceFolder("workbookresources/embroidery/images/" + imageName + "." + imgType)
    image(fileDesc)
  }

  protected def checklist(langIdCheckboxLabel: String, elementId: String = nextId()): WorkbookInteractionElement[Boolean] = {
    LabeledCheckboxInteraction(elementId, LanguageMapContentId(langIdCheckboxLabel))
  }

  protected def numberInput(
                             langIdNumberLabel: String,
                             numberType: NumberType,
                             defaultValue: String = "0",
                             diff: BigDecimal = BigDecimal(1),
                             elementId: String = nextId()
                           ): WorkbookInteractionElement[String] = {
    LabeledNumberInteraction(elementId, LanguageMapContentId(langIdNumberLabel), numberType, defaultValue, diff)
  }

  /*
  Common Interactions
   */

  protected def codeReorder(
                             baseId: String,
                             snippets: List[String],
                             programmingLanguage: ProgrammingLanguage,
                             hints: List[LanguageMapContentId] = List.empty,
                             orderConstraints: List[(Int, Int)] = Nil
                           ): ReorderInteraction.ReorderCodeInteraction = {
    ReorderInteraction.ReorderCodeInteraction(baseId, snippets, programmingLanguage, hints = hints, orderConstraints = orderConstraints)
  }

  protected def codeTaskToggle(
                                reorderId: String,
                                snippets: List[String],
                                codeEditorTitle: String,
                                advancedCodeTemplate: String,
                                hints: List[LanguageMapContentId] = List.empty,
                                orderConstraints: List[(Int, Int)] = Nil
                              ): CodeTaskToggleInteraction = {
    CodeTaskToggleInteraction(
      nextId(reorderId + "-toggle"),
      codeReorder(reorderId, snippets, AppLanguage.C, hints, orderConstraints),
      codeEditorTitle,
      advancedCodeTemplate
    )
  }

  protected def sketchDownload(
                                buttonLabelKey: String,
                                sketchContent: String,
                                filename: String,
                                id: String,
                                unlockWhenReorderCorrect: String
                              ): SketchDownloadInteraction = {
    SketchDownloadInteraction(
      nextId(id),
      LanguageMapContentId(buttonLabelKey),
      filename,
      sketchContent,
      unlockWhenReorderCorrect
    )
  }

  protected def sortingExercise(
    id: String,
    fieldKeys: List[String],
    items: List[(String, Int, String)]
  ): SortingInteraction = {
    SortingInteraction(
      id = id,
      fields = fieldKeys.map(LanguageMapContentId.apply),
      items = items.map { case (labelKey, correctFieldIndex, errorKey) =>
        SortingItem(LanguageMapContentId(labelKey), correctFieldIndex, LanguageMapContentId(errorKey))
      }
    )
  }

  protected def sortingReasonExercise(
    id: String,
    fieldKeys: List[String],
    items: List[(String, Int, String, String)]
  ): SortingReasonInteraction = {
    SortingReasonInteraction(
      id = id,
      fields = fieldKeys.map(LanguageMapContentId.apply),
      items = items.map { case (labelKey, correctFieldIndex, errorKey, reasonPromptKey) =>
        SortingReasonItem(
          LanguageMapContentId(labelKey),
          correctFieldIndex,
          LanguageMapContentId(errorKey),
          LanguageMapContentId(reasonPromptKey)
        )
      }
    )
  }


}
