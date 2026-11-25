package it.evadid.homepage.workbook.content

import it.evadid.workbook.abstractions.InteractionFormat.{LabeledCheckboxInteractionFormat, TextInteractionFormat}


import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.*
import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.TypeOfTextDisplay.URL_RELATIVE_TO_WORKBOOK_RESOURCES
import it.evadid.workbook.elements.displayElements.*
import it.evadid.workbook.elements.displayElements.WorkbookImageElement.LanguageMapBasedWorkbookImageElement
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement.{LabelType, WorkbookLabel}
import it.evadid.workbook.elements.interactionElements.basic.*
import it.evadid.workbook.elements.interactionElements.basic.LabeledNumberInteraction.NumberType
import it.evadid.workbook.elements.interactionElements.codeTaskToggle.{AdvancedCodeRequirement, CodeTaskToggleInteraction, SketchDownloadInteraction}
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction
import it.evadid.workbook.elements.interactionElements.sorting.sortingExercise.{SortingInteraction, SortingItem}
import it.evadid.workbook.elements.interactionElements.sorting.sortingReasonExercise.{SortingReasonInteraction, SortingReasonItem}
import it.evadid.workbook.elements.structureElements.*
import it.evadid.workbook.elements.structureElements.Workbook.WorkbookMetadata
import it.evadid.workbook.elements.structureElements.WorkbookSection.WorkbookSectionMetadata

trait WorkbookFactory {

  def availableLanguages: Seq[HumanLanguage] = List(English, German) // todo remove default value

  def defaultSectionActiveNr: Int = 0

  def estimatedDurations: Map[String, Double] = Map() // todo remove default value

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
  protected def createTextInput(id: String = nextId()): WorkbookInteractionElement[TextInteractionFormat.type] = {
    TextInteraction(id)
  }

  /*
  Structure
   */
  protected def workbook(langIdWorkbookTitle: String, sections: Seq[WorkbookSection], user: User): Workbook = {
    Workbook(
      workbookId,
      WorkbookMetadata(
        Set(user),
        Set(),
        LanguageMapContentId(langIdWorkbookTitle),
        availableLanguages),
      sections)
  }

  protected def section(sectionId: String, langIdSectionTitle: String, sectionContent: Seq[WorkbookElement]): WorkbookSection = {
    //val sectionTitleElement = LangMapContentIdType(titleMapId, WorkbookIdBasedContent(TypeOfTextDisplay.PLAINTEXT, RoleInWorkbook.SECTION_TITLE))
    WorkbookSection(sectionId, WorkbookSectionMetadata(LanguageMapContentId(langIdSectionTitle)), sectionContent)
  }

  protected def container(langIdContainerLabel: String, elements: Seq[WorkbookElement]): WorkbookStructureElement[WorkbookElement] = {
    //val containerTitle = LangMapContentBasedElement(LanguageMapContentId(langIdContainerLabel), LangMapContentIdType(TypeOfTextDisplay.PLAINTEXT, RoleInWorkbook.CONTAINER_TITLE))
    //WorkbookElementGroup(List(containerTitle) ++ elements, Some(WorkbookGroupType.EXERCISE_CONTAINER))
    ExerciseContainer(nextId("container"), LanguageMapContentId(langIdContainerLabel), elements)
  }

  /** Text-choice authoring convenience; definitions and saved answers use WorkbookContent. Choices are graded when
   * the learner checks them, not on every selection. */
  protected def contentChoice(elementId: String, instruction: LanguageMapContentId, options: Seq[LanguageMapContentId],
      allowMultiple: Boolean = false, expected: Option[Seq[Int]] = None): it.evadid.workbook.elements.interactionElements.choice.WorkbookContentChoiceInteraction = {
    import it.evadid.core.datastructures.language.WorkbookContent
    import it.evadid.workbook.elements.interactionElements.choice.WorkbookContentChoiceInteraction
    import WorkbookContentChoiceInteraction.*
    import it.evadid.workbook.abstractions.grading.*
    require(expected.forall(indices => indices.distinct == indices && indices.forall(options.indices.contains) && (allowMultiple || indices.size <= 1)),
      "Expected choices must belong to the exercise")
    WorkbookContentChoiceInteraction(elementId, ChoiceConfig(instruction, Some(allowMultiple), GraderRunningBehavior.RUN_ONLY_MANUALLY),
      options.zipWithIndex.map { (id, index) =>
        ChoiceOptionWorkbookContent(WorkbookContent(id), expected.map { indices =>
          val correct = indices.contains(index)
          GradingForChoice(if correct then GradingStatus.Correct() else GradingStatus.Incorrect(),
            LanguageMapContentId(if correct then "digitalWorkbooks/correct" else "digitalWorkbooks/tryAgain"))
        })
      })
  }

