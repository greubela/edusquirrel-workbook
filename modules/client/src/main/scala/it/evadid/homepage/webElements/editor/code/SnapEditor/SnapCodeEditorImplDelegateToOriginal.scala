package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.ownership.Owner
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor.SnapCodeEditorImpl
import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch.TurtleStitchFromBeExpressionSerializer
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExerciseState
import org.scalajs.dom
import org.scalajs.dom.CanvasRenderingContext2D
import org.scalajs.dom.html.Canvas

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** A retained Snap/Morphic session for the interactive editor and exact previews. */
final class SnapCodeEditorImplDelegateToOriginal() extends SnapCodeEditorImpl:

  private var editorWorld: Option[WorldMorph] = None
  private var editor: Option[IDEMorph] = None
  private var mountedCanvas: Option[Canvas] = None
  private var mountedConfig: Option[SnapCodeEditorConfig] = None
  private var resizeObserver: Option[dom.ResizeObserver] = None
  private var fitRafHandle = 0
  private var fitDebounceHandle = 0
  private var frameHandle = 0
  private var cyclesRunning = false
  private var projectXmlChangedCallback: String => Unit = _ => ()
  private var lastProjectXml: Option[String] = None
  /** Last state→XML string applied via load/create (for no-op detection). */
  private var lastLoadedFromBeProgramXml: Option[String] = None
  private var lastProjectXmlCheckAt = 0.0
  private var originalBlockTemplates: Option[js.Any] = None
  private var installedCustomCategoryNames: List[String] = Nil

  private val ProjectXmlCheckIntervalMs = 500.0
  private val FitDebounceMs = 32.0

  override def mount(owner: Owner): Unit =
    ()

  override def renderEditorInto(initState: ProgrammingExerciseState, canvas: Canvas, config: SnapCodeEditorConfig): Unit =
    // Laminar mounts the same lazy editor element again whenever the fullscreen
    // dialog is reopened. Keep the WorldMorph which already owns this canvas:
    // constructing another world would add a second set of DOM listeners and
    // leave the older (now visually obscured) world handling the input.
    mountedCanvas = Some(canvas)
    mountedConfig = Some(config)
    editorWorld match
      case Some(world) if world.worldCanvas eq canvas =>
        keepKeyboardHandlerInEditor(world, canvas)
        sizeEditorCanvas(canvas, config)
        loadProgramIfChanged(initState)
        editor.foreach { ide =>
          layoutEditor(world, ide, canvas)
          ide.refreshPalette(true)
        }
        installResizeObserver(canvas)
        startWorldCycles()
        scheduleFitAfterLayout()
        return
      case _ => ()

    stopEditorSession()
    mountedCanvas = Some(canvas)
    mountedConfig = Some(config)
    require(canvas.isConnected, "Snap's interactive canvas must be mounted before WorldMorph is created")
    sizeEditorCanvas(canvas, config)

    val world = new WorldMorph(canvas, false)
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
      installLibraries(config.libraryTabs, ide)
      layoutEditor(world, ide, canvas)
    initializeProjectChangeTracking(ide)
    // Align with Snap's normalized XML so external program restores do not
    // immediately rawOpenProjectString again and wipe exercise libraries.
    val seededXml = toSnapXml(initState)
    lastLoadedFromBeProgramXml = Some(seededXml)
    lastProjectXml = Some(ide.getProjectXML())

    editorWorld = Some(world)
    editor = Some(ide)
    installResizeObserver(canvas)
    startWorldCycles()
    // Dialog layout may settle one frame after mount; refit once parent has real size.
    scheduleFitAfterLayout()
    CanvasVisibility.warnIfUnexpectedlyEmpty(this, initState.program, canvas)

  override def renderPreviewInto(state: ProgrammingExerciseState, canvas: Canvas, config: SnapCodeEditorConfig): Unit =
    val sourceCanvas = dom.document.createElement("canvas").asInstanceOf[Canvas]
    sourceCanvas.width = config.visuals.CanvasWidth
    sourceCanvas.height = config.visuals.CanvasHeight

    val world = new WorldMorph(sourceCanvas, false)
    // A preview is an image of the scripts themselves, not another configured
    // IDE. Pane-hiding and palette settings can otherwise collapse the source
    // ScriptsMorph before scriptsPicture() takes its snapshot.
    val ide = createPreviewEditor(world, state)
    layoutEditor(world, ide, sourceCanvas)
    runStartupCycles(world)

    // fullImage() includes the ScriptsMorph workspace at its absolute Morphic
    // position and can consequently contain only its background. Snap's own
    // export path uses scriptsPicture(), which crops and composites its visible
    // script children instead.
    val scriptsImage = ide.currentSprite.scripts.scriptsPicture().getOrElse(
      dom.document.createElement("canvas").asInstanceOf[Canvas]
    )
    canvas.width = math.max(1, scriptsImage.width)
    canvas.height = math.max(1, scriptsImage.height)
    canvas.style.width = s"${canvas.width}px"
    canvas.style.height = s"${canvas.height}px"
    canvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D].drawImage(scriptsImage, 0, 0)
    CanvasVisibility.warnIfUnexpectedlyEmpty(this, state.program, canvas)

    ide.destroy()
    world.destroy()

  override def loadProgramIfChanged(state: ProgrammingExerciseState): Unit =
    applyProgramToEditor(state, force = false)

  /** Always re-open from state (used on fullscreen open). */
  override def forceLoadProgram(state: ProgrammingExerciseState): Unit =
    applyProgramToEditor(state, force = true)

  private def applyProgramToEditor(state: ProgrammingExerciseState, force: Boolean): Unit =
    val xml = toSnapXml(state)
    editor match
      case Some(ide) =>
        // Skip only non-forced sync echoes of the same state→XML.
        // Force on fullscreen open: acknowledge does not rawOpen, so a skip
        // would leave Snap on the previous rawOpen'd project.
        if !force && lastLoadedFromBeProgramXml.contains(xml) then
          return
        ide.rawOpenProjectString(xml)
        lastLoadedFromBeProgramXml = Some(xml)
        lastProjectXml = Some(ide.getProjectXML())
        lastProjectXmlCheckAt = dom.window.performance.now()
        reinstallConfiguredLibraries(ide)
        (editorWorld, mountedCanvas) match
          case (Some(world), Some(canvas)) =>
            layoutEditor(world, ide, canvas)
            ide.refreshPalette(true)
          case _ =>
            ide.fixLayout()
            ide.fullChanged()
      case None => ()

  override def acknowledgeProgramFromEditor(state: ProgrammingExerciseState): Unit =
    lastLoadedFromBeProgramXml = Some(toSnapXml(state))

  override def flushPendingProjectChanges(): Unit =
    editor.foreach(checkWhetherProgramXmlChanged)

  private def toSnapXml(state: ProgrammingExerciseState): String =
    TurtleStitchFromBeExpressionSerializer.toXml(state.program.fullProgram, canvasLayout = state.canvasLayout)

  private def reinstallConfiguredLibraries(ide: IDEMorph): Unit =
    mountedConfig.filter(_.libraryTabs.nonEmpty).foreach { config =>
      installLibraries(config.libraryTabs, ide)
    }

  private def createEditor(world: WorldMorph, state: ProgrammingExerciseState, config: SnapCodeEditorConfig): IDEMorph =
    val hasLibraryTabs = config.libraryTabs.nonEmpty
    val ide = new IDEMorph(js.Dynamic.literal(
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
    ))
    ide.openIn(world)
    ide.rawOpenProjectString(toSnapXml(state))
    ide

  private def createPreviewEditor(world: WorldMorph, state: ProgrammingExerciseState): IDEMorph =
    val ide = new IDEMorph(js.Dynamic.literal(
      noAutoFill = true,
      noCloud = true,
      noExitWarning = true,
      preserveTitle = true
    ))
    ide.openIn(world)
    ide.rawOpenProjectString(toSnapXml(state))
    ide

  /** Replace this editor instance's primitive provider, rather than mutating
    * SpriteMorph.prototype.blockTemplates globally. Multiple editors can therefore use different
    * exercise libraries on the same page.
    */
  private def installLibraries(libraries: List[LibraryTab], ide: IDEMorph): Unit =
    require(libraries.map(_.name).distinct.size == libraries.size, "Snap library tab names must be unique")
    require(libraries.forall(_.name.nonEmpty), "Snap library tab names must not be empty")
    val sprite = ide.currentSprite
    originalBlockTemplates = Some(sprite.asInstanceOf[js.Dynamic].selectDynamic("blockTemplates"))

    clearInstalledCustomCategories()
    ide.asInstanceOf[js.Dynamic].updateDynamic("currentCategory")(libraries.head.name)

    ensurePrimitiveSelectors(libraries.flatMap(_.selectableElements.map(_.id)))

    // Snap calls blockTemplates(category) or blockTemplates(category, forSearch).
    // Exercise-only: only library tab names return blocks; everything else is empty.
    val blockTemplates: js.Function2[String, js.UndefOr[Boolean], js.Array[BlockMorph]] =
      (category: String, _: js.UndefOr[Boolean]) =>
        libraries.find(_.name == category).toList.flatMap(_.selectableElements).flatMap { data =>
          createTemplateBlock(data) match
            case Some(block) => List(block)
            case None =>
              println(s"Snap library: skipping unknown block selector '${data.id}'")
              Nil
        }.toJSArray

    sprite.asInstanceOf[js.Dynamic].updateDynamic("blockTemplates")(blockTemplates)
    sprite.asInstanceOf[js.Dynamic].updateDynamic("primitivesCache")(js.Dictionary.empty[js.Any])
    sprite.paletteCache = js.Dictionary.empty
    registerCustomCategoryTabs(libraries)
    ide.createCategories()
    ide.refreshPalette(true)

  private def spriteMorphPrototype: js.Dynamic =
    js.Dynamic.global
      .selectDynamic("SpriteMorph")
      .selectDynamic("prototype")

  /** If configured selectors are missing from the live primitives table (e.g. after
    * a scene replaced SpriteMorph.prototype.blocks), restore the full table.
    */
  private def ensurePrimitiveSelectors(ids: List[String]): Unit =
    val proto = spriteMorphPrototype
    val blocks = proto.selectDynamic("blocks")
    val missing = ids.filter { id =>
      val info = blocks.selectDynamic(id)
      js.isUndefined(info) || info == null
    }
    if missing.nonEmpty then
      proto.applyDynamic("initBlocks")()

  /** Mirror Snap's palette `block()` helper with explicit `this` = prototype. */
  private def createTemplateBlock(data: LibraryBlock): Option[BlockMorph] =
    val proto = spriteMorphPrototype
    def invoke(): js.Dynamic =
      proto.selectDynamic("blockForSelector").call(proto, data.id, true)

    var raw = invoke()
    if js.isUndefined(raw) || raw == null then
      // One retry after restoring primitives (scene load may have left a thin table).
      proto.applyDynamic("initBlocks")()
      raw = invoke()
    if js.isUndefined(raw) || raw == null then None
    else
      val block = raw.asInstanceOf[BlockMorph]
      block.isDraggable = false
      block.isTemplate = true
      if data.snap_description_line.nonEmpty then
        val spec = block.asInstanceOf[js.Dynamic].selectDynamic("blockSpec")
        val specStr = if js.isUndefined(spec) || spec == null then "" else spec.toString
        block.setSpec(descriptionWithNativeInputs(data.snap_description_line, specStr))
      Some(block)

  override def removeAllLibraries(includeDefaultLibraries: Boolean): Unit =
    editor.foreach { ideMorph =>
      val sprite = ideMorph.currentSprite
      val original = originalBlockTemplates.getOrElse {
        val templates = sprite.asInstanceOf[js.Dynamic].selectDynamic("blockTemplates")
        originalBlockTemplates = Some(templates)
        templates
      }
      val templates = if includeDefaultLibraries then
        ((_: String, _: js.UndefOr[Boolean]) => js.Array[BlockMorph]())
          .asInstanceOf[js.Function2[String, js.UndefOr[Boolean], js.Array[BlockMorph]]]
      else original
      sprite.asInstanceOf[js.Dynamic].updateDynamic("blockTemplates")(templates)
      sprite.asInstanceOf[js.Dynamic].updateDynamic("primitivesCache")(js.Dictionary.empty[js.Any])
      sprite.paletteCache = js.Dictionary.empty
      clearInstalledCustomCategories()
      val ideConfig = ideMorph.asInstanceOf[js.Dynamic].selectDynamic("config")
      ideConfig.updateDynamic("eduLibraryTabs")(js.Array())
      ideConfig.updateDynamic("eduEmptyLibrary")(includeDefaultLibraries)
      ideConfig.updateDynamic("noDefaultCat")(false)
      ideMorph.asInstanceOf[js.Dynamic].updateDynamic("currentCategory")("motion")
      ideMorph.createCategories()
      ideMorph.refreshPalette(true)
    }
    if !includeDefaultLibraries then originalBlockTemplates = None

  private def spriteMorphCustomCategories: js.Dynamic =
    spriteMorphPrototype.selectDynamic("customCategories")

  /** Register exercise tabs on TurtleStitch's customCategories Map (name → Color). */
  private def registerCustomCategoryTabs(libraries: List[LibraryTab]): Unit =
    val color = spriteMorphPrototype.selectDynamic("blockColor").selectDynamic("other")
    val customCategories = spriteMorphCustomCategories
    libraries.foreach(tab => customCategories.applyDynamic("set")(tab.name, color))
    installedCustomCategoryNames = libraries.map(_.name)

  private def clearInstalledCustomCategories(): Unit =
    val customCategories = spriteMorphCustomCategories
    installedCustomCategoryNames.foreach(name => customCategories.applyDynamic("delete")(name))
    installedCustomCategoryNames = Nil

  /** `_` is deliberately only presentation syntax. The selector's native
    * placeholders remain authoritative for numeric, boolean and nested inputs.
    */
  private def descriptionWithNativeInputs(description: String, nativeSpec: String): String =
    val placeholders = "%[^ ]+".r.findAllIn(nativeSpec).toList.iterator
    description.foldLeft(new StringBuilder) { (result, character) =>
      if character == '_' && placeholders.hasNext then result.append(placeholders.next())
      else result.append(character)
    }.result()

  private def layoutEditor(world: WorldMorph, ide: IDEMorph, canvas: Canvas): Unit =
    world.setExtent(new SnapPoint(canvas.width.toDouble, canvas.height.toDouble))
    ide.setExtent(world.extent())
    ide.fixLayout()
    // Canvas bitmap clears on width/height assignment; damage the full world so
    // the next cycles repaint everything instead of leaving a blank surface.
    ide.fullChanged()
    world.fullChanged()
    runStartupCycles(world)

  private def runStartupCycles(world: WorldMorph): Unit =
    world.doOneCycle()
    world.doOneCycle()
    world.doOneCycle()

  /** @return true when the canvas bitmap size changed (which clears pixels). */
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
    canvas.style.width = s"${width}px"
    canvas.style.height = s"${height}px"
    canvas.style.position = "relative"
    canvas.style.display = "block"
    // WorldMorph registers mouse/touch listeners synchronously in its
    // constructor. Make this exact mounted canvas an explicit input target;
    // creating or copying a second canvas would only copy pixels, not those
    // listeners or the Morphic world behind them.
    canvas.tabIndex = 0
    canvas.style.pointerEvents = "auto"
    canvas.style.setProperty("touch-action", "none")
    bitmapChanged

  override def fitEditorToContainer(): Unit =
    applyContainerSize(relayoutIfChanged = true)
    scheduleFitAfterLayout()

  private def applyContainerSize(relayoutIfChanged: Boolean): Unit =
    (mountedCanvas, mountedConfig) match
      case (Some(canvas), Some(config)) if canvas.isConnected =>
        val bitmapChanged = sizeEditorCanvas(canvas, config)
        if relayoutIfChanged && bitmapChanged then
          (editorWorld, editor) match
            case (Some(world), Some(ide)) => layoutEditor(world, ide, canvas)
            case _ => ()
      case _ => ()

  private def scheduleFitAfterLayout(): Unit =
    if fitRafHandle != 0 then
      dom.window.cancelAnimationFrame(fitRafHandle)
    fitRafHandle = dom.window.requestAnimationFrame { _ =>
      fitRafHandle = 0
      applyContainerSize(relayoutIfChanged = true)
    }

  private def requestDebouncedFit(): Unit =
    if fitDebounceHandle != 0 then
      dom.window.clearTimeout(fitDebounceHandle)
    fitDebounceHandle = dom.window.setTimeout(() => {
      fitDebounceHandle = 0
      applyContainerSize(relayoutIfChanged = true)
    }, FitDebounceMs)

  private def installResizeObserver(canvas: Canvas): Unit =
    disconnectResizeObserver()
    Option(canvas.parentElement).foreach { parent =>
      val callback: js.Function0[Unit] = () => requestDebouncedFit()
      val observer = js.Dynamic
        .newInstance(js.Dynamic.global.ResizeObserver)(callback)
        .asInstanceOf[dom.ResizeObserver]
      observer.observe(parent)
      resizeObserver = Some(observer)
    }

  private def disconnectResizeObserver(): Unit =
    resizeObserver.foreach(_.disconnect())
    resizeObserver = None
    if fitRafHandle != 0 then
      dom.window.cancelAnimationFrame(fitRafHandle)
      fitRafHandle = 0
    if fitDebounceHandle != 0 then
      dom.window.clearTimeout(fitDebounceHandle)
      fitDebounceHandle = 0

  private def keepKeyboardHandlerInEditor(world: WorldMorph, canvas: Canvas): Unit =
    // Morphic creates one hidden textarea on document.body and focuses it when
    // an input slot is edited. A modal <dialog> makes body siblings inert, so
    // mouse events still reach the canvas but the textarea cannot receive keys.
    // Moving the shared handler below the mounted canvas keeps it in the same
    // focus scope without changing Morphic's keyboard/IME event pipeline.
    Option(canvas.parentElement).foreach(_.appendChild(world.keyboardHandler))
    world.keyboardHandler.setAttribute("aria-hidden", "true")
    world.keyboardHandler.tabIndex = -1
    world.keyboardHandler.style.pointerEvents = "none"
    world.keyboardHandler.style.opacity = "0"

  override def startWorldCycles(): Unit =
    if !cyclesRunning && editorWorld.nonEmpty then
      cyclesRunning = true
      tickEditor()

  override def pauseWorldCycles(): Unit =
    // Always flush before stopping the poll loop so close/unmount cannot drop
    // edits that happened since the last 500ms check.
    flushPendingProjectChanges()
    cyclesRunning = false
    if frameHandle != 0 then dom.window.cancelAnimationFrame(frameHandle)
    frameHandle = 0

  override def setOnProjectXmlChangedListener(callback: String => Unit): Unit =
    projectXmlChangedCallback = callback

  private def tickEditor(): Unit =
    if cyclesRunning then
      editorWorld.foreach(_.doOneCycle())
      frameHandle = dom.window.requestAnimationFrame(_ => tickEditor())
      val now = dom.window.performance.now()
      if now - lastProjectXmlCheckAt >= ProjectXmlCheckIntervalMs then
        lastProjectXmlCheckAt = now
        editor.foreach(checkWhetherProgramXmlChanged)

  private def initializeProjectChangeTracking(ide: IDEMorph): Unit =
    lastProjectXml = Some(ide.getProjectXML())
    lastProjectXmlCheckAt = dom.window.performance.now()

  /**
   * Compare the persisted project itself rather than IDE_Morph.version. That
   * value is only updated on Snap edit paths which call recordUnsavedChanges,
   * and therefore is not a reliable content revision. Polling is throttled so
   * serialization does not happen on every animation frame.
   */
  private def checkWhetherProgramXmlChanged(ide: IDEMorph): Unit =
    val xml = ide.getProjectXML()
    if !lastProjectXml.contains(xml) then
      lastProjectXml = Some(xml)
      println("Snap! code changed!")
      projectXmlChangedCallback(xml)

  private def stopEditorSession(): Unit =
    editor.foreach(checkWhetherProgramXmlChanged)
    pauseWorldCycles()
    disconnectResizeObserver()
    clearInstalledCustomCategories()
    editor.foreach(_.destroy())
    editorWorld.foreach(_.destroy())
    editor = None
    editorWorld = None
    mountedCanvas = None
    mountedConfig = None
    lastProjectXml = None
    lastLoadedFromBeProgramXml = None
    lastProjectXmlCheckAt = 0.0
    originalBlockTemplates = None

  override def destroy(): Unit =
    stopEditorSession()
