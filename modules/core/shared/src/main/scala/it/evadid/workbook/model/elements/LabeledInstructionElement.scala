package it.evadid.workbook.model.elements

import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.elements.LabeledInstructionElement.LabelType

case class LabeledInstructionElement(
                                      titleLable: LanguageMapContentId,
                                      bodyContent: LanguageMapContentId,
                                      labelType: LabelType
                                    ) extends WorkbookElement {

}

object LabeledInstructionElement {


  sealed trait LabelType(val associatedCssString: String) {
  }

  case object SafetyLabel extends LabelType("instruction-safety")

  case object GoalLabel extends LabelType("instruction-goal")

  case object TaskLabel extends LabelType("instruction-task")

  case object HintLabel extends LabelType("instruction-hint")

}

/*
case class HtmlInstructionLabeledPair(
                                       fullInfo: FullInfo,
                                       titleMapId: String,
                                       bodyMapId: String,
                                       cssClass: String = "instruction-pair"
                                     ) extends HtmlWorkbookElement {

  override def getDomElement(): Element = div(
    cls := "workbook-element exercise-instruction",
    cls := cssClass,
    div(
      cls := s"${cssClass}__title",
      text <-- fullInfo.signals.stringFromLanguageMapId(titleMapId)
    ),
    div(
      cls := s"${cssClass}__body",
      text <-- fullInfo.signals.stringFromLanguageMapId(bodyMapId)
    )
  )
}*/