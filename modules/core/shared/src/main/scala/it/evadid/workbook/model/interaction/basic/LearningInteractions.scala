package it.evadid.workbook.model.interaction.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction
import upickle.default.{ReadWriter, macroRW}

/** State shared by single- and multiple-choice workbook interactions. */
case class ChoiceSelectionState(selectedOptionIndices: List[Int]) {
  def sanitized(optionCount: Int, allowMultiple: Boolean): ChoiceSelectionState = {
    val valid = selectedOptionIndices.distinct.filter(index => index >= 0 && index < optionCount)
    ChoiceSelectionState(if allowMultiple then valid else valid.take(1))
  }

  def isSelected(optionIndex: Int, optionCount: Int, allowMultiple: Boolean): Boolean =
    sanitized(optionCount, allowMultiple).selectedOptionIndices.contains(optionIndex)

  def withSingleSelection(optionIndex: Int, optionCount: Int): ChoiceSelectionState =
    if optionIndex >= 0 && optionIndex < optionCount then ChoiceSelectionState(List(optionIndex)) else sanitized(optionCount, allowMultiple = false)

  def withToggledSelection(optionIndex: Int, optionCount: Int): ChoiceSelectionState = {
    val clean = sanitized(optionCount, allowMultiple = true).selectedOptionIndices
    if optionIndex < 0 || optionIndex >= optionCount then ChoiceSelectionState(clean)
    else if clean.contains(optionIndex) then ChoiceSelectionState(clean.filterNot(_ == optionIndex))
    else ChoiceSelectionState(clean :+ optionIndex)
  }
}

object ChoiceSelectionState {
  private given ReadWriter[ChoiceSelectionState] = macroRW
  val serializer: Serializer[ChoiceSelectionState] = Serializer.fromUpickleJson(summon[ReadWriter[ChoiceSelectionState]])
}

sealed trait ChoiceSelectionInteraction extends WorkbookInteraction[ChoiceSelectionState] {
  def prompt: Option[LanguageMapContentId]
  def options: List[LanguageMapContentId]
  def allowMultiple: Boolean

  override val defaultValue: ChoiceSelectionState = ChoiceSelectionState(List.empty)
  override val serializer: Serializer[ChoiceSelectionState] = ChoiceSelectionState.serializer
}

case class SingleChoiceInteraction(
                                    override val id: String,
                                    override val options: List[LanguageMapContentId],
                                    override val prompt: Option[LanguageMapContentId] = None
                                  ) extends ChoiceSelectionInteraction {
  override val allowMultiple: Boolean = false
}

case class MultipleChoiceInteraction(
                                      override val id: String,
                                      override val options: List[LanguageMapContentId],
                                      override val prompt: Option[LanguageMapContentId] = None
                                    ) extends ChoiceSelectionInteraction {
  override val allowMultiple: Boolean = true
}

/** One selected right-side item for every left-side item; None means unanswered. */
case class MatchingInteractionState(selectedRightIndicesByLeftIndex: List[Option[Int]]) {
  def sanitized(leftCount: Int, rightCount: Int): MatchingInteractionState = {
    val padded = selectedRightIndicesByLeftIndex.take(leftCount).padTo(leftCount, None)
    MatchingInteractionState(padded.map(_.filter(index => index >= 0 && index < rightCount)))
  }

  def selectedRightIndex(leftIndex: Int, leftCount: Int, rightCount: Int): Option[Int] =
    sanitized(leftCount, rightCount).selectedRightIndicesByLeftIndex.lift(leftIndex).flatten

  def withMatch(leftIndex: Int, rightIndex: Option[Int], leftCount: Int, rightCount: Int): MatchingInteractionState = {
    val clean = sanitized(leftCount, rightCount).selectedRightIndicesByLeftIndex
    if leftIndex < 0 || leftIndex >= leftCount then MatchingInteractionState(clean)
    else MatchingInteractionState(clean.updated(leftIndex, rightIndex.filter(index => index >= 0 && index < rightCount)))
  }
}

object MatchingInteractionState {
  private given ReadWriter[MatchingInteractionState] = macroRW
  val serializer: Serializer[MatchingInteractionState] = Serializer.fromUpickleJson(summon[ReadWriter[MatchingInteractionState]])
}

case class MatchingInteraction(
                                override val id: String,
                                leftItems: List[LanguageMapContentId],
                                rightItems: List[LanguageMapContentId]
                              ) extends WorkbookInteraction[MatchingInteractionState] {
  override val defaultValue: MatchingInteractionState = MatchingInteractionState(List.fill(leftItems.size)(None))
  override val serializer: Serializer[MatchingInteractionState] = MatchingInteractionState.serializer
}

/** One selected category for every item; None means unanswered. */
case class CategorizationInteractionState(selectedCategoryIndicesByItemIndex: List[Option[Int]]) {
  def sanitized(itemCount: Int, categoryCount: Int): CategorizationInteractionState = {
    val padded = selectedCategoryIndicesByItemIndex.take(itemCount).padTo(itemCount, None)
    CategorizationInteractionState(padded.map(_.filter(index => index >= 0 && index < categoryCount)))
  }

  def selectedCategoryIndex(itemIndex: Int, itemCount: Int, categoryCount: Int): Option[Int] =
    sanitized(itemCount, categoryCount).selectedCategoryIndicesByItemIndex.lift(itemIndex).flatten

  def withCategory(itemIndex: Int, categoryIndex: Option[Int], itemCount: Int, categoryCount: Int): CategorizationInteractionState = {
    val clean = sanitized(itemCount, categoryCount).selectedCategoryIndicesByItemIndex
    if itemIndex < 0 || itemIndex >= itemCount then CategorizationInteractionState(clean)
    else CategorizationInteractionState(clean.updated(itemIndex, categoryIndex.filter(index => index >= 0 && index < categoryCount)))
  }
}

