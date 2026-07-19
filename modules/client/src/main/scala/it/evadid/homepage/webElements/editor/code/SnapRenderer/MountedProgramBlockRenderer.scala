package it.evadid.homepage.webElements.editor.code.SnapRenderer

import com.raquo.airstream.ownership.Owner
import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.TurtleFileSubmission
import it.evadid.vm.BeProgram
import org.scalajs.dom
import org.scalajs.dom.CanvasRenderingContext2D
import org.scalajs.dom.html.Canvas

import scala.scalajs.js

/** A retained Snap/Morphic session for the interactive editor and exact previews. */
final class MountedProgramBlockRenderer() extends BeProgramSnapRenderer:

  private var editorWorld: Option[WorldMorph] = None
  private var editor: Option[IDEMorph] = None
  private var frameHandle = 0
  private var destroyed = false

  override def mount(owner: Owner): Unit =
    destroyed = false

  override def renderEditorInto(initProgram: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit =
    stopEditorSession()
    destroyed = false
    sizeEditorCanvas(canvas, config)

    val world = new WorldMorph(canvas, false)
    val ide = createEditor(world, initProgram)
    layoutEditor(world, ide, canvas)

    editorWorld = Some(world)
    editor = Some(ide)
    tickEditor()
    CanvasVisibility.warnIfUnexpectedlyEmpty(this, initProgram, canvas)

  override def renderPreviewInto(program: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit =
    val sourceCanvas = dom.document.createElement("canvas").asInstanceOf[Canvas]
    sourceCanvas.width = config.CanvasWidth
    sourceCanvas.height = config.CanvasHeight

    val world = new WorldMorph(sourceCanvas, false)
    val ide = createEditor(world, program)
    layoutEditor(world, ide, sourceCanvas)
    runStartupCycles(world)

    val scriptsImage = ide.currentSprite.scripts.fullImage()
    canvas.width = math.max(1, scriptsImage.width)
    canvas.height = math.max(1, scriptsImage.height)
    canvas.style.width = s"${canvas.width}px"
    canvas.style.height = s"${canvas.height}px"
    canvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D].drawImage(scriptsImage, 0, 0)
    CanvasVisibility.warnIfUnexpectedlyEmpty(this, program, canvas)

    ide.destroy()
    world.destroy()

  private def createEditor(world: WorldMorph, program: BeProgram): IDEMorph =
    val ide = new IDEMorph(js.Dynamic.literal(
      noAutoFill = true,
      noCloud = true,
      noExitWarning = true
    ))
    ide.openIn(world)
    ide.rawOpenProjectString(TurtleFileSubmission.serializeFromBeExpression(program.fullProgram))
    ide

  private def layoutEditor(world: WorldMorph, ide: IDEMorph, canvas: Canvas): Unit =
    world.setExtent(new SnapPoint(canvas.width.toDouble, canvas.height.toDouble))
    ide.setExtent(world.extent())
    ide.fixLayout()
    ide.fullChanged()
    world.changed()
    runStartupCycles(world)

  private def runStartupCycles(world: WorldMorph): Unit =
    world.doOneCycle()
    world.doOneCycle()
    world.doOneCycle()

  private def sizeEditorCanvas(canvas: Canvas, config: SnapCodeEditorConfig): Unit =
    val bounds = canvas.getBoundingClientRect()
    val width = if bounds.width > 0 then bounds.width.round.toInt else config.CanvasWidth
    val height = if bounds.height > 0 then bounds.height.round.toInt else config.CanvasHeight
    canvas.width = math.max(1, width)
    canvas.height = math.max(1, height)
    canvas.style.width = s"${canvas.width}px"
    canvas.style.height = s"${canvas.height}px"
    canvas.style.position = "relative"
    canvas.style.display = "block"

  private def tickEditor(): Unit =
    if !destroyed then
      editorWorld.foreach(_.doOneCycle())
      frameHandle = dom.window.requestAnimationFrame(_ => tickEditor())

  private def stopEditorSession(): Unit =
    if frameHandle != 0 then dom.window.cancelAnimationFrame(frameHandle)
    frameHandle = 0
    editor.foreach(_.destroy())
    editorWorld.foreach(_.destroy())
    editor = None
    editorWorld = None

  override def destroy(): Unit =
    destroyed = true
    stopEditorSession()
