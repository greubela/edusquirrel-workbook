package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.facades.Snap.*
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.{JavaTurtleSnapPrimitives, SnapExtraPrimitives}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw
import org.scalajs.dom.html.Canvas
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** Native project loading and serialization shared by live sessions and previews. */
private[SnapEditor] object SnapProjectRuntime:
  def canonicalXml(state: ProgrammingStateRaw): String =
    state.removeBloatFromXml.code

  def repairCustomBlockParameterBindings(ide: IDEMorph): Unit =
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

  def customBlockDefinitions(ide: IDEMorph): List[js.Dynamic] = {
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

  def spriteMorphPrototype: js.Dynamic =
    js.Dynamic.global
      .selectDynamic("SpriteMorph")
      .selectDynamic("prototype")

  /** Snap's Scene.toXML assigns `SpriteMorph.prototype.blocks` to an array of
    * bootstrapped custom primitives and does not put the dictionary back.
    * `populateCustomBlocks` runs before `initBlocks` on the next rawOpen, so
    * definition bodies load as Undefined! unless we restore the dictionary first.
    */
  def restorePrimitiveBlockDictionary(): Unit =
    try
      spriteMorphPrototype.applyDynamic("initBlocks")()
      JavaTurtleSnapPrimitives.install()
      SnapExtraPrimitives.install()
    catch case _: Throwable => ()

  private val SceneGlobalVariables = """(?s)</stage><variables>(.*?)</variables>""".r
  private val VariableNameAttribute = """<variable\b[^>]*\bname="([^"]*)"""".r

  /** Scene `<variables>` after `</stage>` are Snap globals. Empty sprite `<variables>` are ignored. */
  def sceneGlobalVariableNames(xml: String): List[String] =
    SceneGlobalVariables.findFirstMatchIn(xml).toList.flatMap { matched =>
      VariableNameAttribute
        .findAllMatchIn(matched.group(1))
        .map(found => unescapeXmlAttribute(found.group(1)))
        .filter(_.nonEmpty)
        .toList
    }

  def unescapeXmlAttribute(value: String): String =
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
  def ensureMissingGlobalVariables(ide: IDEMorph, xml: String, refreshPalette: Boolean): Unit =
    val names = sceneGlobalVariableNames(xml)
    if names.isEmpty then return
    val sprite = ide.currentSprite
    if sprite == null then return
    val spriteDyn = sprite.asInstanceOf[js.Dynamic]
    val globals = spriteDyn.applyDynamic("globalVariables")()
    if js.isUndefined(globals) || globals == null then return
    val vars = globals.asInstanceOf[js.Dynamic].selectDynamic("vars")
    val added = names.foldLeft(false) { (previouslyAdded, name) =>
      val existing =
        if js.isUndefined(vars) || vars == null then js.undefined
        else vars.asInstanceOf[js.Dynamic].selectDynamic(name)
      val missing = js.isUndefined(existing) || existing == null
      if missing then
        spriteDyn.applyDynamic("addVariable")(name, true)
      previouslyAdded || missing
    }
    if added && refreshPalette then
      ide.asInstanceOf[js.Dynamic].applyDynamic("flushBlocksCache")("variables")
      ide.refreshPalette(true)

  def snapshotProjectXml(ide: IDEMorph): String =
    try ide.getProjectXML()
    finally restorePrimitiveBlockDictionary()

  /** If configured selectors are missing from the live primitives table (e.g. after
    * a scene replaced SpriteMorph.prototype.blocks), restore the full table.
    */
  def layoutEditor(world: WorldMorph, ide: IDEMorph, canvas: Canvas): Unit =
    world.setExtent(new SnapPoint(CanvasSize.width(canvas), CanvasSize.height(canvas)))
    ide.setExtent(world.extent())
    ide.fixLayout()
    // Canvas bitmap clears on width/height assignment; damage the full world so
    // the next cycles repaint everything instead of leaving a blank surface.
    ide.fullChanged()
    world.fullChanged()
    runStartupCycles(world)

  def runStartupCycles(world: WorldMorph): Unit =
    world.doOneCycle()
    world.doOneCycle()
    world.doOneCycle()

  /** @return true when the canvas bitmap size changed (which clears pixels). */
