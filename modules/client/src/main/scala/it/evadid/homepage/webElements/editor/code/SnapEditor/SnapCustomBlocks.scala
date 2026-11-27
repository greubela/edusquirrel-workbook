package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.facades.Snap.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import scala.scalajs.js

import SnapProjectRuntime.*

/** Custom block definitions of the open project: predefined module blocks and the palette entries of a category. */
private[SnapEditor] object SnapCustomBlocks:
  /** The predefined module block a definition installs, if any. */
  def moduleBlockOf(definition: js.Dynamic): Option[SnapModuleBlocks.ModuleBlock] =
    val spec = definition.selectDynamic("spec")
    if SnapJs.isAbsent(spec) then None else SnapModuleBlocks.bySpec(spec.toString)

  /**
   * Adds the definitions of predefined module blocks the palette offers but the project lacks (a program
   * only carries them once it calls the module), as Snap imports a blocks library. The data variable is
   * added only when missing, so the list a student changed while trying blocks stays.
   */
  def installModuleBlocks(ide: IDEMorph, blocks: Seq[SnapModuleBlocks.ModuleBlock]): Unit =
    val installed = customBlockDefinitions(ide).flatMap(moduleBlockOf).toSet
    val missing = blocks.filterNot(installed.contains)
    val dynamicIde = ide.asInstanceOf[js.Dynamic]
    val stage = dynamicIde.selectDynamic("stage")
    if missing.nonEmpty && !SnapJs.isAbsent(stage) then
      val loaded = dynamicIde.selectDynamic("serializer").applyDynamic("loadBlocks")(SnapModuleBlocks.libraryXml(missing), stage)
      loaded.selectDynamic("global").asInstanceOf[js.Array[js.Dynamic]].foreach { definition =>
        definition.updateDynamic("receiver")(stage)
        stage.selectDynamic("globalBlocks").applyDynamic("push")(definition)
      }
      val globals = dynamicIde.selectDynamic("globalVariables")
      val existing = globals.selectDynamic("vars").selectDynamic(SnapModuleBlocks.NumberListVariable)
      val data = loaded.selectDynamic("data")
      if (SnapJs.isAbsent(existing)) && !SnapJs.isAbsent(data) then
        globals.applyDynamic("merge")(data)
      ide.flushPaletteCache()

  /** The palette template of a module block, from its installed definition. */
  def moduleTemplateBlock(ide: IDEMorph, block: SnapModuleBlocks.ModuleBlock): Option[BlockMorph] =
    customBlockDefinitions(ide).find(moduleBlockOf(_).contains(block)).flatMap { definition =>
      try
        val template = definition.applyDynamic("templateInstance")()
        Option.when(!SnapJs.isAbsent(template))(template.asInstanceOf[BlockMorph])
      catch case _: Throwable => None
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
  def paletteItems(ide: IDEMorph, category: String): List[js.Any] = {
    def blocksOf(owner: js.Dynamic, field: String): List[js.Dynamic] =
      if SnapJs.isAbsent(owner) then Nil
      else
        val blocks = owner.selectDynamic(field)
        if SnapJs.isAbsent(blocks) then Nil
        else blocks.asInstanceOf[js.Array[js.Dynamic]].toList

    val stage = ide.asInstanceOf[js.Dynamic].selectDynamic("stage")
    val sprite = ide.currentSprite.asInstanceOf[js.Dynamic]
    (blocksOf(stage, "globalBlocks") ++ blocksOf(sprite, "customBlocks")).flatMap { definition =>
      val current = definition.selectDynamic("category")
      val currentName = if SnapJs.isAbsent(current) then "" else current.toString
      val helper = definition.selectDynamic("isHelper")
      val isHelper = !SnapJs.isAbsent(helper) && helper.asInstanceOf[Boolean]
      if currentName != category || isHelper then Nil
      else
        try
          val template = definition.applyDynamic("templateInstance")()
          if SnapJs.isAbsent(template) then Nil
          else List(template.asInstanceOf[js.Any])
        catch case _: Throwable => Nil
    }
  }