object CategorizationInteractionState {
  private given ReadWriter[CategorizationInteractionState] = macroRW
  val serializer: Serializer[CategorizationInteractionState] = Serializer.fromUpickleJson(summon[ReadWriter[CategorizationInteractionState]])
}

case class CategorizationInteraction(
                                      override val id: String,
                                      items: List[LanguageMapContentId],
                                      categories: List[LanguageMapContentId]
                                    ) extends WorkbookInteraction[CategorizationInteractionState] {
  override val defaultValue: CategorizationInteractionState = CategorizationInteractionState(List.fill(items.size)(None))
  override val serializer: Serializer[CategorizationInteractionState] = CategorizationInteractionState.serializer
}

case class FillInBlanksState(blankValues: List[String]) {
  def sanitized(blankCount: Int): FillInBlanksState = FillInBlanksState(blankValues.take(blankCount).padTo(blankCount, ""))

  def blankValue(blankIndex: Int, blankCount: Int): String = sanitized(blankCount).blankValues.lift(blankIndex).getOrElse("")

  def withBlankValue(blankIndex: Int, value: String, blankCount: Int): FillInBlanksState = {
    val clean = sanitized(blankCount).blankValues
    if blankIndex < 0 || blankIndex >= blankCount then FillInBlanksState(clean) else FillInBlanksState(clean.updated(blankIndex, value))
  }
}

object FillInBlanksState {
  private given ReadWriter[FillInBlanksState] = macroRW
  val serializer: Serializer[FillInBlanksState] = Serializer.fromUpickleJson(summon[ReadWriter[FillInBlanksState]])
}

case class FillInBlanksInteraction(
                                    override val id: String,
                                    sentenceParts: List[LanguageMapContentId]
                                  ) extends WorkbookInteraction[FillInBlanksState] {
  require(sentenceParts.nonEmpty, "FillInBlanksInteraction requires at least one sentence part.")

  lazy val blankCount: Int = (sentenceParts.size - 1).max(0)
  override val defaultValue: FillInBlanksState = FillInBlanksState(List.fill(blankCount)(""))
  override val serializer: Serializer[FillInBlanksState] = FillInBlanksState.serializer
}

case class DropdownBlanksState(selectedOptionIndicesByBlankIndex: List[Option[Int]]) {
  def sanitized(optionCountsByBlank: List[Int]): DropdownBlanksState = {
    val blankCount = optionCountsByBlank.size
    val padded = selectedOptionIndicesByBlankIndex.take(blankCount).padTo(blankCount, None)
    DropdownBlanksState(padded.zip(optionCountsByBlank).map { case (selected, optionCount) => selected.filter(index => index >= 0 && index < optionCount) })
  }

  def selectedOptionIndex(blankIndex: Int, optionCountsByBlank: List[Int]): Option[Int] =
    sanitized(optionCountsByBlank).selectedOptionIndicesByBlankIndex.lift(blankIndex).flatten

  def withSelection(blankIndex: Int, optionIndex: Option[Int], optionCountsByBlank: List[Int]): DropdownBlanksState = {
    val clean = sanitized(optionCountsByBlank).selectedOptionIndicesByBlankIndex
    if blankIndex < 0 || blankIndex >= optionCountsByBlank.size then DropdownBlanksState(clean)
    else DropdownBlanksState(clean.updated(blankIndex, optionIndex.filter(index => index >= 0 && index < optionCountsByBlank(blankIndex))))
  }
}

object DropdownBlanksState {
  private given ReadWriter[DropdownBlanksState] = macroRW
  val serializer: Serializer[DropdownBlanksState] = Serializer.fromUpickleJson(summon[ReadWriter[DropdownBlanksState]])
}

case class DropdownBlanksInteraction(
                                      override val id: String,
                                      sentenceParts: List[LanguageMapContentId],
                                      optionsByBlank: List[List[LanguageMapContentId]]
                                    ) extends WorkbookInteraction[DropdownBlanksState] {
  require(sentenceParts.nonEmpty, "DropdownBlanksInteraction requires at least one sentence part.")
  require(optionsByBlank.size == sentenceParts.size - 1, "DropdownBlanksInteraction requires exactly one option list between each pair of sentence parts.")

  override val defaultValue: DropdownBlanksState = DropdownBlanksState(List.fill(optionsByBlank.size)(None))
  override val serializer: Serializer[DropdownBlanksState] = DropdownBlanksState.serializer
}

case class TableFillInState(blankValues: List[String]) {
  def sanitized(blankCount: Int): TableFillInState = TableFillInState(blankValues.take(blankCount).padTo(blankCount, ""))

  def blankValue(blankIndex: Int, blankCount: Int): String = sanitized(blankCount).blankValues.lift(blankIndex).getOrElse("")

  def withBlankValue(blankIndex: Int, value: String, blankCount: Int): TableFillInState = {
    val clean = sanitized(blankCount).blankValues
    if blankIndex < 0 || blankIndex >= blankCount then TableFillInState(clean) else TableFillInState(clean.updated(blankIndex, value))
  }
}

object TableFillInState {
  private given ReadWriter[TableFillInState] = macroRW
  val serializer: Serializer[TableFillInState] = Serializer.fromUpickleJson(summon[ReadWriter[TableFillInState]])
}

case class TableFillInInteraction(
                                   override val id: String,
                                   rows: List[List[Option[LanguageMapContentId]]]
                                 ) extends WorkbookInteraction[TableFillInState] {
  lazy val blankCount: Int = rows.flatten.count(_.isEmpty)
  override val defaultValue: TableFillInState = TableFillInState(List.fill(blankCount)(""))
  override val serializer: Serializer[TableFillInState] = TableFillInState.serializer
}