  /*
  Basic Lines
   */

  protected def instructionPlaintext(langIdContent: String): WorkbookElement =
    DisplayLangMapContent(nextId(), LanguageMapContentId(langIdContent), LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.PLAINTEXT))

  protected def instructionHtml(langIdContent: String): WorkbookElement =
    DisplayLangMapContent(nextId(), LanguageMapContentId(langIdContent), LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.HTML))
  //HtmlInstructionElement.fromUnsafeHtmlLanguageMapId(fullInfo, textMapId)

  protected def instructionMarkdown(langIdContent: String): WorkbookElement =
    DisplayLangMapContent(nextId(), LanguageMapContentId(langIdContent), LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.MARKDOWN))
  //HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, textMapId)

  def imageResources(idWithImageLocation: LanguageMapContentId): WorkbookImageElement = {
    LanguageMapBasedWorkbookImageElement(nextId("img"), idWithImageLocation, URL_RELATIVE_TO_WORKBOOK_RESOURCES)
  }

  protected def labeledInstruction(titleMapId: String, bodyMapId: String, labelType: LabelType): LabeledWorkbookElement[WorkbookElement] = {
    val instruction: WorkbookElement = instructionMarkdown(bodyMapId)
    LabeledWorkbookElement(nextId(), instruction, WorkbookLabel(LanguageMapContentId(titleMapId), labelType))
  }

  protected def instructionLabeledPair(titleMapId: String, bodyMapId: String, labelType: LabelType): LabeledWorkbookElement[WorkbookElement] =
    labeledInstruction(titleMapId, bodyMapId, labelType)

  protected def instructionCollapsibleHint(titleMapId: String, bodyMapId: String, initiallyCollapsed: Boolean = true): CollapsibleInstructionElement =
    CollapsibleInstructionElement(nextId(), LanguageMapContentId(titleMapId), LanguageMapContentId(bodyMapId), initiallyCollapsed)


  protected def checklist(langIdCheckboxLabel: String, elementId: String = nextId()): WorkbookInteractionElement[LabeledCheckboxInteractionFormat.type] = {
    LabeledCheckboxInteraction(elementId, LanguageMapContentId(langIdCheckboxLabel))
  }

  protected def numberInput(
                             langIdNumberLabel: String,
                             numberType: NumberType,
                             defaultValue: String = "0",
                             diff: BigDecimal = BigDecimal(1),
                             elementId: String = nextId()
                           ): WorkbookInteractionElement[TextInteractionFormat.type] = {
    LabeledNumberInteraction(elementId, LanguageMapContentId(langIdNumberLabel), numberType, defaultValue, diff)
  }

  /*
  Common Interactions
   */

  protected def codeReorder(
                             baseId: String,
                             snippets: Seq[String],
                             programmingLanguage: ProgrammingLanguage,
                             hints: Seq[LanguageMapContentId] = List.empty,
                             orderConstraints: Seq[(Int, Int)] = Nil
                           ): ReorderInteraction.ReorderCodeInteraction = {
    ReorderInteraction.ReorderCodeInteraction(baseId, snippets, programmingLanguage, hints = hints, orderConstraints = orderConstraints)
  }

  protected def codeTaskToggle(
                                reorderId: String,
                                snippets: Seq[String],
                                codeEditorTitle: String,
                                advancedCodeTemplate: String,
                                hints: Seq[LanguageMapContentId] = List.empty,
                                orderConstraints: Seq[(Int, Int)] = Nil,
                                advancedRequirements: Seq[AdvancedCodeRequirement] = Nil,
                                advancedSuccessMessage: String = "basic/advancedCodeFeedbackSuccess"
                              ): CodeTaskToggleInteraction = {
    CodeTaskToggleInteraction(
      nextId(reorderId + "-toggle"),
      codeReorder(reorderId, snippets, AppLanguage.C, hints, orderConstraints),
      LanguageMapContentId(codeEditorTitle),
      advancedCodeTemplate,
      advancedRequirements,
      LanguageMapContentId(advancedSuccessMessage)
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
                                 fieldKeys: Seq[String],
                                 items: Seq[(String, Int, String)]
                               ): SortingInteraction = {
    SortingInteraction(
      elementId = id,
      fields = fieldKeys.map(LanguageMapContentId.apply),
      items = items.map { case (labelKey, correctFieldIndex, errorKey) =>
        SortingItem(LanguageMapContentId(labelKey), correctFieldIndex, LanguageMapContentId(errorKey))
      }
    )
  }

  protected def sortingReasonExercise(
                                       id: String,
                                       fieldKeys: Seq[String],
                                       items: Seq[(String, Int, String, String)]
                                     ): SortingReasonInteraction = {
    SortingReasonInteraction(
      elementId = id,
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
