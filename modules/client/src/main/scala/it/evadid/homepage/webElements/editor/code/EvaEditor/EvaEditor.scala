package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.airstream.ownership.ManualOwner
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.util.logging.Logger
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}

/** Generic tab navigation and module composition. All editor behavior belongs to adapters. */
case class EvaEditor(logger: Logger, state: Var[ProgrammingState], modules: Seq[EvaEditorModule], config: EvaEditorConfig,
    tabFactory: Option[ProgrammingLanguage => EvaEditorTab[?]] = None) extends HtmlAppElement with FullscreenLifecycle {
  val appearance = EditorAppearance()
  private val languages = config.enabledLanguages.distinct
  private val selected = Var(languages.find(_ == state.now().associatedLanguage).orElse(languages.headOption))
  private val available = Var(false)
  private val lastError = Var(Option.empty[Throwable])
  private var viewGeneration = 0L
  private var revision = 0L
  private var pendingRequest = 0L
  private var closed = false
  private var mounted = false
  private lazy val editorMap = languages.map(language => language -> tabFactory.fold(
    EvaEditorConfig.defaultEditorFor(logger, language, config, state.now(), appearance))(_(language))).toMap

  private val moduleContext = EvaEditorModule.Context(state, () => captureSource(), modules)
  modules.foreach(_.bind(moduleContext))
  def selectedLanguage: Option[ProgrammingLanguage] = selected.now()
  def error: Option[Throwable] = lastError.now()
  def currentState(): ProgrammingState = state.now()

  private def selectView(language: ProgrammingLanguage): Unit =
    if !selected.now().contains(language) then { viewGeneration += 1; selected.set(Some(language)) }
  private def setAvailable(value: Boolean): Unit =
    if available.now() != value then available.set(value)

  private def activeEditor: Option[EvaEditorTab[?]] = selected.now().flatMap(editorMap.get)
  private def closeViews(): Unit = {
    activeEditor.foreach(_.onFullscreenClose())
    modules.foreach(_.onFullscreenClose())
  }
  def captureSource(): ProgrammingState = {
    if !mounted then state.now()
    else if !available.now() then throw IllegalStateException("This draft is unavailable in the selected editor.")
    else activeEditor.get.captureSource()
  }

  def switchTabTo(language: ProgrammingLanguage): Future[Unit] = {
    if !languages.contains(language) then return Future.unit
    if selected.now().contains(language) && available.now() then {
      pendingRequest += 1
      lastError.set(None)
      return Future.unit
    }
    Try { if mounted && available.now() then activeEditor.foreach(_.captureSource()) } match {
      case Failure(error) => lastError.set(Some(error)); return Future.unit
      case _ => ()
    }
    pendingRequest += 1
    val request = pendingRequest
    val source = state.now()
    val version = revision
    val owner = new ManualOwner
    state.signal.changes.foreach(_ => pendingRequest += 1)(using owner)
    editorMap(language).prepare(source).transform { result =>
      if request == pendingRequest && version == revision && state.now() == source && !closed then {
        result match {
          case Success(install) =>
            closeViews()
            // Closing a tab/module may synchronously restore a source or close Eva.
            if request == pendingRequest && version == revision && state.now() == source && !closed then {
              install()
              selectView(language)
              setAvailable(true)
              lastError.set(None)
              if mounted then onFullscreenOpen()
            }
          case Failure(error) => lastError.set(Some(error))
        }
      }
      Success(())
    }.andThen { case _ => owner.killSubscriptions() }
  }

  private def receive(source: ProgrammingState): Unit = {
    revision += 1
    pendingRequest += 1
    val matching = languages.find(_ == source.associatedLanguage)
    matching match {
      case Some(language) =>
        Try(editorMap(language).setWith(source)) match {
          case Success(_) =>
            val previous = activeEditor
            val changed = !selected.now().contains(language)
            selectView(language)
            setAvailable(true)
            lastError.set(None)
            if changed then {
              previous.foreach(_.onFullscreenClose())
              modules.foreach(_.onFullscreenClose())
            }
          case Failure(error) => setAvailable(false); lastError.set(Some(error))
        }
      case None => setAvailable(false); selected.now().foreach(switchTabTo)
    }
  }

  private def createTabButton(language: ProgrammingLanguage): Element = {
    val tab = editorMap(language)
    button(typ := "button", aria.pressed <-- selected.signal.map(_.contains(language).toString),
      aria.selected <-- selected.signal.map(_.contains(language)), aria.label := tab.label, title := tab.label,
      tab.icon, Option.when(tab.showLabel)(span(tab.label)), onClick --> (_ => switchTabTo(language)),
      disabled <-- state.signal.flatMapSwitch { source =>
        if source.associatedLanguage == language then Val(false)
        else EventStream.fromFuture(tab.prepare(source).map(_ => false).recover {
          case scala.util.control.NonFatal(_) => true
        }).startWith(true)
      })
  }
  private lazy val domElement: Element = div(
    cls <-- selected.signal.combineWith(lastError.signal).map { (language, error) =>
      "eva-editor editor-appearance" + language.flatMap(editorMap.get).map(_.additionalCssForEvaEditor).getOrElse("") +
        (if error.nonEmpty then " eva-editor--error" else "")
    },
    dataAttr("editor-theme") <-- appearance.theme,
    state.signal.distinct --> receive,
    selected.signal.flatMapSwitch {
      case Some(language) =>
        val generation = viewGeneration
        editorMap(language).edits.map(next => (language, generation, next))
      case None => EventStream.empty
    } --> { (language, generation, next) =>
      if generation == viewGeneration && selected.now().contains(language) && available.now() && state.now() != next then state.set(next)
    },
    onMountCallback { _ => mounted = true; closed = false; if available.now() then onFullscreenOpen() },
    onUnmountCallback { _ => mounted = false; onFullscreenClose() },
    div(cls := "eva-editor__toolbar",
      div(cls := "eva-editor__tabs", aria.label := "Programming language", languages.map(createTabButton)), appearance.toggleButton()),
    child.maybe <-- lastError.signal.map(_.map(error => div(cls := "eva-editor__error", role := "alert",
      Option(error.getMessage).getOrElse("This draft cannot be converted.")))),
    div(cls := "eva-editor__content", child <-- selected.signal.combineWith(available.signal).map {
      case (Some(language), true) => editorMap(language).getDomElement()
      case _ => div("This draft is not available in the selected editor.")
    }),
    modules.flatMap(_.getDomElement)
  )
  override def getDomElement(): Element = domElement
  override def onFullscreenOpen(): Unit = {
    closed = false
    if available.now() then activeEditor.foreach(_.onFullscreenOpen())
    modules.foreach(_.onFullscreenOpen())
  }
  override def onFullscreenClose(): Unit = {
    if !closed then {
      closed = true
      pendingRequest += 1
      closeViews()
    }
  }
  override def dismissOnOutsideClick: Boolean = false
}
