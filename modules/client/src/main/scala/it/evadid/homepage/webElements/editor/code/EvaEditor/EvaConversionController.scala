package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.util.logging.LoggingLevel
import it.evadid.vm.code.errors.{ProgrammingConversionError, ProgrammingConversionException, ProgrammingConversionKind}
import it.evadid.vm.conversion.ConversionResult
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try

/** Language switching of one Eva editor, independent of its DOM.
  *
  * Conversions run in Futures. Each target language keeps one check of the current draft, so hovering or
  * focusing a tab prepares the switch a click then completes. A result is dropped when the draft changed, a newer
  * switch was requested or the editor was unmounted; a successful switch is passed to `commit`.
  */
final class EvaConversionController(draft: Var[ProgrammingState], commit: ProgrammingState => Unit) {
  import EvaConversionController.*

  private val checks = Var(Map.empty[ProgrammingLanguage, EvaConversionCheck])
  private val request = Var(Option.empty[Request])
  private val lastSwitch = Var(Option.empty[ConversionResult])
  private val mounted = Var(false)

  /** The outcome of the last requested switch until the draft changes. */
  val lastOutcome: Signal[Option[ConversionResult]] = lastSwitch.signal

  /** Where the last failed switch stopped, for the editor to mark in its source. */
  val marker: Signal[Option[ProgrammingConversionError]] = lastSwitch.signal.map(_.flatMap(failure).map(_.error))

  def error: Option[Throwable] = lastSwitch.now().flatMap(failure)

  /** Why switching to `tab` fails for the current draft, once its check has completed. */
  def blockedReason(tab: EvaEditorTab): Signal[Option[String]] =
    draft.signal.combineWith(checks.signal).map { (source, current) =>
      current.get(tab.language) match {
        case Some(CompletedEditorConversion(`source`, result)) => failure(result).map(describe)
        case _ => None
      }
    }

  /** Converts the draft to `tab`'s language; the converted answer then selects that tab's editor. */
  def switchTo(tab: EvaEditorTab): Future[Unit] =
    if draft.now().associatedLanguage == tab.language then {
      request.set(None)
      lastSwitch.set(None)
      Future.unit
    } else {
      val source = draft.now()
      val token = Request()
      request.set(Some(token))
      check(tab, source).map { outcome =>
        if mounted.now() && request.now().contains(token) && draft.now() == source then {
          outcome.finalState.foreach { converted =>
            lastSwitch.set(None)
            request.set(None)
            commit(converted)
          }
          lastSwitch.set(Some(outcome))
        }
      }
    }

  /** The (shared) conversion of the current draft to `tab`'s language. */
  def check(tab: EvaEditorTab, source: ProgrammingState = draft.now()): Future[ConversionResult] =
    if source.associatedLanguage == tab.language then Future.successful(ConversionResult(source, Some(source), Seq.empty))
    else checks.now().get(tab.language) match {
      case Some(PendingEditorConversion(`source`, result)) => result
      case Some(CompletedEditorConversion(`source`, result)) => Future.successful(result)
      case _ =>
        val result = Future(Try(tab.convert(source)).fold(error => ConversionResult.failed(source, error), identity))
        val pending = PendingEditorConversion(source, result)
        checks.update(_.updated(tab.language, pending))
        result.foreach { outcome =>
          if mounted.now() && draft.now() == source then
            checks.update(current =>
              if current.get(tab.language).contains(pending) then current.updated(tab.language, CompletedEditorConversion(source, outcome))
              else current)
        }
        result
    }

  private def forget(): Unit = {
    request.set(None)
    lastSwitch.set(None)
    checks.set(Map.empty)
  }

  /** Binds the controller's lifetime to the editor element: unmounting and draft changes drop pending work. */
  val binders: Seq[Modifier[HtmlElement]] = Seq(
    onMountCallback(_ => mounted.set(true)),
    onUnmountCallback { _ =>
      mounted.set(false)
      forget()
    },
    draft.signal.changes --> (_ => forget()))
}

object EvaConversionController {
  /** Identifies one requested switch; a newer request supersedes it. */
  private final class Request

  def failure(result: ConversionResult): Option[ProgrammingConversionException] =
    Option.when(result.finalState.isEmpty) {
      val info = result.conversionInfo.find(_.warningLevel == LoggingLevel.ERROR)
      new ProgrammingConversionException(info.map(_.warningMsg).getOrElse("Conversion failed."),
        info.flatMap(_.sourceError).getOrElse(ProgrammingConversionError(ProgrammingConversionKind.UnsupportedConstruct)))
    }

  def describe(error: Throwable): String = {
    val message = Option(error.getMessage).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)
    error match {
      case failure: ProgrammingConversionException =>
        failure.error.line.fold(message)(line => if message.contains(s"line $line") then message else s"$message (line $line)")
      case _ => message
    }
  }
}
