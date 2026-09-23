package it.evadid.workbook.serialization

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{LangMapContentIdType, RoleInWorkbook, TypeOfTextDisplay, WorkbookElement}
import it.evadid.workbook.elements.displayElements.{CollapsibleInstructionElement, DisplayLangMapContent}
import it.evadid.workbook.elements.interactionElements.basic.{LabeledCheckboxInteraction, LabeledNumberInteraction, MessagingInteraction, NumberType, TextInteraction}
import it.evadid.workbook.elements.interactionElements.codeTaskToggle.{AdvancedCodeRequirement, CodeTaskToggleInteraction, SketchDownloadInteraction}
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction
import it.evadid.workbook.elements.interactionElements.sortingExercise.{SortingInteraction, SortingItem}
import it.evadid.workbook.elements.interactionElements.sortingReasonExercise.{SortingReasonInteraction, SortingReasonItem}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite

class WorkbookElementFactoryRoundTripSpec extends FunSuite {
  private def content(id: String) = LanguageMapContentId(id)

  private val elements: List[WorkbookElement] = {
    val reorder = ReorderInteraction.ReorderCodeInteraction(
      "reorder-1", List("move(10)", "turn(90)"), Python, seed = 7,
      hints = List(content("hint/one")), orderConstraints = List(0 -> 1)
    )
    List(
      TextInteraction("text-1"),
      MessagingInteraction("messaging-1"),
      LabeledCheckboxInteraction("checkbox-1", content("label/checkbox")),
      LabeledNumberInteraction("number-1", content("label/number"), NumberType.FractionLike, "1.5", BigDecimal("0.5")),
      SketchDownloadInteraction("download-1", content("label/download"), "sketch.xml", "<svg/>", "reorder-1"),
      DisplayLangMapContent("display-1", content("instruction/body"), LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.MARKDOWN)),
      CollapsibleInstructionElement("collapsible-1", content("hint/title"), content("hint/body"), initiallyCollapsed = false),
      reorder,
      ReorderInteraction.ReorderMapIdInteraction("reorder-map-1", List(content("line/one"), content("line/two")), 9),
      CodeTaskToggleInteraction("toggle-1", reorder, content("title/editor"), "print('hello')", List(AdvancedCodeRequirement("print", content("hint/print"))), content("success/message")),
      SortingInteraction("sorting-1", List(content("field/one")), List(SortingItem(content("item/one"), 0, content("feedback/wrong"))), content("button/open")),
      SortingReasonInteraction("sorting-reason-1", List(content("field/one")), List(SortingReasonItem(content("item/one"), 0, content("feedback/wrong"), content("reason/prompt"))), content("button/open"))
    )
  }

  elements.foreach { element =>
    test(s"${element.elementId} round-trips through WorkbookElementFactory") {
      val serialized = element.toSerializableType
      val roundTripped = WorkbookElementFactory.materialize(WorkbookElementFactory.serializer.deserialize(WorkbookElementFactory.serializer.serialize(serialized)))
      assertEquals(roundTripped, element)
    }
  }
}
