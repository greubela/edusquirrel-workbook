package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.state.Var
import it.evadid.facades.Snap.*
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.{LibraryBlock, LibraryTab, SnapCategoryColor}
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.SnapExtraPrimitives
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

import SnapProjectRuntime.*

/** Installs exercise palettes and owns the native customizations it must release. */
private[SnapEditor] final class SnapPaletteInstaller(resources: Var[SnapSessionResources]):
  private case class Installation(originalBlockTemplates: Option[js.Any] = None,
      installedCustomCategoryNames: Seq[String] = Nil)
  private val installation = Var(Installation())

  def release(): Unit =
    clearInstalledCustomCategories()
    restoreCustomBlockEditMenus()
    installation.set(Installation())

  def reinstallConfiguredLibraries(ide: IDEMorph): Unit =
    resources.now().mountedConfig.filter(_.libraryTabs.nonEmpty).foreach { config =>
      installLibraries(config.libraryTabs, ide)
    }

  /** Snap obsolete's custom calls whose category is missing from allCategories at load time. */
  def ensureExerciseCategoriesBeforeLoad(): Unit =
    resources.now().mountedConfig.filter(_.libraryTabs.nonEmpty).foreach { config =>
      registerCustomCategoryTabs(config.libraryTabs)
    }

  /**
   * A custom block only appears in the palette tab whose name equals its category, and
   * Snap rewrites categories it does not know to `other` while loading. Neither is a
   * tab this editor shows, so move those definitions onto the Make-a-Block tab instead
   * of letting the block disappear.
   */
  def retagCustomBlockCategories(ide: IDEMorph): Unit =
    for
      config <- resources.now().mountedConfig if config.libraryTabs.nonEmpty
      target <- config.libraryTabs.find(_.includeMakeBlockButton).orElse(config.libraryTabs.headOption)
    do
      val tabNames = config.libraryTabs.map(_.name).toSet
      val retagged = customBlockDefinitions(ide).count { definition =>
        val category = definition.selectDynamic("category")
        val current = if js.isUndefined(category) || category == null then "" else category.toString
        // Predefined module blocks are shown where the palette lists them.
        if tabNames.contains(current) || moduleBlockOf(definition).nonEmpty then false
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
  private def moduleBlockOf(definition: js.Dynamic): Option[SnapModuleBlocks.ModuleBlock] =
    val spec = definition.selectDynamic("spec")
    if js.isUndefined(spec) || spec == null then None else SnapModuleBlocks.bySpec(spec.toString)

  /**
   * Adds the definitions of predefined module blocks the palette offers but the project lacks (a program
   * only carries them once it calls the module), as Snap imports a blocks library. The data variable is
   * added only when missing, so the list a student changed while trying blocks stays.
   */
  private def installModuleBlocks(ide: IDEMorph, blocks: Seq[SnapModuleBlocks.ModuleBlock]): Unit =
    val installed = customBlockDefinitions(ide).flatMap(moduleBlockOf).toSet
    val missing = blocks.filterNot(installed.contains)
    val dynamicIde = ide.asInstanceOf[js.Dynamic]
    val stage = dynamicIde.selectDynamic("stage")
    if missing.nonEmpty && !js.isUndefined(stage) && stage != null then
      val loaded = dynamicIde.selectDynamic("serializer").applyDynamic("loadBlocks")(SnapModuleBlocks.libraryXml(missing), stage)
      loaded.selectDynamic("global").asInstanceOf[js.Array[js.Dynamic]].foreach { definition =>
        definition.updateDynamic("receiver")(stage)
        stage.selectDynamic("globalBlocks").applyDynamic("push")(definition)
      }
      val globals = dynamicIde.selectDynamic("globalVariables")
      val existing = globals.selectDynamic("vars").selectDynamic(SnapModuleBlocks.NumberListVariable)
      val data = loaded.selectDynamic("data")
      if (js.isUndefined(existing) || existing == null) && !js.isUndefined(data) && data != null then
        globals.applyDynamic("merge")(data)
      ide.flushPaletteCache()

  /** The palette template of a module block, from its installed definition. */
  private def moduleTemplateBlock(ide: IDEMorph, block: SnapModuleBlocks.ModuleBlock): Option[BlockMorph] =
    customBlockDefinitions(ide).find(moduleBlockOf(_).contains(block)).flatMap { definition =>
      try
        val template = definition.applyDynamic("templateInstance")()
        Option.when(!js.isUndefined(template) && template != null)(template.asInstanceOf[BlockMorph])
      catch case _: Throwable => None
    }

  /** Global definitions plus the current sprite's local ones. */
  def installLibraries(libraries: Seq[LibraryTab], ide: IDEMorph): Unit =
    require(libraries.map(_.name).distinct.size == libraries.size, "Snap library tab names must be unique")
    require(libraries.forall(_.name.nonEmpty), "Snap library tab names must not be empty")
    val sprite = ide.currentSprite
    installation.update(value => value.copy(originalBlockTemplates = Some(sprite.asInstanceOf[js.Dynamic].selectDynamic("blockTemplates"))))

    clearInstalledCustomCategories()
    ide.asInstanceOf[js.Dynamic].updateDynamic("currentCategory")(libraries.head.name)

    if libraries.exists(_.useNativeCategory) then
      spriteMorphPrototype.applyDynamic("initBlocks")()
    val (moduleIds, primitiveIds) = libraries.flatMap(_.selectableElements.map(_.id)).partition(SnapModuleBlocks.byId(_).nonEmpty)
    ensurePrimitiveSelectors(primitiveIds)
    SnapExtraPrimitives.install()
    installModuleBlocks(ide, moduleIds.flatMap(SnapModuleBlocks.byId).distinct)

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
              SnapModuleBlocks.byId(data.id).fold(createTemplateBlock(data))(moduleTemplateBlock(ide, _)) match
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

    val button = Snap.button(
      (() => {
        val dialog = Snap.dialog(onConfirm, sprite).createLoadedInstance().asInstanceOf[js.Dynamic]
        dialog.applyDynamic("prompt")("Variable name", null, sprite.applyDynamic("world")())
        (): Unit
      }): js.Function0[Unit],
      "Make a variable"
    ).createLoadedInstance().asInstanceOf[js.Dynamic]
    button.updateDynamic("userMenu")(sprite.selectDynamic("helpMenu"))
    button.updateDynamic("selector")("addVariable")
    button.updateDynamic("showHelp")(BlockMorph.selectDynamic("prototype").selectDynamic("showHelp"))
    button
  }

  /** "Delete a variable" with a Yes/No confirm after picking a name. */
  private def makeDeleteVariableButton(sprite: js.Dynamic): js.Any = {
    val BlockMorph = js.Dynamic.global.BlockMorph

    def confirmAndDelete(varName: String): Unit = {
      val onYes: js.Function1[js.Any, Unit] = (_: js.Any) => {
        // Globals only in this editor (see makeGlobalVariableButton).
        sprite.applyDynamic("deleteVariable")(varName, true)
        (): Unit
      }
      val dialog = Snap.dialog(onYes).createLoadedInstance().asInstanceOf[js.Dynamic]
      dialog.applyDynamic("askYesNo")(
        "Delete variable",
        s"""Delete variable "$varName"?""",
        sprite.applyDynamic("world")()
      )
    }

    val onPick: js.Function1[js.UndefOr[String], Unit] = (rawName: js.UndefOr[String]) => {
      rawName.toOption.map(_.trim).filter(_.nonEmpty).foreach(confirmAndDelete)
    }

    val button = Snap.button(
      (() => {
        val menu = Snap.menu(onPick, sprite).createLoadedInstance().asInstanceOf[js.Dynamic]
        val names = sprite.applyDynamic("deletableVariableNames")()
        if !js.isUndefined(names) && names != null then
          names.asInstanceOf[js.Array[String]].foreach { name =>
            menu.applyDynamic("addItem")(name, name, null, null, null, null, null, null, true)
          }
        menu.applyDynamic("popUpAtHand")(sprite.applyDynamic("world")())
        (): Unit
      }): js.Function0[Unit],
      "Delete a variable"
    ).createLoadedInstance().asInstanceOf[js.Dynamic]
    button.updateDynamic("userMenu")(sprite.selectDynamic("helpMenu"))
    button.updateDynamic("selector")("deleteVariable")
    button.updateDynamic("showHelp")(BlockMorph.selectDynamic("prototype").selectDynamic("showHelp"))
    button
  }

  /** Native create/delete flushes the built-in `variables` cache; also flush exercise tab names. */
  private def wrapFlushBlocksCacheForVariableTabs(ide: IDEMorph, tabNames: Seq[String]): Unit =
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

  def restoreCustomBlockEditMenus(): Unit =
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
    val mounted = resources.now().editor.map(_.asInstanceOf[js.Dynamic])

    viaScriptTarget
      .orElse(viaParent)
      .orElse(mounted)
      .flatMap(ide => defined(ide.selectDynamic("config")))
      .getOrElse(null.asInstanceOf[js.Dynamic])
  }

  private def ensurePrimitiveSelectors(ids: Seq[String]): Unit =
    val proto = spriteMorphPrototype
    val blocks = proto.selectDynamic("blocks")
    val missing = ids.filter { id =>
      val info = blocks.selectDynamic(id)
      js.isUndefined(info) || info == null
    }
    if missing.nonEmpty then
      proto.applyDynamic("initBlocks")()
    SnapExtraPrimitives.install()

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

    val first = invoke()
    val raw = if js.isUndefined(first) || first == null then
      proto.applyDynamic("initBlocks")()
      SnapExtraPrimitives.install()
      invoke()
    else first
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

  def removeAllLibraries(includeDefaultLibraries: Boolean): Unit =
    resources.now().editor.foreach { ideMorph =>
      val sprite = ideMorph.currentSprite
      val original = installation.now().originalBlockTemplates.getOrElse {
        val templates = sprite.asInstanceOf[js.Dynamic].selectDynamic("blockTemplates")
        installation.update(value => value.copy(originalBlockTemplates = Some(templates)))
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
    if !includeDefaultLibraries then installation.update(value => value.copy(originalBlockTemplates = None))

  private def spriteMorphCustomCategories: js.Dynamic =
    spriteMorphPrototype.selectDynamic("customCategories")

  /** Register exercise tabs on Snap's customCategories Map (name → Color). */
  def registerCustomCategoryTabs(libraries: Seq[LibraryTab]): Unit =
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
    installation.update(value => value.copy(installedCustomCategoryNames = libraries.map(_.name)))

  def clearInstalledCustomCategories(): Unit =
    val customCategories = spriteMorphCustomCategories
    val blockColor = spriteMorphPrototype.selectDynamic("blockColor")
    installation.now().installedCustomCategoryNames.foreach { name =>
      customCategories.applyDynamic("delete")(name)
      js.Dynamic.global.Reflect.applyDynamic("deleteProperty")(blockColor, name)
    }
    installation.update(value => value.copy(installedCustomCategoryNames = Nil))

  // Morphic paints category tabs on a canvas, so it cannot consume CSS directly.
  // Read the shared dimensions at the DOM boundary instead of duplicating them.
  private def cssPixels(property: String): Option[Double] =
    resources.now().mountedCanvas.flatMap { canvas =>
      dom.window.getComputedStyle(canvas).getPropertyValue(property).trim.stripSuffix("px")
        .toDoubleOption.filter(value => value.isFinite && value >= 0)
    }

  /** Make visible category tabs taller and restack them after Snap's default layout. */
  def enlargeCategoryTabButtons(ide: IDEMorph): Unit =
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
    val top = visible.foldLeft(first.applyDynamic("top")().asInstanceOf[Double]) { (currentTop, button) =>
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
      button.applyDynamic("setPosition")(new SnapPoint(left, currentTop))
      val height = button.applyDynamic("height")().asInstanceOf[Double]
      currentTop + height + yPadding
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

