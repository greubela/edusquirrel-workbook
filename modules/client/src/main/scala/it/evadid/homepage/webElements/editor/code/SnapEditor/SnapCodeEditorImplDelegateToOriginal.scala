package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.state.State
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor.SnapCodeEditorImpl
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditorConfig.SnapCodeEditorConfig
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditorImplDelegateToOriginal.EditorHandleState
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapExpressionBridge.LibraryTab
import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.TurtleFileSubmission
import it.evadid.vm.BeProgram
import org.scalajs.dom
import org.scalajs.dom.CanvasRenderingContext2D
import org.scalajs.dom.html.Canvas

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

case class SnapCodeEditorImplDelegateToOriginal
(
  programState: Var[BeProgram],
  forConfigState: Var[SnapCodeEditorConfig],
  mountedCanvas: Canvas,
  enableInteraction: Boolean = true
) extends SnapCodeEditorImpl:

  private val initState: State[EditorHandleState] = State(EditorHandleState())
  private val ProjectXmlCheckIntervalMs = 500.0

  val world: WorldMorph = {
    val world = new WorldMorph(mountedCanvas, false)

    if (enableInteraction) {
      Option(mountedCanvas.parentElement).foreach(_.appendChild(world.keyboardHandler))
      
      world.keyboardHandler.tabIndex = -1
      world.keyboardHandler.style.pointerEvents = "none"
      world.keyboardHandler.style.opacity = "0"
    }
    world
  }

  private def createIde(curConfig: SnapCodeEditorConfig, curProgram: BeProgram): IDEMorph = {
    val ide = new IDEMorph(js.Dynamic.literal(
      noAutoFill = true,
      noCloud = true,
      noExitWarning = true,
      preserveTitle = true,
      hideControls = !curConfig.showElements.showHeadline,
      hideCategories = !curConfig.showElements.showPaletteCategories,
      noSprites = !curConfig.showElements.showStage,
      noSpriteEdits = !curConfig.showElements.showSpriteControl,
      noPalette = !curConfig.showElements.showPalette,
      noOwnBlocks = !curConfig.control.allowCustomBlocks,
      //eduLibraryTabs = config.libraryTabs.map(_.name).toJSArray
    ))
    ide.openIn(world)

    layoutEditor(world, ide, curConfig)
    if curConfig.libraryTabs.nonEmpty then
      installLibraries(curConfig.libraryTabs, ide)
      layoutEditor(world, ide, curConfig)

    ide
  }

  private def layoutEditor(world: WorldMorph, ide: IDEMorph, curConfig: SnapCodeEditorConfig): Unit = {
    world.setExtent(new SnapPoint(curConfig.visuals.CanvasWidth, curConfig.visuals.CanvasHeight))
    ide.setExtent(world.extent())
    ide.fixLayout()
    ide.fullChanged()
    world.changed()
    world.doOneCycle()
    world.doOneCycle()
    world.doOneCycle()
  }
  //  ide.rawOpenProjectString(TurtleFileSubmission.serializeFromBeExpression(program.fullProgram))

  /*

  override def renderPreviewInto(program: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit =
    editor.foreach(checkWhetherProgramXmlChanged)
    val sourceCanvas = dom.document.createElement("canvas").asInstanceOf[Canvas]
    sourceCanvas.width = config.visuals.CanvasWidth
    sourceCanvas.height = config.visuals.CanvasHeight

    val world = new WorldMorph(sourceCanvas, false)
    // A preview is an image of the scripts themselves, not another configured
    // IDE. Pane-hiding and palette settings can otherwise collapse the source
    // ScriptsMorph before scriptsPicture() takes its snapshot.
    val ide = createPreviewEditor(world, program)
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
    CanvasVisibility.warnIfUnexpectedlyEmpty(this, program, canvas)

    ide.destroy()
    world.destroy()


  private def createPreviewEditor(world: WorldMorph, program: BeProgram): IDEMorph =
    val ide = new IDEMorph(js.Dynamic.literal(
      noAutoFill = true,
      noCloud = true,
      noExitWarning = true,
      preserveTitle = true
    ))
    ide.openIn(world)
    ide.rawOpenProjectString(TurtleFileSubmission.serializeFromBeExpression(program.fullProgram))
    ide
*/

  /** Replace this editor instance's primitive provider, rather than mutating
   * SpriteMorph.prototype. Multiple editors can therefore use different
   * exercise libraries on the same page.
   */
  private def installLibraries(libraries: List[LibraryTab], ide: IDEMorph): Unit = {

    // todo 

  }

  override def removeAllLibraries(includeDefaultLibraries: Boolean): Unit = {
    // todo 
  }

