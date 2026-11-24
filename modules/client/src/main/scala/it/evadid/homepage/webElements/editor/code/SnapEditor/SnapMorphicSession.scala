package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.facades.Snap
import it.evadid.facades.Snap.*
import it.evadid.homepage.webElements.editor.code.SnapEditor.{CanvasVisibility, SnapCodeEditorConfig}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw
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

  private val changes = new SnapProjectChangeTracker
  private val stageMirror = SnapStageMirror(() => resources.now().editor, () => startWorldCycles())
  private val layout = SnapCanvasLayout(
    () => for canvas <- resources.now().mountedCanvas; config <- resources.now().mountedConfig yield canvas -> config,
    canvas => for world <- resources.now().editorWorld; ide <- resources.now().editor do layoutEditor(world, ide, canvas))

  override def mount(owner: Owner): Unit =
    ()

  override def renderEditorInto(initState: ProgrammingStateRaw, canvas: Canvas, config: SnapCodeEditorConfig): Unit =
    Snap.useLanguage(humanLanguage.now())
    // Reuse the world when this mounted canvas is laid out again, so native
    // keyboard and mouse listeners are not registered twice.
    resources.update(_.copy(mountedCanvas = Some(canvas), mountedConfig = Some(config)))
    resources.now().editorWorld match
      case Some(world) if world.worldCanvas eq canvas =>
        SnapKeyboardFocus.keepInEditor(world, canvas)
        SnapCanvasLayout.size(canvas, config)
        loadProgramIfChanged(initState)
        resources.now().editor.foreach { ide =>
          layoutEditor(world, ide, canvas)
          ide.refreshPalette(true)
        }
        layout.observe(canvas)
        startWorldCycles()
        layout.fitAfterLayout()
        return
      case _ => ()

    stopEditorSession()
    resources.update(_.copy(mountedCanvas = Some(canvas), mountedConfig = Some(config)))
    require(canvas.isConnected, "Snap's interactive canvas must be mounted before WorldMorph is created")
    SnapCanvasLayout.size(canvas, config)

    val world = Snap.world(canvas).createLoadedInstance()
    require(
      world.worldCanvas eq canvas,
      "WorldMorph did not retain the mounted editor canvas used to register input listeners"
    )
    SnapKeyboardFocus.keepInEditor(world, canvas)
    val ide = createEditor(world, initState, config)
    layoutEditor(world, ide, canvas)
    // Palette construction calls fixLayout. Install custom templates only
    // after the noAutoFill IDE has a real extent; doing it while its extent is
    // still zero leaves both the palette and scripts pane with empty bounds.
    if config.libraryTabs.nonEmpty then
      palette.installLibraries(config.libraryTabs, ide)
      palette.retagCustomBlockCategories(ide)
      layoutEditor(world, ide, canvas)
    // Align with Snap's normalized XML so external program restores do not
    // immediately rawOpenProjectString again and wipe exercise libraries.
    changes.markLoaded(canonicalXml(initState))
    changes.reset(ide)

    resources.update(_.copy(editorWorld = Some(world), editor = Some(ide)))
    renderedLanguage.set(humanLanguage.now())
    layout.observe(canvas)
    startWorldCycles()
    // Dialog layout may settle one frame after mount; refit once parent has real size.
    layout.fitAfterLayout()
    CanvasVisibility.warnIfUnexpectedlyEmpty(getClass.getSimpleName, initState.code, canvas)

  override def loadProgramIfChanged(state: ProgrammingStateRaw): Unit =
    applyProgramToEditor(state, force = false)

  /** Always re-open from state (used on fullscreen open). */
  override def forceLoadProgram(state: ProgrammingStateRaw): Unit =
    applyProgramToEditor(state, force = true)

  private def applyProgramToEditor(state: ProgrammingStateRaw, force: Boolean): Unit =
    val xml = canonicalXml(state)
    resources.now().editor match
      case Some(ide) =>
        // Skip only non-forced sync echoes of the same stored XML.
        // Force on fullscreen open: acknowledge does not rawOpen, so a skip
        // would leave Snap on the previous rawOpen'd project.
        if !force && changes.isLoaded(xml) then
          return
        if !force && isTextEditing then return
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
        changes.markLoaded(xml)
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
        changes.reset(ide)
        refreshLanguage()
      case None => ()

  override def acknowledgeProgramFromEditor(state: ProgrammingStateRaw): Unit =
    changes.markLoaded(canonicalXml(state))

  override def flushPendingProjectChanges(): Unit =
    // Force: flush on close / popup must capture in-progress slot text.
    resources.now().editor.foreach(checkWhetherProgramXmlChanged(_, allowDuringEdit = true))

  override def currentProjectXml(): Option[String] =
    resources.now().editor.map(snapshotProjectXml)

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

  override def setOnProjectXmlChangedListener(callback: String => Unit): Unit =
    changes.onChanged(callback)

  private def tickEditor(): Unit =
    if resources.now().cyclesRunning then
      resources.now().editorWorld.foreach(_.doOneCycle())
      resources.update(value => value.copy(frameHandle = dom.window.requestAnimationFrame(_ => tickEditor())))
      // Skip XML serialization while Execute is mirroring — getProjectXML is heavy
      // and competes with Morphic + stage blit for main-thread time.
      if !stageMirror.isMirroring && changes.due(dom.window.performance.now()) then
        resources.now().editor.foreach(checkWhetherProgramXmlChanged(_))

  /**
   * Compare the persisted project itself rather than IDE_Morph.version. That
   * value is only updated on Snap edit paths which call recordUnsavedChanges,
   * and therefore is not a reliable content revision. Polling is throttled so
   * serialization does not happen on every animation frame.
   *
   * While a text cursor is active, skip publish so mid-digit slot edits do not
   * rebuild preview / clear Morphic's shared keyboard. After blur, the next
   * poll (or an explicit flush) publishes the committed value.
   */
  private def checkWhetherProgramXmlChanged(ide: IDEMorph, allowDuringEdit: Boolean = false): Unit =
    if !allowDuringEdit && languageRefresh.now().nonEmpty then return
    if !allowDuringEdit && isTextEditing then return
    // Commit in-progress slot text before serializing (needed for flush-on-close).
    if allowDuringEdit && isTextEditing then
      resources.now().editorWorld.foreach(_.stopEditing())
    changes.publish(ide)

  /** True while Morphic has an active CursorMorph for an input slot. */
  private def isTextEditing: Boolean =
    resources.now().editorWorld.exists(world => world.cursor != null)

  private def stopEditorSession(): Unit =
    resources.now().editor.foreach(checkWhetherProgramXmlChanged(_, allowDuringEdit = true))
    pauseWorldCycles()
    resources.now().editor.foreach(_.onNextStep = null)
    languageRefresh.set(None)
    layout.disconnect()
    // An editor can unmount while the runtime is still loading; no native globals exist yet.
    if resources.now().editor.nonEmpty then palette.release()
    resources.now().editor.foreach(_.destroy())
    resources.now().editorWorld.foreach(_.destroy())
    resources.update(_.copy(editor = None, editorWorld = None, mountedCanvas = None, mountedConfig = None))
    changes.clear()

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
              changes.reset(ide)
            finally languageRefresh.set(None)
            refreshLanguage()
        })
        catch
          case error: Throwable =>
            languageRefresh.set(None)
            throw error
      }
