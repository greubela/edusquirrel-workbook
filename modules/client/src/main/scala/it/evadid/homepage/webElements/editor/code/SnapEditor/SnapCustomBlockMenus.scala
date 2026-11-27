package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.facades.Snap.*
import scala.scalajs.js

/** Keeps custom blocks editable although `noOwnBlocks` hides the palette's Make-a-Block button. The patch lives on
  * Snap's prototypes, so it must be restored when the editor is released. `mountedEditor` is the last resort for
  * finding a block's IDE.
  */
private[SnapEditor] final class SnapCustomBlockMenus(mountedEditor: () => Option[IDEMorph]):
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
  def wrap(): Unit =
    customBlockMorphs.foreach(wrapCustomBlockUserMenu)

  def restore(): Unit =
    customBlockMorphs.flatMap(customBlockPrototype).foreach { proto =>
      val original = proto.selectDynamic(OriginalUserMenuField)
      if !SnapJs.isAbsent(original) then
        proto.updateDynamic("userMenu")(original)
        js.Dynamic.global.Reflect.applyDynamic("deleteProperty")(proto, OriginalUserMenuField)
    }

  private def customBlockPrototype(ctor: js.Dynamic): Option[js.Dynamic] =
    if SnapJs.isAbsent(ctor) then None
    else
      val proto = ctor.selectDynamic("prototype")
      if SnapJs.isAbsent(proto) then None else Some(proto)

  private def wrapCustomBlockUserMenu(ctor: js.Dynamic): Unit =
    customBlockPrototype(ctor).foreach(wrapUserMenuOnPrototype)

  private def wrapUserMenuOnPrototype(proto: js.Dynamic): Unit = {
    val stored = proto.selectDynamic(OriginalUserMenuField)
    val original =
      if !SnapJs.isAbsent(stored) then stored
      else
        val orig = proto.selectDynamic("userMenu")
        if SnapJs.isAbsent(orig) then null.asInstanceOf[js.Dynamic]
        else
          proto.updateDynamic(OriginalUserMenuField)(orig)
          orig
    if SnapJs.isAbsent(original) then return
    val wrapped: js.ThisFunction0[js.Dynamic, js.Any] = (self: js.Dynamic) => {
      val config = ideConfigOf(self)
      val hadNoOwnBlocks =
        !SnapJs.isAbsent(config) && {
          val flag = config.selectDynamic("noOwnBlocks")
          !SnapJs.isAbsent(flag) && flag.asInstanceOf[Boolean]
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


    def attempt(resolve: () => js.Dynamic): Option[js.Dynamic] =
      try SnapJs.present(resolve())
      catch case _: Throwable => None

    val viaScriptTarget = attempt(() => block.applyDynamic("scriptTarget")(true))
      .flatMap(target => attempt(() => target.applyDynamic("parentThatIsA")(ideMorph)))
    val viaParent = attempt(() => block.applyDynamic("parentThatIsA")(ideMorph))
    val mounted = mountedEditor().map(_.asInstanceOf[js.Dynamic])

    viaScriptTarget
      .orElse(viaParent)
      .orElse(mounted)
      .flatMap(ide => SnapJs.present(ide.selectDynamic("config")))
      .getOrElse(null.asInstanceOf[js.Dynamic])
  }
