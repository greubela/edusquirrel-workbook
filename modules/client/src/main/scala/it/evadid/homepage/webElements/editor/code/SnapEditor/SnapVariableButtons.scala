package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.facades.Snap
import it.evadid.facades.Snap.*
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** Variable controls of exercise palettes: globals only, since this editor has no sprite corral. */
private[SnapEditor] object SnapVariableButtons:
  def paletteItems(ide: IDEMorph): List[js.Any] = {
    val sprite = ide.currentSprite.asInstanceOf[js.Dynamic]
    val deletable = sprite.applyDynamic("deletableVariableNames")()
    val deletableCount =
      if SnapJs.isAbsent(deletable) then 0
      else deletable.asInstanceOf[js.Array[js.Any]].length

    val namesDyn = sprite.applyDynamic("allGlobalVariableNames")(true)
    val names =
      if SnapJs.isAbsent(namesDyn) then js.Array[String]()
      else namesDyn.asInstanceOf[js.Array[String]]
    val blocks = names.toList.flatMap { name =>
      val blockDyn = sprite.applyDynamic("variableBlock")(name)
      Option.when(!SnapJs.isAbsent(blockDyn)) {
        val block = blockDyn.asInstanceOf[BlockMorph]
        block.isDraggable = false
        block.isTemplate = true
        block: js.Any
      }
    }
    // No sprite corral in this editor — always create globals (skip Snap's local/global dialog).
    (makeGlobalVariableButton(sprite) :: Option.when(deletableCount > 0)(makeDeleteVariableButton(sprite)).toList) ++ blocks
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
        if !SnapJs.isAbsent(ide) then
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
        if !SnapJs.isAbsent(names) then
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
  def flushWithExerciseTabs(ide: IDEMorph, tabNames: Seq[String]): Unit =
    if tabNames.isEmpty then return
    val dyn = ide.asInstanceOf[js.Dynamic]
    val stored = dyn.selectDynamic("__eduOriginalFlushBlocksCache")
    val original =
      if !SnapJs.isAbsent(stored) then stored
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
