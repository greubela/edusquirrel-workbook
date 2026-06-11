package it.evadid.homepage.workbook.htmlRenderer.interactionEditors

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{option as optionTag, *}
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.model.interaction.basic.*
import it.evadid.workbook.model.interaction.sync.UpdateImportance

object HtmlChoiceSelectionRenderer extends HtmlRenderFactory[ChoiceSelectionInteraction] {

  override protected def createDomElement(interaction: ChoiceSelectionInteraction): L.Element = {
    val stateVar = interaction.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MINOR).toAirstreamVar
    val inputType = if interaction.allowMultiple then "checkbox" else "radio"

    div(
      cls := s"workbook-interaction choice-selection-interaction ${if interaction.allowMultiple then "choice-selection-interaction--multiple" else "choice-selection-interaction--single"}",
      interaction.prompt.map(prompt => div(cls := "choice-selection-interaction__prompt", child.text <-- contentIdStringSignal(prompt))).toSeq,
      div(
        cls := "choice-selection-interaction__options",
        interaction.options.zipWithIndex.map { case (optionLabel, optionIndex) =>
          label(
            cls := "choice-selection-interaction__option",
            input(
              typ := inputType,
              cls := "choice-selection-interaction__input",
              nameAttr := interaction.id,
              controlled(
                checked <-- stateVar.signal.map(_.isSelected(optionIndex, interaction.options.size, interaction.allowMultiple)),
                onInput.mapToChecked.map { checked =>
                  if interaction.allowMultiple then stateVar.now().withToggledSelection(optionIndex, interaction.options.size)
                  else if checked then stateVar.now().withSingleSelection(optionIndex, interaction.options.size)
                  else stateVar.now().sanitized(interaction.options.size, interaction.allowMultiple)
                } --> stateVar.writer
              )
            ),
            span(cls := "choice-selection-interaction__label", child.text <-- contentIdStringSignal(optionLabel))
          )
        }
      )
    )
  }
}

object HtmlMatchingInteractionRenderer extends HtmlRenderFactory[MatchingInteraction] {

  private val MissingValue = ""

  override protected def createDomElement(interaction: MatchingInteraction): L.Element = {
    val stateVar = interaction.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MINOR).toAirstreamVar

    div(
      cls := "workbook-interaction matching-interaction",
      interaction.leftItems.zipWithIndex.map { case (leftItem, leftIndex) =>
        div(
          cls := "matching-interaction__row",
          span(cls := "matching-interaction__left-item", child.text <-- contentIdStringSignal(leftItem)),
          select(
            cls := "matching-interaction__select",
            optionTag(value := MissingValue, "—"),
            interaction.rightItems.zipWithIndex.map { case (rightItem, rightIndex) =>
              optionTag(value := rightIndex.toString, child.text <-- contentIdStringSignal(rightItem))
            },
            controlled(
              value <-- stateVar.signal.map(_.selectedRightIndex(leftIndex, interaction.leftItems.size, interaction.rightItems.size).map(_.toString).getOrElse(MissingValue)),
              onChange.mapToValue.map(value => value.toIntOption).map { rightIndex =>
                stateVar.now().withMatch(leftIndex, rightIndex, interaction.leftItems.size, interaction.rightItems.size)
              } --> stateVar.writer
            )
          )
        )
      }
    )
  }
}

object HtmlCategorizationInteractionRenderer extends HtmlRenderFactory[CategorizationInteraction] {

  private val MissingValue = ""

  override protected def createDomElement(interaction: CategorizationInteraction): L.Element = {
    val stateVar = interaction.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MINOR).toAirstreamVar

    div(
      cls := "workbook-interaction categorization-interaction",
      interaction.items.zipWithIndex.map { case (item, itemIndex) =>
        div(
          cls := "categorization-interaction__row",
          span(cls := "categorization-interaction__item", child.text <-- contentIdStringSignal(item)),
          select(
            cls := "categorization-interaction__select",
            optionTag(value := MissingValue, "—"),
            interaction.categories.zipWithIndex.map { case (category, categoryIndex) =>
              optionTag(value := categoryIndex.toString, child.text <-- contentIdStringSignal(category))
            },
            controlled(
              value <-- stateVar.signal.map(_.selectedCategoryIndex(itemIndex, interaction.items.size, interaction.categories.size).map(_.toString).getOrElse(MissingValue)),
              onChange.mapToValue.map(value => value.toIntOption).map { categoryIndex =>
                stateVar.now().withCategory(itemIndex, categoryIndex, interaction.items.size, interaction.categories.size)
              } --> stateVar.writer
            )
          )
        )
      }
    )
  }
}

