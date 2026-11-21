package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.facades.Snap
import it.evadid.facades.Snap.*
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.{CanvasVisibility, SnapCodeEditorConfig}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw
import org.scalajs.dom
import org.scalajs.dom.CanvasRenderingContext2D
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

  private val projectXmlSync = new SnapProjectXmlSync

  private val ProjectXmlCheckIntervalMs = 500.0
  private val FitDebounceMs = 32.0
  /** Extra mirror frames after processes go idle so pen trails settle. */
  private val StageMirrorIdleSettleFrames = 8
  /** Mirror at most this often; fullImage() is too expensive for every rAF. */
  private val StageMirrorMinIntervalMs = 33.0
  /** Default visible-step delay between blocks (ms). */
  /** Current pause between blocks during Execute (ms, >= 0). */

  override def mount(owner: Owner): Unit =
    ()

  override def renderEditorInto(initState: ProgrammingStateRaw, canvas: Canvas, config: SnapCodeEditorConfig): Unit =
    Snap.useLanguage(humanLanguage.now())
    // Reuse the world when this mounted canvas is laid out again, so native
    // keyboard and mouse listeners are not registered twice.
    resources.update(value => value.copy(mountedCanvas = Some(canvas)))
    resources.update(value => value.copy(mountedConfig = Some(config)))
    resources.now().editorWorld match
      case Some(world) if world.worldCanvas eq canvas =>
        keepKeyboardHandlerInEditor(world, canvas)
        sizeEditorCanvas(canvas, config)
        loadProgramIfChanged(initState)
        resources.now().editor.foreach { ide =>
          layoutEditor(world, ide, canvas)
          ide.refreshPalette(true)
        }
        installResizeObserver(canvas)
        startWorldCycles()
        scheduleFitAfterLayout()
        return
      case _ => ()

    stopEditorSession()
    resources.update(value => value.copy(mountedCanvas = Some(canvas)))
    resources.update(value => value.copy(mountedConfig = Some(config)))
    require(canvas.isConnected, "Snap's interactive canvas must be mounted before WorldMorph is created")
    sizeEditorCanvas(canvas, config)

    val world = Snap.world(canvas).createLoadedInstance()
    require(
      world.worldCanvas eq canvas,
      "WorldMorph did not retain the mounted editor canvas used to register input listeners"
    )
    keepKeyboardHandlerInEditor(world, canvas)
    val ide = createEditor(world, initState, config)
    layoutEditor(world, ide, canvas)
    // Palette construction calls fixLayout. Install custom templates only
    // after the noAutoFill IDE has a real extent; doing it while its extent is
    // still zero leaves both the palette and scripts pane with empty bounds.
    if config.libraryTabs.nonEmpty then
      palette.installLibraries(config.libraryTabs, ide)
      palette.retagCustomBlockCategories(ide)
      layoutEditor(world, ide, canvas)
    initializeProjectChangeTracking(ide)
    // Align with Snap's normalized XML so external program restores do not
    // immediately rawOpenProjectString again and wipe exercise libraries.
    val seededXml = canonicalXml(initState)
    projectXmlSync.markLoaded(seededXml)
    projectXmlSync.resetSnapshot(snapshotProjectXml(ide))

    resources.update(value => value.copy(editorWorld = Some(world)))
    resources.update(value => value.copy(editor = Some(ide)))
    renderedLanguage.set(humanLanguage.now())
    installResizeObserver(canvas)
    startWorldCycles()
    // Dialog layout may settle one frame after mount; refit once parent has real size.
    scheduleFitAfterLayout()
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
        if !force && projectXmlSync.isLoaded(xml) then
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
        projectXmlSync.markLoaded(xml)
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
        projectXmlSync.resetSnapshot(snapshotProjectXml(ide))
        resources.update(value => value.copy(lastProjectXmlCheckAt = dom.window.performance.now()))
        refreshLanguage()
      case None => ()

  override def acknowledgeProgramFromEditor(state: ProgrammingStateRaw): Unit =
    projectXmlSync.markLoaded(canonicalXml(state))

  override def flushPendingProjectChanges(): Unit =
    // Force: flush on close / popup must capture in-progress slot text.
    resources.now().editor.foreach(checkWhetherProgramXmlChanged(_, allowDuringEdit = true))

  override def currentProjectXml(): Option[String] =
    resources.now().editor.map(snapshotProjectXml)

  private def processesStillRunning(stage: StageMorph): Boolean =
    try
      val processes =
        stage.asInstanceOf[js.Dynamic].selectDynamic("threads").selectDynamic("processes").asInstanceOf[js.Array[js.Dynamic]]
      processes.exists { proc =>
        val errorFlag =
          try proc.selectDynamic("errorFlag").asInstanceOf[Boolean]
          catch case _: Throwable => false
        val isDead =
          try proc.selectDynamic("isDead").asInstanceOf[Boolean]
          catch case _: Throwable => false
        val isRunning =
          try proc.applyDynamic("isRunning")().asInstanceOf[Boolean]
          catch case _: Throwable => !errorFlag
        !errorFlag && !isDead && isRunning
      }
    catch case _: Throwable =>
      try stage.threads.processes.length > 0
      catch case _: Throwable => false

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

  private def sizeEditorCanvas(canvas: Canvas, config: SnapCodeEditorConfig): Boolean =
    // Keep the CSS and bitmap coordinate systems identical. CSS-only scaling in
    // the fullscreen dialog produced independent X/Y factors and distorted hits.
    val parent = Option(canvas.parentElement)
    val parentWidth = parent.map(_.clientWidth).getOrElse(0)
    val parentHeight = parent.map(_.clientHeight).getOrElse(0)
    val width = math.max(1, if parentWidth > 0 then parentWidth else config.visuals.CanvasWidth)
    val height = math.max(1, if parentHeight > 0 then parentHeight else config.visuals.CanvasHeight)
    // Assigning canvas.width/height always clears the bitmap — even when the
    // numeric value is unchanged — which left a blank IDE until the next click.
    val bitmapChanged = canvas.width != width || canvas.height != height
    if bitmapChanged then
      canvas.width = width
      canvas.height = height
    // WorldMorph registers mouse/touch listeners synchronously in its
    // constructor. Make this exact mounted canvas an explicit input target;
    // creating or copying a second canvas would only copy pixels, not those
    // listeners or the Morphic world behind them.
    canvas.tabIndex = 0
    bitmapChanged

  override def fitEditorToContainer(): Unit =
    applyContainerSize(relayoutIfChanged = true)
    scheduleFitAfterLayout()

  private def applyContainerSize(relayoutIfChanged: Boolean): Unit =
    (resources.now().mountedCanvas, resources.now().mountedConfig) match
      case (Some(canvas), Some(config)) if canvas.isConnected =>
        val bitmapChanged = sizeEditorCanvas(canvas, config)
        if relayoutIfChanged && bitmapChanged then
          (resources.now().editorWorld, resources.now().editor) match
            case (Some(world), Some(ide)) => layoutEditor(world, ide, canvas)
            case _ => ()
      case _ => ()

  private def scheduleFitAfterLayout(): Unit =
    if resources.now().fitRafHandle != 0 then
      dom.window.cancelAnimationFrame(resources.now().fitRafHandle)
    resources.update(value => value.copy(fitRafHandle = dom.window.requestAnimationFrame { _ =>
      resources.update(value => value.copy(fitRafHandle = 0))
      applyContainerSize(relayoutIfChanged = true)
    }))

  private def requestDebouncedFit(): Unit =
    if resources.now().fitDebounceHandle != 0 then
      dom.window.clearTimeout(resources.now().fitDebounceHandle)
    resources.update(value => value.copy(fitDebounceHandle = dom.window.setTimeout(() => {
      resources.update(value => value.copy(fitDebounceHandle = 0))
      applyContainerSize(relayoutIfChanged = true)
    }, FitDebounceMs)))

  private def installResizeObserver(canvas: Canvas): Unit =
    disconnectResizeObserver()
    Option(canvas.parentElement).foreach { parent =>
      val callback: js.Function0[Unit] = () => requestDebouncedFit()
      val observer = js.Dynamic
        .newInstance(js.Dynamic.global.ResizeObserver)(callback)
        .asInstanceOf[dom.ResizeObserver]
      observer.observe(parent)
      resources.update(value => value.copy(resizeObserver = Some(observer)))
    }

  private def disconnectResizeObserver(): Unit =
    resources.now().resizeObserver.foreach(_.disconnect())
    resources.update(value => value.copy(resizeObserver = None))
    if resources.now().fitRafHandle != 0 then
      dom.window.cancelAnimationFrame(resources.now().fitRafHandle)
      resources.update(value => value.copy(fitRafHandle = 0))
    if resources.now().fitDebounceHandle != 0 then
      dom.window.clearTimeout(resources.now().fitDebounceHandle)
      resources.update(value => value.copy(fitDebounceHandle = 0))

  private def keepKeyboardHandlerInEditor(world: WorldMorph, canvas: Canvas): Unit =
    // Morphic creates one hidden textarea on document.body and focuses it when
    // an input slot is edited. A modal <dialog> makes body siblings inert, so
    // mouse events still reach the canvas but the textarea cannot receive keys.
    // Moving the shared handler below the mounted canvas keeps it in the same
    // focus scope without changing Morphic's keyboard/IME event pipeline.
    Option(canvas.parentElement).foreach(_.appendChild(world.keyboardHandler))
    world.keyboardHandler.setAttribute("aria-hidden", "true")
    world.keyboardHandler.tabIndex = -1
    world.keyboardHandler.classList.add("snap-keyboard-handler")

  override def startWorldCycles(): Unit =
    if !resources.now().cyclesRunning && resources.now().editorWorld.nonEmpty then
      resources.update(value => value.copy(cyclesRunning = true))
      tickEditor()

  override def pauseWorldCycles(): Unit =
    // Always flush before stopping the poll loop so close/unmount cannot drop
    // edits that happened since the last 500ms check.
    flushPendingProjectChanges()
    stopGreenFlagOnStage()
    resources.update(value => value.copy(cyclesRunning = false))
    if resources.now().frameHandle != 0 then dom.window.cancelAnimationFrame(resources.now().frameHandle)
    resources.update(value => value.copy(frameHandle = 0))

  override def runGreenFlagOnStage(mirrorTarget: Canvas): Unit =
    // Execute can be pressed after reopen/unmount cycles; ensure Morphic is ticking.
    startWorldCycles()
    resources.now().editor match
      case None => ()
      case Some(ide) =>
        stopGreenFlagOnStage()
        val stage = ide.stage
        if stage == null then return
        // Scratch-paced yields (not turbo). Pause per block from greenFlagStepMs.
        try
          stage.isFastTracked = false
          enableGreenFlagStepping()
        catch
          case _: Throwable => ()
        try ide.stopAllScripts()
        catch case _: Throwable => ()
        try stage.clearPenTrails()
        catch case _: Throwable => ()
        try ide.runScripts()
        catch
          case _: Throwable =>
            restoreGreenFlagStepping()
            return
        resources.update(value => value.copy(stageMirrorIdleFrames = 0))
        resources.update(value => value.copy(lastStageMirrorAt = 0.0))
        mirrorStageTo(stage, mirrorTarget, force = true)
        def tick(ts: Double): Unit =
          mirrorStageTo(stage, mirrorTarget, force = false, nowMs = ts)
          val running = processesStillRunning(stage)
          if running then resources.update(value => value.copy(stageMirrorIdleFrames = 0))
          else resources.update(value => value.copy(stageMirrorIdleFrames = value.stageMirrorIdleFrames + 1))
          if resources.now().stageMirrorIdleFrames < StageMirrorIdleSettleFrames then
            resources.update(value => value.copy(stageMirrorRafHandle = dom.window.requestAnimationFrame(t => tick(t))))
          else
            resources.update(value => value.copy(stageMirrorRafHandle = 0))
            mirrorStageTo(stage, mirrorTarget, force = true, nowMs = ts)
            restoreGreenFlagStepping()
        resources.update(value => value.copy(stageMirrorRafHandle = dom.window.requestAnimationFrame(ts => tick(ts))))

  override def stopGreenFlagOnStage(): Unit =
    if resources.now().stageMirrorRafHandle != 0 then
      dom.window.cancelAnimationFrame(resources.now().stageMirrorRafHandle)
      resources.update(value => value.copy(stageMirrorRafHandle = 0))
    resources.update(value => value.copy(stageMirrorIdleFrames = 0))
    resources.now().editor.foreach { ide =>
      try ide.stopAllScripts()
      catch case _: Throwable => ()
    }
    restoreGreenFlagStepping()

  override def setGreenFlagStepMs(ms: Double): Unit =
    resources.update(value => value.copy(greenFlagStepMs = math.max(0.0, ms)))

  /** Snap visible stepping: pause `greenFlagStepMs` between blocks (0 = no step delay). */
  private def enableGreenFlagStepping(): Unit =
    val proto = js.Dynamic.global.Process.selectDynamic("prototype")
    if resources.now().savedFlashTime.isEmpty then
      resources.update(value => value.copy(savedFlashTime = Some(proto.selectDynamic("flashTime").asInstanceOf[Double])))
    if resources.now().savedSingleStepping.isEmpty then
      resources.update(value => value.copy(savedSingleStepping = Some(proto.selectDynamic("enableSingleStepping").asInstanceOf[Boolean])))
    if resources.now().greenFlagStepMs <= 0 then
      proto.updateDynamic("flashTime")(0)
      proto.updateDynamic("enableSingleStepping")(false)
    else
      proto.updateDynamic("flashTime")(resources.now().greenFlagStepMs / 1000.0)
      proto.updateDynamic("enableSingleStepping")(true)

  private def restoreGreenFlagStepping(): Unit =
    // Closing a pending editor is valid even before threads.js defines Process.
    if resources.now().savedFlashTime.nonEmpty || resources.now().savedSingleStepping.nonEmpty then
      val proto = js.Dynamic.global.Process.selectDynamic("prototype")
      resources.now().savedFlashTime.foreach(proto.updateDynamic("flashTime")(_))
      resources.now().savedSingleStepping.foreach(proto.updateDynamic("enableSingleStepping")(_))
    resources.update(value => value.copy(savedFlashTime = None))
    resources.update(value => value.copy(savedSingleStepping = None))

  /**
   * Copy the live stage onto the turtle panel. Prefer pen-trails during the
   * animation (cheap blit); use Morph.fullImage() only for forced frames so the
   * final frame includes costumes/sprites.
   */
  private def mirrorStageTo(
      stage: StageMorph,
      target: Canvas,
      force: Boolean,
      nowMs: Double = dom.window.performance.now()
  ): Unit =
    if !force && nowMs - resources.now().lastStageMirrorAt < StageMirrorMinIntervalMs then return
    resources.update(value => value.copy(lastStageMirrorAt = nowMs))
    try
      if !force && mirrorPenTrails(stage, target) then return
      val src = stage.fullImage()
      if src == null then return
      if target.width != src.width then target.width = src.width
      if target.height != src.height then target.height = src.height
      val ctx = target.getContext("2d").asInstanceOf[CanvasRenderingContext2D]
      ctx.clearRect(0, 0, target.width.toDouble, target.height.toDouble)
      ctx.drawImage(src, 0, 0)
    catch
      case error: Throwable =>
        println(s"Snap Execute stage mirror failed: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")

  /** Fast path: blit the trails canvas only (main visual for turtle programs). */
  private def mirrorPenTrails(stage: StageMorph, target: Canvas): Boolean =
    try
      val trails = stage.penTrails()
      if trails == null then return false
      val w = trails.width
      val h = trails.height
      if w <= 0 || h <= 0 then return false
      if target.width != w then target.width = w
      if target.height != h then target.height = h
      val ctx = target.getContext("2d").asInstanceOf[CanvasRenderingContext2D]
      ctx.fillStyle = "#ffffff"
      ctx.fillRect(0, 0, w.toDouble, h.toDouble)
      ctx.drawImage(trails, 0, 0)
      true
    catch
      case _: Throwable => false

  override def setOnProjectXmlChangedListener(callback: String => Unit): Unit =
    resources.update(value => value.copy(projectXmlChangedCallback = callback))

  private def tickEditor(): Unit =
    if resources.now().cyclesRunning then
      resources.now().editorWorld.foreach(_.doOneCycle())
      resources.update(value => value.copy(frameHandle = dom.window.requestAnimationFrame(_ => tickEditor())))
      // Skip XML serialization while Execute is mirroring — getProjectXML is heavy
      // and competes with Morphic + stage blit for main-thread time.
      if resources.now().stageMirrorRafHandle != 0 then return
      val now = dom.window.performance.now()
      if now - resources.now().lastProjectXmlCheckAt >= ProjectXmlCheckIntervalMs then
        resources.update(value => value.copy(lastProjectXmlCheckAt = now))
        resources.now().editor.foreach(checkWhetherProgramXmlChanged(_))

  private def initializeProjectChangeTracking(ide: IDEMorph): Unit =
    projectXmlSync.resetSnapshot(snapshotProjectXml(ide))
    resources.update(value => value.copy(lastProjectXmlCheckAt = dom.window.performance.now()))

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
    val xml = snapshotProjectXml(ide)
    projectXmlSync.changedSnapshot(xml).foreach(resources.now().projectXmlChangedCallback)

  /** True while Morphic has an active CursorMorph for an input slot. */
  private def isTextEditing: Boolean =
    resources.now().editorWorld.exists(world => world.cursor != null)

  private def stopEditorSession(): Unit =
    resources.now().editor.foreach(checkWhetherProgramXmlChanged(_, allowDuringEdit = true))
    pauseWorldCycles()
    resources.now().editor.foreach(_.onNextStep = null)
    languageRefresh.set(None)
    disconnectResizeObserver()
    // An editor can unmount while the runtime is still loading; no native globals exist yet.
    if resources.now().editor.nonEmpty then palette.release()
    resources.now().editor.foreach(_.destroy())
    resources.now().editorWorld.foreach(_.destroy())
    resources.update(value => value.copy(editor = None))
    resources.update(value => value.copy(editorWorld = None))
    resources.update(value => value.copy(mountedCanvas = None))
    resources.update(value => value.copy(mountedConfig = None))
    projectXmlSync.clear()
    resources.update(value => value.copy(lastProjectXmlCheckAt = 0.0))

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
              projectXmlSync.resetSnapshot(snapshotProjectXml(ide))
              resources.update(value => value.copy(lastProjectXmlCheckAt = dom.window.performance.now()))
            finally languageRefresh.set(None)
            refreshLanguage()
        })
        catch
          case error: Throwable =>
            languageRefresh.set(None)
            throw error
      }