/*
require(libraries.map(_.name).distinct.size == libraries.size, "Snap library tab names must be unique")
require(libraries.forall(_.name.nonEmpty), "Snap library tab names must not be empty")
val sprite = ide.currentSprite
val originalBlockTemplates = Some(sprite.asInstanceOf[js.Dynamic].selectDynamic("blockTemplates"))
val blockTemplates: js.Function2[String, Boolean, js.Array[BlockMorph]] =
  (category: String, _: Boolean) => libraries.find(_.name == category).toList.flatMap(_.selectableElements).map { data =>
    val block = Option(sprite.blockForSelector(data.id, true)).getOrElse(
      throw new IllegalArgumentException(s"Unknown Snap block selector '${data.id}'")
    )
    block.isDraggable = false
    block.isTemplate = true
    if data.snap_description_line.nonEmpty then
      block.setSpec(descriptionWithNativeInputs(data.snap_description_line, block.blockSpec))
    block
  }.toJSArray

sprite.asInstanceOf[js.Dynamic].updateDynamic("blockTemplates")(blockTemplates)
sprite.asInstanceOf[js.Dynamic].updateDynamic("primitivesCache")(js.Dictionary.empty[js.Any])
sprite.paletteCache = js.Dictionary.empty
ide.refreshPalette(true)
}

override def removeAllLibraries(includeDefaultLibraries: Boolean): Unit =
editor.foreach { ideMorph =>
  val sprite = ideMorph.currentSprite
  val original = originalBlockTemplates.getOrElse {
    val templates = sprite.asInstanceOf[js.Dynamic].selectDynamic("blockTemplates")
    originalBlockTemplates = Some(templates)
    templates
  }
  val templates = if includeDefaultLibraries then
    ((_: String, _: Boolean) => js.Array[BlockMorph]()).asInstanceOf[js.Function2[String, Boolean, js.Array[BlockMorph]]]
  else original
  sprite.asInstanceOf[js.Dynamic].updateDynamic("blockTemplates")(templates)
  sprite.asInstanceOf[js.Dynamic].updateDynamic("primitivesCache")(js.Dictionary.empty[js.Any])
  sprite.paletteCache = js.Dictionary.empty
  val ideConfig = ideMorph.asInstanceOf[js.Dynamic].selectDynamic("config")
  ideConfig.updateDynamic("eduLibraryTabs")(js.Array())
  ideConfig.updateDynamic("eduEmptyLibrary")(includeDefaultLibraries)
  ideMorph.asInstanceOf[js.Dynamic].updateDynamic("currentCategory")("motion")
  ideMorph.createCategories()
  ideMorph.refreshPalette(true)
}
if !includeDefaultLibraries then originalBlockTemplates = None

/** `_` is deliberately only presentation syntax. The selector's native
* placeholders remain authoritative for numeric, boolean and nested inputs.
*/
private def descriptionWithNativeInputs(description: String, nativeSpec: String): String =
val placeholders = "%[^ ]+".r.findAllIn(nativeSpec).toList.iterator
description.foldLeft(new StringBuilder) { (result, character) =>
  if character == '_' && placeholders.hasNext then result.append(placeholders.next())
  else result.append(character)
}.result()



private def runStartupCycles(world: WorldMorph): Unit =


override def startWorldCycles(): Unit =
if !cyclesRunning && editorWorld.nonEmpty then
  cyclesRunning = true
  tickEditor()

override def pauseWorldCycles(): Unit =
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
editor.foreach(_.destroy())
editorWorld.foreach(_.destroy())
editor = None
editorWorld = None
lastProjectXml = None
lastProjectXmlCheckAt = 0.0
originalBlockTemplates = None

override def destroy(): Unit =
stopEditorSession()

 */

object SnapCodeEditorImplDelegateToOriginal {

  private case class EditorHandleState
  (
    editorWorld: Option[WorldMorph] = None,
    editorIde: Option[IDEMorph] = None,
    currentFrame: Int = 0,
    lastKnownProjectXml: Option[String] = None
  )


}