object HtmlFillInBlanksRenderer extends HtmlRenderFactory[FillInBlanksInteraction] {

  override protected def createDomElement(interaction: FillInBlanksInteraction): L.Element = {
    val stateVar = interaction.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MINOR).toAirstreamVar

    div(
      cls := "workbook-interaction fill-in-blanks-interaction",
      interaction.sentenceParts.zipWithIndex.flatMap { case (part, partIndex) =>
        val partElement = span(cls := "fill-in-blanks-interaction__text", child.text <-- contentIdStringSignal(part))
        if partIndex < interaction.blankCount then
          List(
            partElement,
            input(
              typ := "text",
              cls := "fill-in-blanks-interaction__blank",
              controlled(
                value <-- stateVar.signal.map(_.blankValue(partIndex, interaction.blankCount)),
                onInput.mapToValue.map(value => stateVar.now().withBlankValue(partIndex, value, interaction.blankCount)) --> stateVar.writer
              )
            )
          )
        else List(partElement)
      }
    )
  }
}

object HtmlDropdownBlanksRenderer extends HtmlRenderFactory[DropdownBlanksInteraction] {

  private val MissingValue = ""

  override protected def createDomElement(interaction: DropdownBlanksInteraction): L.Element = {
    val stateVar = interaction.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MINOR).toAirstreamVar
    val optionCountsByBlank = interaction.optionsByBlank.map(_.size)

    div(
      cls := "workbook-interaction dropdown-blanks-interaction",
      interaction.sentenceParts.zipWithIndex.flatMap { case (part, partIndex) =>
        val partElement = span(cls := "dropdown-blanks-interaction__text", child.text <-- contentIdStringSignal(part))
        if partIndex < interaction.optionsByBlank.size then
          List(
            partElement,
            select(
              cls := "dropdown-blanks-interaction__select",
              optionTag(value := MissingValue, "—"),
              interaction.optionsByBlank(partIndex).zipWithIndex.map { case (optionLabel, optionIndex) =>
                optionTag(value := optionIndex.toString, child.text <-- contentIdStringSignal(optionLabel))
              },
              controlled(
                value <-- stateVar.signal.map(_.selectedOptionIndex(partIndex, optionCountsByBlank).map(_.toString).getOrElse(MissingValue)),
                onChange.mapToValue.map(value => value.toIntOption).map { optionIndex =>
                  stateVar.now().withSelection(partIndex, optionIndex, optionCountsByBlank)
                } --> stateVar.writer
              )
            )
          )
        else List(partElement)
      }
    )
  }
}

object HtmlTableFillInRenderer extends HtmlRenderFactory[TableFillInInteraction] {

  override protected def createDomElement(interaction: TableFillInInteraction): L.Element = {
    val stateVar = interaction.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MINOR).toAirstreamVar

    var blankIndex = 0
    val renderedRows = interaction.rows.map { row =>
      tr(
        row.map {
          case Some(contentId) => td(cls := "table-fill-in-interaction__given-cell", child.text <-- contentIdStringSignal(contentId))
          case None =>
            val currentBlankIndex = blankIndex
            blankIndex += 1
            td(
              cls := "table-fill-in-interaction__blank-cell",
              input(
                typ := "text",
                cls := "table-fill-in-interaction__blank-input",
                controlled(
                  value <-- stateVar.signal.map(_.blankValue(currentBlankIndex, interaction.blankCount)),
                  onInput.mapToValue.map(value => stateVar.now().withBlankValue(currentBlankIndex, value, interaction.blankCount)) --> stateVar.writer
                )
              )
            )
        }
      )
    }

    div(
      cls := "workbook-interaction table-fill-in-interaction",
      table(
        cls := "table-fill-in-interaction__table",
        tbody(renderedRows)
      )
    )
  }
}
