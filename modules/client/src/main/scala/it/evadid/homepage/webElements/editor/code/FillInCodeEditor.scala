package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.elements.interactionElements.codeTaskToggle.{
  AdvancedCodeCheckResult,
  AdvancedCodeRequirement
}

/** Fill-in-the-blank style code editor with TODO legend and requirement checking. */
object FillInCodeEditor {

  private sealed trait FeedbackState

  private object FeedbackState {
    case object Hidden extends FeedbackState
    case class Success(messageId: LanguageMapContentId) extends FeedbackState
    case class Incomplete(introId: LanguageMapContentId, hintIds: List[LanguageMapContentId]) extends FeedbackState
  }

  def apply(
    codeState: Var[String],
    resetTemplate: String,
    title: Signal[String],
    todoLegend: Signal[String],
    checkButtonLabel: Signal[String],
    resetButtonLabel: Signal[String],
    resetConfirmMessage: Signal[String],
    resetConfirmYes: Signal[String],
    resetConfirmNo: Signal[String],
    incompleteIntroId: LanguageMapContentId,
    successMessageId: LanguageMapContentId,
    requirements: List[AdvancedCodeRequirement],
    resolveText: LanguageMapContentId => Signal[String],
    language: ProgrammingLanguage = AppLanguage.C
  ): HtmlElement = {
    val feedbackVar: Var[FeedbackState] = Var(FeedbackState.Hidden)
    val confirmOpen: Var[Boolean] = Var(false)

    def resetToTemplate(): Unit = {
      codeState.set(resetTemplate)
      feedbackVar.set(FeedbackState.Hidden)
      confirmOpen.set(false)
    }

    div(
      cls := "fill-in-code-editor",
      h4(text <-- title),
      div(
        cls := "fill-in-code-editor__todo-legend",
        span(cls := "fill-in-code-editor__todo-badge", "TODO"),
        span(text <-- todoLegend)
      ),
      div(
        cls := "fill-in-code-editor__toolbar",
        button(
          cls := "fill-in-code-editor__reset-btn",
          text <-- resetButtonLabel,
          onClick --> { _ => confirmOpen.set(true) }
        )
      ),
      div(
        cls := "fill-in-code-editor__mirror",
        CodeMirrorEditor(codeState, language = language).getDomElement()
      ),
      button(
        cls := "btn-check",
        text <-- checkButtonLabel,
        onClick --> { _ =>
          feedbackVar.set(
            AdvancedCodeCheckResult.evaluate(codeState.now(), requirements) match {
              case AdvancedCodeCheckResult.Success =>
                FeedbackState.Success(successMessageId)
              case AdvancedCodeCheckResult.Incomplete(missingHints) =>
                FeedbackState.Incomplete(incompleteIntroId, missingHints)
            }
          )
        }
      ),
      div(
        cls := "reorder-feedback",
        cls.toggle("mode-hidden") <-- feedbackVar.signal.map(_ == FeedbackState.Hidden),
        cls.toggle("reorder-feedback--success") <-- feedbackVar.signal.map {
          case FeedbackState.Success(_) => true
          case _ => false
        },
        cls.toggle("reorder-feedback--incomplete") <-- feedbackVar.signal.map {
          case FeedbackState.Incomplete(_, _) => true
          case _ => false
        },
        child <-- feedbackVar.signal.map {
          case FeedbackState.Hidden =>
            emptyNode
          case FeedbackState.Success(messageId) =>
            span(text <-- resolveText(messageId))
          case FeedbackState.Incomplete(introId, hintIds) =>
            div(
              p(text <-- resolveText(introId)),
              ul(hintIds.map(hintId => li(text <-- resolveText(hintId))))
            )
        }
      ),
      div(
        cls := "sorting-error-popup",
        cls.toggle("is-visible") <-- confirmOpen.signal,
        div(
          cls := "sorting-error-popup__content",
          p(text <-- resetConfirmMessage),
          div(
            cls := "fill-in-code-editor__confirm-actions",
            button(
              typ := "button",
              cls := "sorting-error-popup__button",
              text <-- resetConfirmNo,
              onClick --> { _ => confirmOpen.set(false) }
            ),
            button(
              typ := "button",
              cls := "sorting-error-popup__button fill-in-code-editor__confirm-reset",
              text <-- resetConfirmYes,
              onClick --> { _ => resetToTemplate() }
            )
          )
        )
      )
    )
  }
}
