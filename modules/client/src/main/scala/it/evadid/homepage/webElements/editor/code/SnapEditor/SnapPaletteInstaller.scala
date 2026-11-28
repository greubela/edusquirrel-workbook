package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.state.Var
import it.evadid.facades.Snap.*
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.SnapExtraPrimitives
import it.evadid.workbook.interactions.programming.state.snap.*
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

import SnapProjectRuntime.*

/** Installs exercise palettes and owns the native customizations it must release: the replaced block templates,
  * the exercise categories ([[SnapExerciseCategories]]) and the custom block menus ([[SnapCustomBlockMenus]]).
  */
private[SnapEditor] final class SnapPaletteInstaller(resources: Var[SnapSessionResources]):
  private val originalBlockTemplates = Var(Option.empty[js.Any])
  private val categories = SnapExerciseCategories(() => resources.now().mountedCanvas)
  private val menus = SnapCustomBlockMenus(() => resources.now().editor)

  private def configuredLibraries: Seq[LibraryTab] = resources.now().mountedConfig.toSeq.flatMap(_.libraryTabs)

  def release(): Unit =
    categories.clear()
    menus.restore()
    originalBlockTemplates.set(None)

  def reinstallConfiguredLibraries(ide: IDEMorph): Unit =
    resources.now().mountedConfig.filter(_.libraryTabs.nonEmpty).foreach { config =>
      installLibraries(config.libraryTabs, ide)
    }

  /** Snap obsolete's custom calls whose category is missing from allCategories at load time. */
  def ensureExerciseCategoriesBeforeLoad(): Unit =
    if configuredLibraries.nonEmpty then categories.register(configuredLibraries)

  /** Custom blocks whose category no exercise tab shows move to the Make-a-Block tab. */
  def retagCustomBlockCategories(ide: IDEMorph): Unit =
    if configuredLibraries.nonEmpty then categories.retag(ide, configuredLibraries)

  def registerCustomCategoryTabs(libraries: Seq[LibraryTab]): Unit = categories.register(libraries)

  /** Global definitions plus the current sprite's local ones. */
  def installLibraries(libraries: Seq[LibraryTab], ide: IDEMorph): Unit =
    require(libraries.map(_.name).distinct.size == libraries.size, "Snap library tab names must be unique")
    require(libraries.forall(_.name.nonEmpty), "Snap library tab names must not be empty")
    val sprite = ide.currentSprite
    originalBlockTemplates.set(Some(sprite.asInstanceOf[js.Dynamic].selectDynamic("blockTemplates")))

    categories.clear()
    ide.asInstanceOf[js.Dynamic].updateDynamic("currentCategory")(libraries.head.name)

    if libraries.exists(_.useNativeCategory) then
      spriteMorphPrototype.applyDynamic("initBlocks")()
    val (moduleIds, primitiveIds) = libraries.flatMap(_.selectableElements.map(_.id)).partition(SnapModuleBlocks.byId(_).nonEmpty)
    ensurePrimitiveSelectors(primitiveIds)
    SnapExtraPrimitives.install()
    SnapCustomBlocks.installModuleBlocks(ide, moduleIds.flatMap(SnapModuleBlocks.byId).distinct)

    val blockTemplates = exerciseBlockTemplates(libraries, ide)

    // The palette's primitive selectors; custom and module blocks are matched by their definitions instead.
    lazy val offeredSelectors: Set[String] = libraries.flatMap(tab => blockTemplates(tab.name, true).toList).flatMap { item =>
      val selector = item.asInstanceOf[js.Dynamic].selectDynamic("selector")
      Option.when(js.typeOf(selector) == "string")(selector.toString)
    }.toSet
    val blocksMatching = searchWithinPalette(offeredSelectors)

    overridePalette(sprite.asInstanceOf[js.Dynamic], blockTemplates, blocksMatching)
    sprite.paletteCache = js.Dictionary.empty
    val stage = ide.asInstanceOf[js.Dynamic].selectDynamic("stage")
    if !SnapJs.isAbsent(stage) then overridePalette(stage, blockTemplates, blocksMatching)
    SnapVariableButtons.flushWithExerciseTabs(ide, libraries.filter(needsVariablePaletteFlush).map(_.name))
    menus.wrap()
    categories.register(libraries)
    ide.createCategories()
    categories.enlargeTabs(ide)
    ide.refreshPalette(true)

  /** Snap calls blockTemplates(category) or blockTemplates(category, forSearch).
    * Exercise-only: only library tab names return blocks; everything else is empty.
    */
  private def exerciseBlockTemplates(libraries: Seq[LibraryTab], ide: IDEMorph)
      : js.Function2[String, js.UndefOr[Boolean], js.Array[js.Any]] =
    (category: String, forSearch: js.UndefOr[Boolean]) =>
      libraries.find(_.name == category).toList.flatMap(tabPaletteItems(ide, _, forSearch)).toJSArray

  private def tabPaletteItems(ide: IDEMorph, tab: LibraryTab, forSearch: js.UndefOr[Boolean]): List[js.Any] =
    if tab.useNativeCategory then
      val native = nativeCategoryPaletteItems(ide, tab.color.snapKey, forSearch)
      val extras =
        if tab.name == tab.color.snapKey then Nil
        else SnapCustomBlocks.paletteItems(ide, tab.name)
      native ++ extras
    else
      val controls =
        (if tab.includeVariableControls then SnapVariableButtons.paletteItems(ide) else Nil) ++
          (if tab.includeMakeBlockButton then nativeMakeBlockButton(ide) else Nil)
      val blocks = tab.selectableElements.flatMap { data =>
        SnapModuleBlocks.byId(data.id).fold(createTemplateBlock(data))(SnapCustomBlocks.moduleTemplateBlock(ide, _)) match
          case Some(block) => List(block)
          case None =>
            println(s"Snap library: skipping unknown block selector '${data.id}'")
            Nil
      }
      controls ++ blocks ++ SnapCustomBlocks.paletteItems(ide, tab.name)

  /** Exercise palette lookups on a sprite or the stage; their cached primitives are rebuilt. */
  private def overridePalette(target: js.Dynamic, blockTemplates: js.Any, blocksMatching: js.Any): Unit =
    target.updateDynamic("blockTemplates")(blockTemplates)
    target.updateDynamic("blocksMatching")(blocksMatching)
    target.updateDynamic("primitivesCache")(js.Dictionary.empty[js.Any])


  /**
   * Snap's block search lists every primitive it knows, including those the exercise palette leaves out
   * and that the conversion does not support. Keep primitives the palette offers, plus custom and module
   * blocks (they carry a definition) and variable getters.
   */
  private def searchWithinPalette(offeredSelectors: => Set[String])
      : js.ThisFunction4[js.Dynamic, js.Any, js.Any, js.Any, js.Any, js.Array[js.Dynamic]] =
    (self: js.Dynamic, search: js.Any, strictly: js.Any, types: js.Any, varNames: js.Any) =>
      val original = spriteMorphPrototype.selectDynamic("blocksMatching")
      original.applyDynamic("call")(self, search, strictly, types, varNames).asInstanceOf[js.Array[js.Dynamic]].filter { block =>
        val definition = block.selectDynamic("definition")
        val selector = block.selectDynamic("selector")
        !SnapJs.isAbsent(definition) ||
          (js.typeOf(selector) == "string" && (selector.toString == "reportGetVar" || offeredSelectors.contains(selector.toString)))
      }

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
    if SnapJs.isAbsent(raw) then Nil
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
  private def ensurePrimitiveSelectors(ids: Seq[String]): Unit =
    val proto = spriteMorphPrototype
    val blocks = proto.selectDynamic("blocks")
    val missing = ids.filter(id => SnapJs.isAbsent(blocks.selectDynamic(id)))
    if missing.nonEmpty then
      proto.applyDynamic("initBlocks")()
    SnapExtraPrimitives.install()

  private def nativeMakeBlockButton(ide: IDEMorph): List[js.Any] = {
    val sprite = ide.currentSprite.asInstanceOf[js.Dynamic]
    val button = sprite.applyDynamic("makeBlockButton")("other")
    if SnapJs.isAbsent(button) then Nil
    else List(button.asInstanceOf[js.Any])
  }

  /** Mirror Snap's palette `block()` helper with explicit `this` = prototype. */
  private def createTemplateBlock(data: LibraryBlock): Option[BlockMorph] =
    val proto = spriteMorphPrototype
    def invoke(): js.Dynamic =
      proto.selectDynamic("blockForSelector").call(proto, data.id, true)

    val first = invoke()
    val raw = if SnapJs.isAbsent(first) then
      proto.applyDynamic("initBlocks")()
      SnapExtraPrimitives.install()
      invoke()
    else first
    if SnapJs.isAbsent(raw) then None
    else
      val block = raw.asInstanceOf[BlockMorph]
      block.isDraggable = false
      block.isTemplate = true
      if data.snap_description_line.nonEmpty then
        val spec = block.asInstanceOf[js.Dynamic].selectDynamic("blockSpec")
        val specStr = if SnapJs.isAbsent(spec) then "" else spec.toString
        block.setSpec(descriptionWithNativeInputs(data.snap_description_line, specStr))
      Some(block)

  def removeAllLibraries(includeDefaultLibraries: Boolean): Unit =
    resources.now().editor.foreach { ideMorph =>
      val sprite = ideMorph.currentSprite
      val original = originalBlockTemplates.now().getOrElse {
        val templates = sprite.asInstanceOf[js.Dynamic].selectDynamic("blockTemplates")
        originalBlockTemplates.set(Some(templates))
        templates
      }
      val templates = if includeDefaultLibraries then
        ((_: String, _: js.UndefOr[Boolean]) => js.Array[BlockMorph]())
          .asInstanceOf[js.Function2[String, js.UndefOr[Boolean], js.Array[BlockMorph]]]
      else original
      sprite.asInstanceOf[js.Dynamic].updateDynamic("blockTemplates")(templates)
      // Without exercise tabs the search covers Snap's own palette again.
      js.special.delete(sprite, "blocksMatching")
      sprite.asInstanceOf[js.Dynamic].updateDynamic("primitivesCache")(js.Dictionary.empty[js.Any])
      sprite.paletteCache = js.Dictionary.empty
      categories.clear()
      val ideConfig = ideMorph.asInstanceOf[js.Dynamic].selectDynamic("config")
      ideConfig.updateDynamic("eduLibraryTabs")(js.Array())
      ideConfig.updateDynamic("eduEmptyLibrary")(includeDefaultLibraries)
      ideConfig.updateDynamic("noDefaultCat")(false)
      ideMorph.asInstanceOf[js.Dynamic].updateDynamic("currentCategory")("motion")
      ideMorph.createCategories()
      ideMorph.refreshPalette(true)
    }
    if !includeDefaultLibraries then originalBlockTemplates.set(None)

  /** `_` is deliberately only presentation syntax. The selector's native
    * placeholders remain authoritative for numeric, boolean and nested inputs.
    */
  private def descriptionWithNativeInputs(description: String, nativeSpec: String): String =
    val placeholders = "%[^ ]+".r.findAllIn(nativeSpec).toList.iterator
    description.foldLeft(new StringBuilder) { (result, character) =>
      if character == '_' && placeholders.hasNext then result.append(placeholders.next())
      else result.append(character)
    }.result()

