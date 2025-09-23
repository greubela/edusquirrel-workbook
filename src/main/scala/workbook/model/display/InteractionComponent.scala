package workbook.model.display

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.htmlElements.HtmlAppElement

trait InteractionComponent extends HtmlAppElement {

  def setHighlight(highlight: Boolean): Unit

  def setVisible(visible: Boolean): Unit

  def setDisabled(disabled: Boolean): Unit

}

object InteractionComponent {

  abstract class InteractionComponentWithReactiveVars extends InteractionComponent {

    val isHiddenVar: Var[Boolean] = Var(false)
    val isHighlightedVar: Var[Boolean] = Var(false)
    val isDisabledVar: Var[Boolean] = Var(false)

    def setHighlight(highlight: Boolean): Unit = isHighlightedVar.set(highlight)

    def setVisible(visible: Boolean): Unit = isHiddenVar.set(!visible)

    def setDisabled(disabled: Boolean): Unit = isDisabledVar.set(disabled)

  }

  trait InteractionComponentForRole extends InteractionComponent {
    def forContentRole: InteractionContentRole
  }

  enum InteractionContentRole(val cssString: String) {
    case Editor extends InteractionContentRole("editor")
    case ScaffoldingStateEditor extends InteractionContentRole("editor-state")
    case GradingStateEditor extends InteractionContentRole("grading-config")
    case ScaffoldingResult extends InteractionContentRole("scaffolding-result")
    case GradingResult extends InteractionContentRole("grading-result")

    case ButtonShowScaffolder extends InteractionContentRole("show-scaffolder")
    case ButtonShowEditor extends InteractionContentRole("show-editor")
    case ButtonShowGrader extends InteractionContentRole("show-grader")
    case ButtonStartGrading extends InteractionContentRole("start-grading")
    case ButtonStartScaffolding extends InteractionContentRole("start-scaffolding")
  }

  case class InteractionWithRole(interaction: InteractionComponent, role: InteractionContentRole) extends InteractionComponentForRole {
    override def forContentRole: InteractionContentRole = role

    override def getDomElement(): L.Element = interaction.getDomElement()

    def setHighlight(highlight: Boolean): Unit = interaction.setHighlight(highlight)

    def setVisible(visible: Boolean): Unit = interaction.setVisible(visible)

    def setDisabled(disabled: Boolean): Unit = interaction.setDisabled(disabled)
  }

}
