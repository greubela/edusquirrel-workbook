package it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor

import com.raquo.airstream.ownership.Owner
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor.*
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditorImpl
import it.evadid.workbook.elements.interactionElements.programming.*
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
  private var stageMirrorRafHandle = 0
  private var stageMirrorIdleFrames = 0
  private var cyclesRunning = false
  private var projectXmlChangedCallback: String => Unit = _ => ()
  private var lastProjectXml: Option[String] = None
  /** Last canonical XML loaded into the IDE (for no-op detection). */
  private var lastLoadedXml: Option[String] = None
  private var lastProjectXmlCheckAt = 0.0
  private var originalBlockTemplates: Option[js.Any] = None
  private var installedCustomCategoryNames: List[String] = Nil

  private val ProjectXmlCheckIntervalMs = 500.0
  private val FitDebounceMs = 32.0
  /** Extra mirror frames after processes go idle so pen trails settle. */
  private val StageMirrorIdleSettleFrames = 8
  /** Mirror at most this often; fullImage() is too expensive for every rAF. */
  private val StageMirrorMinIntervalMs = 33.0
  /** Default visible-step delay between blocks (ms). */
  private val DefaultGreenFlagStepMs = 20.0
  /** Current pause between blocks during Execute (ms, >= 0). */
  private var greenFlagStepMs: Double = DefaultGreenFlagStepMs
  private var lastStageMirrorAt = 0.0
  private var savedFlashTime: Option[Double] = None
  private var savedSingleStepping: Option[Boolean] = None

  override def mount(owner: Owner): Unit =
    ()

  override def renderEditorInto(initState: ProgrammingStateSnapXml, canvas: Canvas, config: SnapCodeEditorConfig): Unit =
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
      retagCustomBlockCategories(ide)
      layoutEditor(world, ide, canvas)
    initializeProjectChangeTracking(ide)
    // Align with Snap's normalized XML so external program restores do not
    // immediately rawOpenProjectString again and wipe exercise libraries.
    val seededXml = canonicalXml(initState)
    lastLoadedXml = Some(seededXml)
    lastProjectXml = Some(snapshotProjectXml(ide))

    editorWorld = Some(world)
    editor = Some(ide)
    installResizeObserver(canvas)
    startWorldCycles()
    // Dialog layout may settle one frame after mount; refit once parent has real size.
    scheduleFitAfterLayout()
    CanvasVisibility.warnIfUnexpectedlyEmpty(this, initState.snapXml, canvas)

  override def renderPreviewInto(state: ProgrammingStateSnapXml, canvas: Canvas, config: SnapCodeEditorConfig): Unit =
    // Preview creates a temporary WorldMorph that resets Morphic's shared
    // #morphic_keyboard textarea. Skip while the live editor world is ticking
    // so mid-edit keystrokes are not wiped / overwritten.
    if cyclesRunning then return

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
    CanvasVisibility.warnIfUnexpectedlyEmpty(this, state.snapXml, canvas)

    ide.destroy()
    world.destroy()

  override def loadProgramIfChanged(state: ProgrammingStateSnapXml): Unit =
    applyProgramToEditor(state, force = false)

  /** Always re-open from state (used on fullscreen open). */
  override def forceLoadProgram(state: ProgrammingStateSnapXml): Unit =
    applyProgramToEditor(state, force = true)

  private def applyProgramToEditor(state: ProgrammingStateSnapXml, force: Boolean): Unit =
    val xml = canonicalXml(state)
    editor match
      case Some(ide) =>
        // Skip only non-forced sync echoes of the same stored XML.
        // Force on fullscreen open: acknowledge does not rawOpen, so a skip
        // would leave Snap on the previous rawOpen'd project.
        if !force && lastLoadedXml.contains(xml) then
          return
        if !force && isTextEditing then return
        restorePrimitiveBlockDictionary()
        ensureExerciseCategoriesBeforeLoad()
        ide.rawOpenProjectString(xml)
        lastLoadedXml = Some(xml)
        repairCustomBlockParameterBindings(ide)
        ensureMissingGlobalVariables(ide, xml, refreshPalette = true)
        lastProjectXml = Some(snapshotProjectXml(ide))
        lastProjectXmlCheckAt = dom.window.performance.now()
        reinstallConfiguredLibraries(ide)
        retagCustomBlockCategories(ide)
        (editorWorld, mountedCanvas) match
          case (Some(world), Some(canvas)) =>
            layoutEditor(world, ide, canvas)
            ide.refreshPalette(true)
          case _ =>
            ide.fixLayout()
            ide.fullChanged()
      case None => ()

  override def acknowledgeProgramFromEditor(state: ProgrammingStateSnapXml): Unit =
    lastLoadedXml = Some(canonicalXml(state))

  override def flushPendingProjectChanges(): Unit =
    // Force: flush on close / popup must capture in-progress slot text.
    editor.foreach(checkWhetherProgramXmlChanged(_, allowDuringEdit = true))

  override def currentProjectXml(): Option[String] =
    editor.map(_.getProjectXML())

  private def canonicalXml(state: ProgrammingStateSnapXml): String =
    state.snapXml

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

  private def reinstallConfiguredLibraries(ide: IDEMorph): Unit =
    mountedConfig.filter(_.libraryTabs.nonEmpty).foreach { config =>
      installLibraries(config.libraryTabs, ide)
    }

  /** Snap obsolete's custom calls whose category is missing from allCategories at load time. */
  private def ensureExerciseCategoriesBeforeLoad(): Unit =
    mountedConfig.filter(_.libraryTabs.nonEmpty).foreach { config =>
      registerCustomCategoryTabs(config.libraryTabs)
    }

  /**
   * A custom block only appears in the palette tab whose name equals its category, and
   * Snap rewrites categories it does not know to `other` while loading. Neither is a
   * tab this editor shows, so move those definitions onto the Make-a-Block tab instead
   * of letting the block disappear.
   */
  private def retagCustomBlockCategories(ide: IDEMorph): Unit =
    for
      config <- mountedConfig if config.libraryTabs.nonEmpty
      target <- config.libraryTabs.find(_.includeMakeBlockButton).orElse(config.libraryTabs.headOption)
    do
      val tabNames = config.libraryTabs.map(_.name).toSet
      val retagged = customBlockDefinitions(ide).count { definition =>
        val category = definition.selectDynamic("category")
        val current = if js.isUndefined(category) || category == null then "" else category.toString
        if tabNames.contains(current) then false
        else
          definition.updateDynamic("category")(target.name)
          true
      }
      if retagged > 0 then
        ide.flushPaletteCache()
        ide.refreshPalette(true)

  /**
   * Snap's `evaluateCustomBlock` indexes `body.inputs` into `declarations`. After a
   * Python reshape that adds a slot, those two can drift (empty `body.inputs`, or a
   * missing Map entry). Either path throws at Execute and the call is replaced with
   * `Undefined!`. Rebind them from the spec Snap actually loaded.
   */
  private def repairCustomBlockParameterBindings(ide: IDEMorph): Unit =
    customBlockDefinitions(ide).foreach { definition =>
      val names =
        try definition.applyDynamic("inputNames")().asInstanceOf[js.Array[String]].toList
        catch case _: Throwable => Nil
      if names.nonEmpty then
        val declarations = definition.selectDynamic("declarations")
        if !js.isUndefined(declarations) && declarations != null then
          names.foreach { name =>
            val present =
              try declarations.applyDynamic("has")(name).asInstanceOf[Boolean]
              catch case _: Throwable => false
            if !present then
              declarations.applyDynamic("set")(name, js.Array[js.Any]("%n", "", js.undefined, false, false))
          }
        val body = definition.selectDynamic("body")
        if !js.isUndefined(body) && body != null then
          body.updateDynamic("inputs")(names.toJSArray)
    }

  /** Global definitions plus the current sprite's local ones. */
  private def customBlockDefinitions(ide: IDEMorph): List[js.Dynamic] = {
    def definitionsOf(owner: js.Dynamic, field: String): List[js.Dynamic] =
      if js.isUndefined(owner) || owner == null then Nil
      else
        val blocks = owner.selectDynamic(field)
        if js.isUndefined(blocks) || blocks == null then Nil
        else blocks.asInstanceOf[js.Array[js.Dynamic]].toList

    val dynamicIde = ide.asInstanceOf[js.Dynamic]
    definitionsOf(dynamicIde.selectDynamic("stage"), "globalBlocks") ++
      definitionsOf(dynamicIde.selectDynamic("currentSprite"), "customBlocks")
  }

  private def createEditor(world: WorldMorph, state: ProgrammingStateSnapXml, config: SnapCodeEditorConfig): IDEMorph =
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
    if hasLibraryTabs then
      registerCustomCategoryTabs(config.libraryTabs)
    restorePrimitiveBlockDictionary()
    ide.rawOpenProjectString(canonicalXml(state))
    repairCustomBlockParameterBindings(ide)
    ensureMissingGlobalVariables(ide, canonicalXml(state), refreshPalette = false)
    retagCustomBlockCategories(ide)
    ide

  private def createPreviewEditor(world: WorldMorph, state: ProgrammingStateSnapXml): IDEMorph =
    val ide = new IDEMorph(js.Dynamic.literal(
      noAutoFill = true,
      noCloud = true,
      noExitWarning = true,
      preserveTitle = true
    ))
    ide.openIn(world)
    restorePrimitiveBlockDictionary()
    ide.rawOpenProjectString(canonicalXml(state))
    ensureMissingGlobalVariables(ide, canonicalXml(state), refreshPalette = false)
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

    if libraries.exists(_.useNativeCategory) then
      spriteMorphPrototype.applyDynamic("initBlocks")()
    ensurePrimitiveSelectors(libraries.flatMap(_.selectableElements.map(_.id)))
    injectExtraPrimitives()

    // Snap calls blockTemplates(category) or blockTemplates(category, forSearch).
    // Exercise-only: only library tab names return blocks; everything else is empty.
    val blockTemplates: js.Function2[String, js.UndefOr[Boolean], js.Array[js.Any]] =
      (category: String, forSearch: js.UndefOr[Boolean]) =>
        libraries.find(_.name == category).toList.flatMap { tab =>
          if tab.useNativeCategory then
            val native = nativeCategoryPaletteItems(ide, tab.color.snapKey, forSearch)
            val extras =
              if tab.name == tab.color.snapKey then Nil
              else nativeCustomBlockPaletteItems(ide, tab.name)
            native ++ extras
          else
            val controls =
              (if tab.includeVariableControls then nativeVariablePaletteItems(ide) else Nil) ++
                (if tab.includeMakeBlockButton then nativeMakeBlockButton(ide) else Nil)
            val blocks = tab.selectableElements.flatMap { data =>
              createTemplateBlock(data) match
                case Some(block) => List(block)
                case None =>
                  println(s"Snap library: skipping unknown block selector '${data.id}'")
                  Nil
            }
            controls ++ blocks ++ nativeCustomBlockPaletteItems(ide, tab.name)
        }.toJSArray

    sprite.asInstanceOf[js.Dynamic].updateDynamic("blockTemplates")(blockTemplates)
    sprite.asInstanceOf[js.Dynamic].updateDynamic("primitivesCache")(js.Dictionary.empty[js.Any])
    sprite.paletteCache = js.Dictionary.empty
    val stage = ide.asInstanceOf[js.Dynamic].selectDynamic("stage")
    if !js.isUndefined(stage) && stage != null then
      stage.updateDynamic("blockTemplates")(blockTemplates)
      stage.updateDynamic("primitivesCache")(js.Dictionary.empty[js.Any])
    wrapFlushBlocksCacheForVariableTabs(ide, libraries.filter(needsVariablePaletteFlush).map(_.name))
    wrapCustomBlockEditMenus()
    registerCustomCategoryTabs(libraries)
    ide.createCategories()
    enlargeCategoryTabButtons(ide)
    ide.refreshPalette(true)

  private def needsVariablePaletteFlush(tab: LibraryTab): Boolean =
    tab.includeVariableControls || (tab.useNativeCategory && tab.color == SnapCategoryColor.Variables)

  private def nativeCategoryPaletteItems(
      ide: IDEMorph,
      snapKey: String,
      forSearch: js.UndefOr[Boolean]
  ): List[js.Any] = {
    val sprite = ide.currentSprite.asInstanceOf[js.Dynamic]
    val proto = spriteMorphPrototype
    val templates = proto.selectDynamic("blockTemplates")
    val raw =
      if forSearch.isDefined then templates.call(sprite, snapKey, forSearch)
      else templates.call(sprite, snapKey)
    if js.isUndefined(raw) || raw == null then Nil
    else raw.asInstanceOf[js.Array[js.Any]].toList
  }

  /**
   * Snap's native palette appends `customBlockTemplatesForCategory(category)`.
   * Our exercise `blockTemplates` override must do the same, otherwise Apply
   * (which reinstalls libraries after `rawOpenProjectString`) hides every
   * Make-a-Block definition from the tab it belongs to.
   *
   * Walk `globalBlocks` from the IDE we already hold instead of calling Snap's
   * helper: that helper uses `parentThatIsA(IDE_Morph)`, which is empty while
   * the sprite is being reparented during `rawOpen` + library reinstall.
   */
  private def nativeCustomBlockPaletteItems(ide: IDEMorph, category: String): List[js.Any] = {
    def blocksOf(owner: js.Dynamic, field: String): List[js.Dynamic] =
      if js.isUndefined(owner) || owner == null then Nil
      else
        val blocks = owner.selectDynamic(field)
        if js.isUndefined(blocks) || blocks == null then Nil
        else blocks.asInstanceOf[js.Array[js.Dynamic]].toList

    val stage = ide.asInstanceOf[js.Dynamic].selectDynamic("stage")
    val sprite = ide.currentSprite.asInstanceOf[js.Dynamic]
    (blocksOf(stage, "globalBlocks") ++ blocksOf(sprite, "customBlocks")).flatMap { definition =>
      val current = definition.selectDynamic("category")
      val currentName = if js.isUndefined(current) || current == null then "" else current.toString
      val helper = definition.selectDynamic("isHelper")
      val isHelper = !js.isUndefined(helper) && helper != null && helper.asInstanceOf[Boolean]
      if currentName != category || isHelper then Nil
      else
        try
          val template = definition.applyDynamic("templateInstance")()
          if js.isUndefined(template) || template == null then Nil
          else List(template.asInstanceOf[js.Any])
        catch case _: Throwable => Nil
    }
  }

  private def nativeVariablePaletteItems(ide: IDEMorph): List[js.Any] = {
    val sprite = ide.currentSprite.asInstanceOf[js.Dynamic]
    val items = scala.collection.mutable.ListBuffer.empty[js.Any]
    // No sprite corral in this editor — always create globals (skip Snap's local/global dialog).
    items += makeGlobalVariableButton(sprite)

    val deletable = sprite.applyDynamic("deletableVariableNames")()
    val deletableCount =
      if js.isUndefined(deletable) || deletable == null then 0
      else deletable.asInstanceOf[js.Array[js.Any]].length
    if deletableCount > 0 then
      items += makeDeleteVariableButton(sprite)

    val namesDyn = sprite.applyDynamic("allGlobalVariableNames")(true)
    val names =
      if js.isUndefined(namesDyn) || namesDyn == null then js.Array[String]()
      else namesDyn.asInstanceOf[js.Array[String]]
    names.foreach { name =>
      val blockDyn = sprite.applyDynamic("variableBlock")(name)
      if !js.isUndefined(blockDyn) && blockDyn != null then
        val block = blockDyn.asInstanceOf[BlockMorph]
        block.isDraggable = false
        block.isTemplate = true
        items += block
    }
    items.toList
  }

  /** "Make a variable" that always adds a global (no "for this sprite only" choice). */
  private def makeGlobalVariableButton(sprite: js.Dynamic): js.Any = {
    val PushButtonMorph = js.Dynamic.global.PushButtonMorph
    val DialogBoxMorph = js.Dynamic.global.DialogBoxMorph
    val BlockMorph = js.Dynamic.global.BlockMorph
    val IDE_Morph = js.Dynamic.global.IDE_Morph

    val onConfirm: js.Function1[js.UndefOr[String], Unit] = (rawName: js.UndefOr[String]) => {
      val name = rawName.toOption.map(_.trim).filter(_.nonEmpty)
      name.foreach { varName =>
        sprite.applyDynamic("addVariable")(varName, true)
        sprite.applyDynamic("toggleVariableWatcher")(varName, true)
        val ide = sprite.applyDynamic("parentThatIsA")(IDE_Morph)
        if !js.isUndefined(ide) && ide != null then
          ide.applyDynamic("flushBlocksCache")("variables")
          ide.applyDynamic("refreshPalette")()
        sprite.applyDynamic("recordUserEdit")("palette", "variable", "global", "new", varName)
      }
    }

    val button = js.Dynamic.newInstance(PushButtonMorph)(
      null,
      (() => {
        val dialog = js.Dynamic.newInstance(DialogBoxMorph)(null, onConfirm, sprite)
        dialog.applyDynamic("prompt")("Variable name", null, sprite.applyDynamic("world")())
        (): Unit
      }): js.Function0[Unit],
      "Make a variable"
    )
    button.updateDynamic("userMenu")(sprite.selectDynamic("helpMenu"))
    button.updateDynamic("selector")("addVariable")
    button.updateDynamic("showHelp")(BlockMorph.selectDynamic("prototype").selectDynamic("showHelp"))
    button
  }

  /** "Delete a variable" with a Yes/No confirm after picking a name. */
  private def makeDeleteVariableButton(sprite: js.Dynamic): js.Any = {
    val PushButtonMorph = js.Dynamic.global.PushButtonMorph
    val MenuMorph = js.Dynamic.global.MenuMorph
    val DialogBoxMorph = js.Dynamic.global.DialogBoxMorph
    val BlockMorph = js.Dynamic.global.BlockMorph

    def confirmAndDelete(varName: String): Unit = {
      val onYes: js.Function1[js.Any, Unit] = (_: js.Any) => {
        // Globals only in this editor (see makeGlobalVariableButton).
        sprite.applyDynamic("deleteVariable")(varName, true)
        (): Unit
      }
      val dialog = js.Dynamic.newInstance(DialogBoxMorph)(null, onYes)
      dialog.applyDynamic("askYesNo")(
        "Delete variable",
        s"""Delete variable "$varName"?""",
        sprite.applyDynamic("world")()
      )
    }

    val onPick: js.Function1[js.UndefOr[String], Unit] = (rawName: js.UndefOr[String]) => {
      rawName.toOption.map(_.trim).filter(_.nonEmpty).foreach(confirmAndDelete)
    }

    val button = js.Dynamic.newInstance(PushButtonMorph)(
      null,
      (() => {
        val menu = js.Dynamic.newInstance(MenuMorph)(onPick, null, sprite)
        val names = sprite.applyDynamic("deletableVariableNames")()
        if !js.isUndefined(names) && names != null then
          names.asInstanceOf[js.Array[String]].foreach { name =>
            menu.applyDynamic("addItem")(name, name, null, null, null, null, null, null, true)
          }
        menu.applyDynamic("popUpAtHand")(sprite.applyDynamic("world")())
        (): Unit
      }): js.Function0[Unit],
      "Delete a variable"
    )
    button.updateDynamic("userMenu")(sprite.selectDynamic("helpMenu"))
    button.updateDynamic("selector")("deleteVariable")
    button.updateDynamic("showHelp")(BlockMorph.selectDynamic("prototype").selectDynamic("showHelp"))
    button
  }

  /** Native create/delete flushes the built-in `variables` cache; also flush exercise tab names. */
  private def wrapFlushBlocksCacheForVariableTabs(ide: IDEMorph, tabNames: List[String]): Unit =
    if tabNames.isEmpty then return
    val dyn = ide.asInstanceOf[js.Dynamic]
    val stored = dyn.selectDynamic("__eduOriginalFlushBlocksCache")
    val original =
      if !js.isUndefined(stored) && stored != null then stored
      else
        val orig = dyn.selectDynamic("flushBlocksCache")
        dyn.updateDynamic("__eduOriginalFlushBlocksCache")(orig)
        orig
    val extra = tabNames.toJSArray
    val wrapped: js.Function1[js.UndefOr[String], Unit] = (category: js.UndefOr[String]) => {
      original.call(dyn, category)
      val cat = category.toOption.filter(_.nonEmpty)
      if cat.isEmpty || cat.contains("variables") || cat.contains("lists") then
        extra.foreach { name => original.call(dyn, name) }
    }
    dyn.updateDynamic("flushBlocksCache")(wrapped)

  /**
   * `noOwnBlocks` hides Make-a-Block / Make-a-Category, but Snap also strips
   * custom-block edit/delete from the context menu. Temporarily clear the flag
   * during `userMenu` so those items return without restoring palette create UI.
   */
  /** Snap's three custom block morphs; Scala.js forbids looking these up by name. */
  private def customBlockMorphs: List[js.Dynamic] =
    List(
      js.Dynamic.global.CustomCommandBlockMorph,
      js.Dynamic.global.CustomReporterBlockMorph,
      js.Dynamic.global.CustomHatBlockMorph
    )

  private val OriginalUserMenuField = "__eduOriginalUserMenu"

  /**
   * `noOwnBlocks` hides the Make-a-Block button, but Snap reads the same flag to strip
   * "edit..." and "delete block definition..." from a custom block's context menu
   * (`byob.js`, `CustomCommandBlockMorph.prototype.userMenu`). Clear it for the duration
   * of `userMenu` only: the palette stays free of create UI while existing blocks remain
   * editable. Each block kind keeps its own menu — they are not interchangeable.
   */
  private def wrapCustomBlockEditMenus(): Unit =
    customBlockMorphs.foreach(wrapCustomBlockUserMenu)

  private def restoreCustomBlockEditMenus(): Unit =
    customBlockMorphs.flatMap(customBlockPrototype).foreach { proto =>
      val original = proto.selectDynamic(OriginalUserMenuField)
      if !js.isUndefined(original) && original != null then
        proto.updateDynamic("userMenu")(original)
        js.Dynamic.global.Reflect.applyDynamic("deleteProperty")(proto, OriginalUserMenuField)
    }

  private def customBlockPrototype(ctor: js.Dynamic): Option[js.Dynamic] =
    if js.isUndefined(ctor) || ctor == null then None
    else
      val proto = ctor.selectDynamic("prototype")
      if js.isUndefined(proto) || proto == null then None else Some(proto)

  private def wrapCustomBlockUserMenu(ctor: js.Dynamic): Unit =
    customBlockPrototype(ctor).foreach(wrapUserMenuOnPrototype)

  private def wrapUserMenuOnPrototype(proto: js.Dynamic): Unit = {
    val stored = proto.selectDynamic(OriginalUserMenuField)
    val original =
      if !js.isUndefined(stored) && stored != null then stored
      else
        val orig = proto.selectDynamic("userMenu")
        if js.isUndefined(orig) || orig == null then null.asInstanceOf[js.Dynamic]
        else
          proto.updateDynamic(OriginalUserMenuField)(orig)
          orig
    if original == null || js.isUndefined(original) then return
    val wrapped: js.ThisFunction0[js.Dynamic, js.Any] = (self: js.Dynamic) => {
      val config = ideConfigOf(self)
      val hadNoOwnBlocks =
        config != null && !js.isUndefined(config) && {
          val flag = config.selectDynamic("noOwnBlocks")
          !js.isUndefined(flag) && flag != null && flag.asInstanceOf[Boolean]
        }
      if hadNoOwnBlocks then config.updateDynamic("noOwnBlocks")(false)
      try original.applyDynamic("call")(self)
      finally if hadNoOwnBlocks then config.updateDynamic("noOwnBlocks")(true)
    }
    proto.updateDynamic("userMenu")(wrapped)
  }

  /**
   * The IDE owning a block. Inside the Block Editor dialog the block has no IDE_Morph
   * ancestor, which is why Snap itself goes through `scriptTarget()`; the mounted
   * editor is the last resort.
   */
  private def ideConfigOf(block: js.Dynamic): js.Dynamic = {
    val ideMorph = js.Dynamic.global.IDE_Morph

    def defined(value: js.Dynamic): Option[js.Dynamic] =
      if js.isUndefined(value) || value == null then None else Some(value)

    def attempt(resolve: () => js.Dynamic): Option[js.Dynamic] =
      try defined(resolve())
      catch case _: Throwable => None

    val viaScriptTarget = attempt(() => block.applyDynamic("scriptTarget")(true))
      .flatMap(target => attempt(() => target.applyDynamic("parentThatIsA")(ideMorph)))
    val viaParent = attempt(() => block.applyDynamic("parentThatIsA")(ideMorph))
    val mounted = editor.map(_.asInstanceOf[js.Dynamic])

    viaScriptTarget
      .orElse(viaParent)
      .orElse(mounted)
      .flatMap(ide => defined(ide.selectDynamic("config")))
      .getOrElse(null.asInstanceOf[js.Dynamic])
  }

  private def spriteMorphPrototype: js.Dynamic =
    js.Dynamic.global
      .selectDynamic("SpriteMorph")
      .selectDynamic("prototype")

  /** Snap's Scene.toXML assigns `SpriteMorph.prototype.blocks` to an array of
    * bootstrapped custom primitives and does not put the dictionary back.
    * `populateCustomBlocks` runs before `initBlocks` on the next rawOpen, so
    * definition bodies load as Undefined! unless we restore the dictionary first.
    */
  private def restorePrimitiveBlockDictionary(): Unit =
    try
      spriteMorphPrototype.applyDynamic("initBlocks")()
      injectExtraPrimitives()
    catch case _: Throwable => ()

  private val SceneGlobalVariables = """(?s)</stage><variables>(.*?)</variables>""".r
  private val VariableNameAttribute = """<variable\b[^>]*\bname="([^"]*)"""".r

  /** Scene `<variables>` after `</stage>` are Snap globals. Empty sprite `<variables>` are ignored. */
  private def sceneGlobalVariableNames(xml: String): List[String] =
    SceneGlobalVariables.findFirstMatchIn(xml).toList.flatMap { matched =>
      VariableNameAttribute
        .findAllMatchIn(matched.group(1))
        .map(found => unescapeXmlAttribute(found.group(1)))
        .filter(_.nonEmpty)
        .toList
    }

  private def unescapeXmlAttribute(value: String): String =
    value
      .replace("&amp;", "&")
      .replace("&apos;", "'")
      .replace("&quot;", "\"")
      .replace("&gt;", ">")
      .replace("&lt;", "<")

  /**
   * Snap shows a set-block name even when the global frame has no such variable.
   * Register any scene variable the loader skipped, without resetting one that already exists.
   */
  private def ensureMissingGlobalVariables(ide: IDEMorph, xml: String, refreshPalette: Boolean): Unit =
    val names = sceneGlobalVariableNames(xml)
    if names.isEmpty then return
    val sprite = ide.currentSprite
    if sprite == null then return
    val spriteDyn = sprite.asInstanceOf[js.Dynamic]
    val globals = spriteDyn.applyDynamic("globalVariables")()
    if js.isUndefined(globals) || globals == null then return
    val vars = globals.asInstanceOf[js.Dynamic].selectDynamic("vars")
    var added = false
    names.foreach { name =>
      val existing =
        if js.isUndefined(vars) || vars == null then js.undefined
        else vars.asInstanceOf[js.Dynamic].selectDynamic(name)
      val missing = js.isUndefined(existing) || existing == null
      if missing then
        spriteDyn.applyDynamic("addVariable")(name, true)
        added = true
    }
    if added && refreshPalette then
      ide.asInstanceOf[js.Dynamic].applyDynamic("flushBlocksCache")("variables")
      ide.refreshPalette(true)

  private def snapshotProjectXml(ide: IDEMorph): String =
    val xml = ide.getProjectXML()
    restorePrimitiveBlockDictionary()
    xml

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
    injectExtraPrimitives()

  /** TurtleStitch embroidery / circle / home / backward specs are not in vanilla Snap. */
  private def injectExtraPrimitives(): Unit =
    val proto = spriteMorphPrototype
    val blocks = proto.selectDynamic("blocks")
    val blockColor = proto.selectDynamic("blockColor")
    val Color = js.Dynamic.global.Color
    val embroideryColor = blockColor.selectDynamic("embroidery")
    if js.isUndefined(embroideryColor) || embroideryColor == null then
      blockColor.updateDynamic("embroidery")(js.Dynamic.newInstance(Color)(0, 120, 0))
    SnapTurtleCatalog.ExtraPrimitives.foreach { primitive =>
      primitive.extraSpec.foreach { spec =>
        val obj = js.Dynamic.literal()
        obj.updateDynamic("type")("command")
        obj.updateDynamic("category")(spec.category)
        obj.updateDynamic("spec")(spec.spec)
        obj.updateDynamic("only")(js.Dynamic.global.SpriteMorph)
        if spec.defaults.nonEmpty then
          val defaults = spec.defaults.map {
            case b: Boolean => b
            case n: Int => n
            case n: Long => n.toInt
            case n: Double => n
            case other => other.toString
          }.toJSArray
          obj.updateDynamic("defaults")(defaults)
        blocks.updateDynamic(primitive.snapSelector)(obj)
      }
    }

  private def nativeMakeBlockButton(ide: IDEMorph): List[js.Any] = {
    val sprite = ide.currentSprite.asInstanceOf[js.Dynamic]
    val button = sprite.applyDynamic("makeBlockButton")("other")
    if js.isUndefined(button) || button == null then Nil
    else List(button.asInstanceOf[js.Any])
  }

  /** Mirror Snap's palette `block()` helper with explicit `this` = prototype. */
  private def createTemplateBlock(data: LibraryBlock): Option[BlockMorph] =
    val proto = spriteMorphPrototype
    def invoke(): js.Dynamic =
      proto.selectDynamic("blockForSelector").call(proto, data.id, true)

    var raw = invoke()
    if js.isUndefined(raw) || raw == null then
      proto.applyDynamic("initBlocks")()
      injectExtraPrimitives()
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

  /** Register exercise tabs on Snap's customCategories Map (name → Color). */
  private def registerCustomCategoryTabs(libraries: List[LibraryTab]): Unit =
    val customCategories = spriteMorphCustomCategories
    val blockColor = spriteMorphPrototype.selectDynamic("blockColor")
    libraries.foreach { tab =>
      val resolved = blockColor.selectDynamic(tab.color.snapKey)
      val color =
        if js.isUndefined(resolved) || resolved == null then blockColor.selectDynamic("other")
        else resolved
      customCategories.applyDynamic("set")(tab.name, color)
      // eduLibraryTabs also look up colors via blockColor[name] for prim-category buttons.
      blockColor.updateDynamic(tab.name)(color)
    }
    installedCustomCategoryNames = libraries.map(_.name)

  private def clearInstalledCustomCategories(): Unit =
    val customCategories = spriteMorphCustomCategories
    val blockColor = spriteMorphPrototype.selectDynamic("blockColor")
    installedCustomCategoryNames.foreach { name =>
      customCategories.applyDynamic("delete")(name)
      js.Dynamic.global.Reflect.applyDynamic("deleteProperty")(blockColor, name)
    }
    installedCustomCategoryNames = Nil

  // Morphic paints category tabs on a canvas, so it cannot consume CSS directly.
  // Read the shared dimensions at the DOM boundary instead of duplicating them.
  private def cssPixels(property: String): Option[Double] =
    mountedCanvas.flatMap { canvas =>
      dom.window.getComputedStyle(canvas).getPropertyValue(property).trim.stripSuffix("px")
        .toDoubleOption.filter(value => value.isFinite && value >= 0)
    }

  /** Make visible category tabs taller and restack them after Snap's default layout. */
  private def enlargeCategoryTabButtons(ide: IDEMorph): Unit =
    val categories = ide.asInstanceOf[js.Dynamic].selectDynamic("categories")
    if js.isUndefined(categories) || categories == null then return
    val buttons = categories.selectDynamic("buttons").asInstanceOf[js.UndefOr[js.Array[js.Dynamic]]]
      .toOption
      .getOrElse(js.Array())
    val visible = buttons.filter { button =>
      button.selectDynamic("isVisible").asInstanceOf[Boolean]
    }
    if visible.isEmpty then return

    val dimensions = List("--snap-category-row-gap", "--snap-category-border", "--snap-category-padding", "--snap-category-label-growth").map(cssPixels)
    // Without a stylesheet, retain Snap's own default category layout.
    if dimensions.exists(_.isEmpty) then return
    val yPadding = dimensions(0).get
    val border = dimensions(1).get
    val first = visible(0)
    val left = first.applyDynamic("left")().asInstanceOf[Double]
    var top = first.applyDynamic("top")().asInstanceOf[Double]

    visible.foreach { button =>
      button.updateDynamic("padding")(dimensions(2).get)
      // Drop Snap's default label shadow — it fights colored category tabs.
      button.updateDynamic("labelShadowOffset")(new SnapPoint(0, 0))
      button.updateDynamic("labelShadowColor")(new SnapColor(0, 0, 0, 0))
      val label = button.selectDynamic("label")
      if !js.isUndefined(label) && label != null then
        val fontSize = label.selectDynamic("fontSize")
        if !js.isUndefined(fontSize) && fontSize != null then
          label.updateDynamic("fontSize")(fontSize.asInstanceOf[Double] + dimensions(3).get)
        if label.selectDynamic("fixLayout").asInstanceOf[js.UndefOr[js.Function0[Unit]]].isDefined then
          label.applyDynamic("fixLayout")()
      button.applyDynamic("fixLayout")()
      button.applyDynamic("refresh")()
      button.applyDynamic("setPosition")(new SnapPoint(left, top))
      val height = button.applyDynamic("height")().asInstanceOf[Double]
      top += height + yPadding
    }

    categories.applyDynamic("setHeight")(top - categories.applyDynamic("top")().asInstanceOf[Double] + border)

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
    world.keyboardHandler.classList.add("snap-keyboard-handler")

  override def startWorldCycles(): Unit =
    if !cyclesRunning && editorWorld.nonEmpty then
      cyclesRunning = true
      tickEditor()

  override def pauseWorldCycles(): Unit =
    // Always flush before stopping the poll loop so close/unmount cannot drop
    // edits that happened since the last 500ms check.
    flushPendingProjectChanges()
    stopGreenFlagOnStage()
    cyclesRunning = false
    if frameHandle != 0 then dom.window.cancelAnimationFrame(frameHandle)
    frameHandle = 0

  override def runGreenFlagOnStage(mirrorTarget: Canvas): Unit =
    // Execute can be pressed after reopen/unmount cycles; ensure Morphic is ticking.
    startWorldCycles()
    editor match
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
        stageMirrorIdleFrames = 0
        lastStageMirrorAt = 0.0
        mirrorStageTo(stage, mirrorTarget, force = true)
        def tick(ts: Double): Unit =
          mirrorStageTo(stage, mirrorTarget, force = false, nowMs = ts)
          val running = processesStillRunning(stage)
          if running then stageMirrorIdleFrames = 0
          else stageMirrorIdleFrames += 1
          if stageMirrorIdleFrames < StageMirrorIdleSettleFrames then
            stageMirrorRafHandle = dom.window.requestAnimationFrame(t => tick(t))
          else
            stageMirrorRafHandle = 0
            mirrorStageTo(stage, mirrorTarget, force = true, nowMs = ts)
            restoreGreenFlagStepping()
        stageMirrorRafHandle = dom.window.requestAnimationFrame(ts => tick(ts))

  override def stopGreenFlagOnStage(): Unit =
    if stageMirrorRafHandle != 0 then
      dom.window.cancelAnimationFrame(stageMirrorRafHandle)
      stageMirrorRafHandle = 0
    stageMirrorIdleFrames = 0
    editor.foreach { ide =>
      try ide.stopAllScripts()
      catch case _: Throwable => ()
    }
    restoreGreenFlagStepping()

  override def setGreenFlagStepMs(ms: Double): Unit =
    greenFlagStepMs = math.max(0.0, ms)

  /** Snap visible stepping: pause `greenFlagStepMs` between blocks (0 = no step delay). */
  private def enableGreenFlagStepping(): Unit =
    val proto = js.Dynamic.global.Process.selectDynamic("prototype")
    if savedFlashTime.isEmpty then
      savedFlashTime = Some(proto.selectDynamic("flashTime").asInstanceOf[Double])
    if savedSingleStepping.isEmpty then
      savedSingleStepping = Some(proto.selectDynamic("enableSingleStepping").asInstanceOf[Boolean])
    if greenFlagStepMs <= 0 then
      proto.updateDynamic("flashTime")(0)
      proto.updateDynamic("enableSingleStepping")(false)
    else
      proto.updateDynamic("flashTime")(greenFlagStepMs / 1000.0)
      proto.updateDynamic("enableSingleStepping")(true)

  private def restoreGreenFlagStepping(): Unit =
    val proto = js.Dynamic.global.Process.selectDynamic("prototype")
    savedFlashTime.foreach(proto.updateDynamic("flashTime")(_))
    savedSingleStepping.foreach(proto.updateDynamic("enableSingleStepping")(_))
    savedFlashTime = None
    savedSingleStepping = None

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
    if !force && nowMs - lastStageMirrorAt < StageMirrorMinIntervalMs then return
    lastStageMirrorAt = nowMs
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
    projectXmlChangedCallback = callback

  private def tickEditor(): Unit =
    if cyclesRunning then
      editorWorld.foreach(_.doOneCycle())
      frameHandle = dom.window.requestAnimationFrame(_ => tickEditor())
      // Skip XML serialization while Execute is mirroring — getProjectXML is heavy
      // and competes with Morphic + stage blit for main-thread time.
      if stageMirrorRafHandle != 0 then return
      val now = dom.window.performance.now()
      if now - lastProjectXmlCheckAt >= ProjectXmlCheckIntervalMs then
        lastProjectXmlCheckAt = now
        editor.foreach(checkWhetherProgramXmlChanged(_))

  private def initializeProjectChangeTracking(ide: IDEMorph): Unit =
    lastProjectXml = Some(snapshotProjectXml(ide))
    lastProjectXmlCheckAt = dom.window.performance.now()

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
    if !allowDuringEdit && isTextEditing then return
    // Commit in-progress slot text before serializing (needed for flush-on-close).
    if allowDuringEdit && isTextEditing then
      editorWorld.foreach(_.stopEditing())
    val xml = snapshotProjectXml(ide)
    if !lastProjectXml.contains(xml) then
      lastProjectXml = Some(xml)
      println("Snap! code changed!")
      projectXmlChangedCallback(xml)

  /** True while Morphic has an active CursorMorph for an input slot. */
  private def isTextEditing: Boolean =
    editorWorld.exists(world => world.cursor != null)

  private def stopEditorSession(): Unit =
    editor.foreach(checkWhetherProgramXmlChanged(_, allowDuringEdit = true))
    pauseWorldCycles()
    disconnectResizeObserver()
    clearInstalledCustomCategories()
    restoreCustomBlockEditMenus()
    editor.foreach(_.destroy())
    editorWorld.foreach(_.destroy())
    editor = None
    editorWorld = None
    mountedCanvas = None
    mountedConfig = None
    lastProjectXml = None
    lastLoadedXml = None
    lastProjectXmlCheckAt = 0.0
    originalBlockTemplates = None

  override def destroy(): Unit =
    stopEditorSession()
