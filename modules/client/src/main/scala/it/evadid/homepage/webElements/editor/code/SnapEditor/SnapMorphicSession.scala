package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.facades.Snap
import it.evadid.facades.Snap.*
import it.evadid.homepage.webElements.editor.code.SnapEditor.{CanvasVisibility, SnapCodeEditorConfig}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStateRaw, ProgrammingStateSnapScalaModel}
import org.scalajs.dom
import org.scalajs.dom.html.Canvas
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

import SnapProjectRuntime.*

/** A retained Snap/Morphic session for the interactive editor and exact previews. */
final class SnapMorphicSession() extends SnapCodeEditorImpl:
  private val resources = Var(SnapSessionResources())
  private val palette = new SnapPaletteInstaller(resources)

  override def renderPreviewInto(state: ProgrammingStateRaw, canvas: Canvas, config: SnapCodeEditorConfig): Unit =
    if !resources.now().cyclesRunning then SnapPreviewRenderer.render(state, canvas, config, humanLanguage.now())

  override def removeAllLibraries(includeDefaultLibraries: Boolean): Unit = palette.removeAllLibraries(includeDefaultLibraries)


  override def whenReady(render: () => Unit, onError: Throwable => Unit): Unit =
    import scala.scalajs.js.Thenable.Implicits.*
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    Snap.load()
      .map(_ => render()).failed.foreach(onError)

  override def whenReady(language: AppLanguage.HumanLanguage, render: () => Unit, onError: Throwable => Unit): Unit =
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    Snap.preloadLanguage(language).map(_ => render()).failed.foreach(onError)

  private val humanLanguage = Var[AppLanguage.HumanLanguage](AppLanguage.English)
  private val renderedLanguage = Var[AppLanguage.HumanLanguage](AppLanguage.English)
  private val languageRefresh = Var(Option.empty[(AnyRef, AppLanguage.HumanLanguage)])

  /** The project binding of the mounted editor ([[SnapProjectBinding]]). */
  private val binding = Var(Option.empty[SnapProjectBinding])
  private val native = new SnapProjectBinding.Native:
    def serialized(): Option[String] = resources.now().editor.map(snapshotProjectXml)
    def open(xml: ProgrammingStateRaw): Boolean = openProject(xml)
  private val stageMirror = SnapStageMirror(() => resources.now().editor, () => startWorldCycles())
  private val layout = SnapCanvasLayout(
    () => for canvas <- resources.now().mountedCanvas; config <- resources.now().mountedConfig yield canvas -> config,
    canvas => for world <- resources.now().editorWorld; ide <- resources.now().editor do layoutEditor(world, ide, canvas))

  override def renderEditorInto(project: Var[Option[ProgrammingStateSnapScalaModel]], canvas: Canvas,
      config: SnapCodeEditorConfig)(using owner: Owner): Unit =
    Snap.useLanguage(humanLanguage.now())
    stopEditorSession()
    resources.update(_.copy(mountedCanvas = Some(canvas), mountedConfig = Some(config)))
    require(canvas.isConnected, "Snap's interactive canvas must be mounted before WorldMorph is created")
    SnapCanvasLayout.size(canvas, config)

    val answer = project.now().getOrElse(ProgrammingStateSnapScalaModel.empty)
    val world = Snap.world(canvas).createLoadedInstance()
    SnapCanvasLayout.releaseRetinaCssSize(canvas)
    require(
      world.worldCanvas eq canvas,
      "WorldMorph did not retain the mounted editor canvas used to register input listeners"
    )
    SnapKeyboardFocus.keepInEditor(world, canvas)
    val ide = createEditor(world, answer.toSnapXml, config)
    layoutEditor(world, ide, canvas)
    // Palette construction calls fixLayout. Install custom templates only
    // after the noAutoFill IDE has a real extent; doing it while its extent is
    // still zero leaves both the palette and scripts pane with empty bounds.
    if config.libraryTabs.nonEmpty then
      palette.installLibraries(config.libraryTabs, ide)
      palette.retagCustomBlockCategories(ide)
      layoutEditor(world, ide, canvas)

    resources.update(_.copy(editorWorld = Some(world), editor = Some(ide)))
    val bound = SnapProjectBinding(project, native)
    bound.opened(answer)
    bound.observe(owner)
    binding.set(Some(bound))
    renderedLanguage.set(humanLanguage.now())
    layout.observe(canvas)
    startWorldCycles()
    // Dialog layout may settle one frame after mount; refit once parent has real size.
    layout.fitAfterLayout()
    CanvasVisibility.warnIfUnexpectedlyEmpty(getClass.getSimpleName, answer.toSnapXml.code, canvas)

  /** Replaces the open project with `state`, unless a slot is being edited. */
  private def openProject(state: ProgrammingStateRaw): Boolean =
    val xml = canonicalXml(state)
    resources.now().editor match
      case Some(ide) if !isTextEditing =>
        // A restored answer supersedes Snap's queued project reload.
        languageRefresh.now().foreach { (_, language) =>
          ide.onNextStep = null
          renderedLanguage.set(language)
          languageRefresh.set(None)
        }
        Snap.useLanguage(renderedLanguage.now())
        restorePrimitiveBlockDictionary()
        palette.ensureExerciseCategoriesBeforeLoad()
        ide.rawOpenProjectString(xml)
        repairCustomBlockParameterBindings(ide)
        ensureMissingGlobalVariables(ide, xml, refreshPalette = true)
        palette.reinstallConfiguredLibraries(ide)
        palette.retagCustomBlockCategories(ide)
        (resources.now().editorWorld, resources.now().mountedCanvas) match
          case (Some(world), Some(canvas)) =>
            layoutEditor(world, ide, canvas)
            ide.refreshPalette(true)
          case _ =>
            ide.fixLayout()
            ide.fullChanged()
        refreshLanguage()
        true
      case _ => false

  override def flushPendingProjectChanges(): Unit =
    // Commit in-progress slot text before serializing (close and outside controls must capture it).
    if isTextEditing then resources.now().editorWorld.foreach(_.stopEditing())
    binding.now().foreach(_.publish())

  private def createEditor(world: WorldMorph, state: ProgrammingStateRaw, config: SnapCodeEditorConfig): IDEMorph =
    val hasLibraryTabs = config.libraryTabs.nonEmpty
    val ide = Snap.editor(js.Dynamic.literal(
      noUserSettings = true,
      noAutoFill = true,
      noCloud = true,
      noExitWarning = true,
      preserveTitle = true,
      hideControls = !config.parts.headline,
      hideCategories = !config.parts.libraryCategories,
      noSprites = !config.parts.stage,
      noSpriteEdits = !config.parts.spriteControls,
      noPalette = !config.parts.palette,
      noOwnBlocks = hasLibraryTabs,
      // Hide built-in categories; exercise tabs via customCategories in installLibraries.
      noDefaultCat = hasLibraryTabs,
      eduLibraryTabs = config.libraryTabs.map(_.name).toJSArray
    )).createLoadedInstance()
    ide.openIn(world)
    if hasLibraryTabs then
      palette.registerCustomCategoryTabs(config.libraryTabs)
    restorePrimitiveBlockDictionary()
    ide.rawOpenProjectString(canonicalXml(state))
    repairCustomBlockParameterBindings(ide)
    ensureMissingGlobalVariables(ide, canonicalXml(state), refreshPalette = false)
    palette.retagCustomBlockCategories(ide)
    ide

  override def fitEditorToContainer(): Unit = layout.fit()

  override def startWorldCycles(): Unit =
    if !resources.now().cyclesRunning && resources.now().editorWorld.nonEmpty then
      resources.update(value => value.copy(cyclesRunning = true))
      tickEditor()

  override def pauseWorldCycles(): Unit =
    // Always flush before stopping the poll loop so close/unmount cannot drop
    // edits that happened since the last 500ms check.
    flushPendingProjectChanges()
    stopGreenFlagOnStage()
    if resources.now().frameHandle != 0 then dom.window.cancelAnimationFrame(resources.now().frameHandle)
    resources.update(_.copy(cyclesRunning = false, frameHandle = 0))

  override def runGreenFlagOnStage(mirrorTarget: Canvas): Unit = stageMirror.run(mirrorTarget)

  override def stopGreenFlagOnStage(): Unit = stageMirror.stop()

  override def setGreenFlagStepMs(ms: Double): Unit = stageMirror.setStepMs(ms)

  private def tickEditor(): Unit =
    if resources.now().cyclesRunning then
      resources.now().editorWorld.foreach(_.doOneCycle())
      resources.update(value => value.copy(frameHandle = dom.window.requestAnimationFrame(_ => tickEditor())))
  override def publishProjectChanges(): Unit =
    if !stageMirror.isMirroring && languageRefresh.now().isEmpty && !isTextEditing then
      binding.now().foreach(_.publish())

  /** True while Morphic has an active CursorMorph for an input slot. */
  private def isTextEditing: Boolean =
    resources.now().editorWorld.exists(world => world.cursor != null)

  private def stopEditorSession(): Unit =
    pauseWorldCycles()
    resources.now().editor.foreach(_.onNextStep = null)
    languageRefresh.set(None)
    layout.disconnect()
    // An editor can unmount while the runtime is still loading; no native globals exist yet.
    if resources.now().editor.nonEmpty then palette.release()
    resources.now().editor.foreach(_.destroy())
    resources.now().editorWorld.foreach(_.destroy())
    resources.update(_.copy(editor = None, editorWorld = None, mountedCanvas = None, mountedConfig = None))
    binding.set(None)

  override def destroy(): Unit =
    stopEditorSession()

  override def changeLanguage(language: AppLanguage.HumanLanguage): Unit =
    humanLanguage.set(language)
    refreshLanguage()

  private def refreshLanguage(): Unit =
    if languageRefresh.now().isEmpty && renderedLanguage.now() != humanLanguage.now() then
      resources.now().editor.foreach { ide =>
        flushPendingProjectChanges()
        stopGreenFlagOnStage()
        val xml = snapshotProjectXml(ide)
        val language = humanLanguage.now()
        val token = new Object
        languageRefresh.set(Some(token -> language))
        palette.ensureExerciseCategoriesBeforeLoad()
        try Snap.changeLanguage(ide, language, () => {
          if resources.now().editor.contains(ide) && languageRefresh.now().contains(token -> language) then
            try
              restorePrimitiveBlockDictionary()
              repairCustomBlockParameterBindings(ide)
              ensureMissingGlobalVariables(ide, xml, refreshPalette = true)
              palette.reinstallConfiguredLibraries(ide)
              palette.retagCustomBlockCategories(ide)
              for world <- resources.now().editorWorld; canvas <- resources.now().mountedCanvas do layoutEditor(world, ide, canvas)
              renderedLanguage.set(language)
              binding.now().foreach(_.reserialized())
            finally languageRefresh.set(None)
            refreshLanguage()
        })
        catch
          case error: Throwable =>
            languageRefresh.set(None)
            throw error
      }
