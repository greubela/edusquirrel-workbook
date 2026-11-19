package it.evadid.homepage.webElements.editor.code.EvaEditor

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage, ProgrammingLanguage}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.abstractions.SimpleWebEditor
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.util.logging.Logger
import it.evadid.vm.code.errors.{ProgrammingConversionError, ProgrammingConversionException, ProgrammingConversionKind}
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try
import it.evadid.vm.conversion.{ConversionInfo, ConversionResult}
import it.evadid.util.logging.LoggingLevel


/** One editor bound to the answer; panels only observe that same answer. */
case class EvaEditor(
                      logger: Logger,
                      underlyingVar: Var[ProgrammingState],
                      panels: Seq[HtmlAppElement],
                      config: Var[EvaEditorConfig],
                      choices: Option[Seq[EvaEditorTab]] = None,
                      humanLanguage: Signal[HumanLanguage] = Val(English),
                      labels: EvaEditorLabels
                    ) extends SimpleWebEditor[ProgrammingState, EvaEditorConfig] {
  val appearance = EditorAppearance()

  def currentConfig: EvaEditorConfig = config.now()
  private val tabs = choices.getOrElse(EvaEditorConfig.tabs(logger, currentConfig, labels, humanLanguage)).distinctBy(_.language)
  private val selected = underlyingVar.signal.map(source => tabs.find(_.language == source.associatedLanguage)).distinct
  private val lastConversion = Var(Option.empty[ConversionResult])
  private val request = Var(Option.empty[AnyRef])
  private val focused = Var(Option.empty[ProgrammingLanguage])
  private val tabListId = s"eva-tabs-${java.util.UUID.randomUUID()}"

  private sealed trait ConversionCheck {
    def source: ProgrammingState
  }

  private final case class PendingConversion(source: ProgrammingState, result: Future[ConversionResult])
    extends ConversionCheck

  private final case class CompletedConversion(source: ProgrammingState, result: ConversionResult)
    extends ConversionCheck

  private val conversionChecks = Var(Map.empty[ProgrammingLanguage, ConversionCheck])
  private val mounted = Var(false)
  private def failure(result: ConversionResult): Option[ProgrammingConversionException] =
    Option.when(result.finalState.isEmpty) {
      val info = result.conversionInfo.find(_.warningLevel == LoggingLevel.ERROR)
      new ProgrammingConversionException(info.map(_.warningMsg).getOrElse("Conversion failed."),
        info.flatMap(_.sourceError).getOrElse(ProgrammingConversionError(ProgrammingConversionKind.UnsupportedConstruct)))
    }
  private val marker: Signal[Option[ProgrammingConversionError]] = lastConversion.signal.map(
    _.flatMap(failure).map(_.error))

  def selectedLanguage: Option[ProgrammingLanguage] = tabs.find(_.language == underlyingVar.now().associatedLanguage).map(_.language)

  def error: Option[Throwable] = lastConversion.now().flatMap(failure)

  def switchTabTo(language: ProgrammingLanguage): Future[Unit] = tabs.find(_.language == language) match {
    case None => Future.unit
    case Some(_) if selectedLanguage.contains(language) =>
      request.set(None)
      lastConversion.set(None)
      Future.unit
    case Some(tab) =>
      val source = underlyingVar.now()
      val token = new Object
      request.set(Some(token))
      checkConversion(tab, source).map { outcome =>
        if mounted.now() && request.now().contains(token) && underlyingVar.now() == source then {
          outcome.finalState.foreach(commit)
          lastConversion.set(Some(outcome))
        }
      }
  }

  private def checkConversion(tab: EvaEditorTab, source: ProgrammingState = underlyingVar.now()): Future[ConversionResult] =
    if source.associatedLanguage == tab.language then Future.successful(ConversionResult(source, Some(source), Seq.empty))
    else conversionChecks.now().get(tab.language) match {
      case Some(PendingConversion(`source`, result)) => result
      case Some(CompletedConversion(`source`, result)) => Future.successful(result)
      case _ =>
        val result = Future(Try(tab.convert(source)).fold(error => ConversionResult.failed(source, error), identity))
        val pending = PendingConversion(source, result)
        conversionChecks.update(_.updated(tab.language, pending))
        result.foreach { outcome =>
          if mounted.now() && underlyingVar.now() == source then
            conversionChecks.update { checks =>
              if checks.get(tab.language).contains(pending) then
                checks.updated(tab.language, CompletedConversion(source, outcome))
              else checks
            }
        }
        result
    }

  private def commit(converted: ProgrammingState): Unit = {
    lastConversion.set(None)
    underlyingVar.set(converted)
    request.set(None)
  }

  private def tabDomId(language: ProgrammingLanguage): String = s"$tabListId-${language.name}"

  private def focusTab(language: ProgrammingLanguage): Unit = {
    focused.set(Some(language))
    Option(dom.document.getElementById(tabDomId(language))).foreach(_.asInstanceOf[dom.html.Element].focus())
  }

  private def moveFocus(current: ProgrammingLanguage, direction: Int): Unit = if tabs.nonEmpty then {
    val index = tabs.indexWhere(_.language == current)
    val start = if index < 0 then 0 else index
    val next = Math.floorMod(start + direction, tabs.length)
    focusTab(tabs(next).language)
  }

  private def tabButton(tab: EvaEditorTab): Element = {
    val status = underlyingVar.signal.combineWith(conversionChecks.signal).map { (source, checks) =>
      checks.get(tab.language) match {
        case Some(CompletedConversion(`source`, result)) => failure(result).map(describeConversionError)
        case _ => None
      }
    }
    val blocked = status.map(_.nonEmpty)
    val reason = status.map(_.getOrElse(""))
    val reasonId = s"${tabDomId(tab.language)}-reason"
    button(
      typ := "button", role := "tab", idAttr := tabDomId(tab.language),
      aria.label := tab.label,
      title <-- reason.map(r => if r.nonEmpty then r else tab.label),
      aria.selected <-- selected.map(_.contains(tab)),
      aria.disabled <-- blocked,
      aria.describedBy := reasonId,
      tabIndex <-- focused.signal.combineWith(selected).map { (focus, current) =>
        val active = focus.orElse(current.map(_.language)).orElse(tabs.headOption.map(_.language))
        if active.contains(tab.language) then 0 else -1
      },
      tab.icon, Option.when(tab.showLabel)(span(tab.label)),
      span(idAttr := reasonId, cls := "eva-editor__sr-only", child.text <-- reason),
      onKeyDown --> { event =>
        val current = focused.now().orElse(selectedLanguage).getOrElse(tab.language)
        event.key match {
          case "ArrowRight" | "ArrowDown" => event.preventDefault(); moveFocus(current, 1)
          case "ArrowLeft" | "ArrowUp" => event.preventDefault(); moveFocus(current, -1)
          case "Home" => event.preventDefault(); tabs.headOption.foreach(item => focusTab(item.language))
          case "End" => event.preventDefault(); tabs.lastOption.foreach(item => focusTab(item.language))
          case _ => ()
        }
      },
      onFocus --> (_ => checkConversion(tab)),
      onPointerEnter --> (_ => checkConversion(tab)),
      onClick --> (_ => switchTabTo(tab.language)))
  }

  private def describeConversionError(error: Throwable): String = EvaEditor.describe(error)

  override def getDomElement(): Element = div(
    cls <-- selected.map(tab => "eva-editor editor-appearance" + tab.fold("")(_.css)),
    dataAttr("editor-theme") <-- appearance.theme,
    onMountCallback { context =>
      mounted.set(true)
      appearance.watch(context.owner)
      if selectedLanguage.isEmpty then tabs.headOption.foreach(tab => switchTabTo(tab.language))
    },
    onUnmountCallback { _ =>
      mounted.set(false)
      request.set(None)
      conversionChecks.set(Map.empty)
    },
    underlyingVar.signal.changes --> { _ =>
      request.set(None)
      focused.set(None)
      lastConversion.set(None)
      conversionChecks.set(Map.empty)
    },
    div(cls := "eva-editor__toolbar",
      div(cls := "eva-editor__tabs", role := "tablist", aria.label <-- labels.languages, tabs.map(tabButton)),
      child.maybe <-- lastConversion.signal.combineWith(labels.switchFailed).map {
        case (Some(result), prefix) => failure(result) match {
          case Some(error) => Some(div(cls := "eva-editor__error", role := "alert", s"$prefix: ${describeConversionError(error)}"))
          case None => Option.when(result.conversionInfo.nonEmpty)(div(cls := "instruction-callout instruction-callout--warning",
            role := "status", result.conversionInfo.map(info => div(info.warningMsg))))
        }
        case _ => None
      }),
    div(cls := "eva-editor__content", child <-- selected.map {
      case Some(tab) => tab.create(underlyingVar, appearance, marker, labels.loading).getDomElement()
      case None => div(child.text <-- labels.unavailable)
    }),
    panels.map(_.getDomElement())
  )

  override def dismissOnOutsideClick: Boolean = false
}

object EvaEditor {
  def describe(error: Throwable): String = {
    val message = Option(error.getMessage).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)
    error match {
      case failure: ProgrammingConversionException =>
        failure.error.line.fold(message)(line => if message.contains(s"line $line") then message else s"$message (line $line)")
      case _ => message
    }
  }
}
