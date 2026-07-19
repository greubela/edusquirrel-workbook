package it.evadid.homepage.webElements.editor.code.SnapRenderer

import com.raquo.airstream.ownership.Owner
import it.evadid.homepage.webElements.editor.code.SnapRenderer.SnapCodeEditor.SnapCodeEditorImpl
import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.TurtleFileSubmission
import it.evadid.vm.BeProgram
import org.scalajs.dom
import org.scalajs.dom.CanvasRenderingContext2D
import org.scalajs.dom.html.Canvas

import scala.scalajs.js

/** A retained Snap/Morphic session for the interactive editor and exact previews. */
final class SnapCodeEditorImplDelegateToOriginal() extends SnapCodeEditorImpl:

  private var editorWorld: Option[WorldMorph] = None
  private var editor: Option[IDEMorph] = None
  private var frameHandle = 0
  private var cyclesRunning = false
  private var projectXmlChangedCallback: String => Unit = _ => ()
  private var observedProjectVersion = 0.0
  private var lastProjectXml: Option[String] = None

  override def mount(owner: Owner): Unit =
    ()

  override def renderEditorInto(initProgram: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit =
    stopEditorSession()
    sizeEditorCanvas(canvas, config)

    val world = new WorldMorph(canvas, false)
    val ide = createEditor(world, initProgram)
    layoutEditor(world, ide, canvas)
    initializeProjectChangeTracking(ide)

    editorWorld = Some(world)
    editor = Some(ide)
    startWorldCycles()
    CanvasVisibility.warnIfUnexpectedlyEmpty(this, initProgram, canvas)

  override def renderPreviewInto(program: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit =
    val sourceCanvas = dom.document.createElement("canvas").asInstanceOf[Canvas]
    sourceCanvas.width = config.CanvasWidth
    sourceCanvas.height = config.CanvasHeight

    val world = new WorldMorph(sourceCanvas, false)
    val ide = createEditor(world, program)
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

  private def createEditor(world: WorldMorph, program: BeProgram): IDEMorph =
    val ide = new IDEMorph(js.Dynamic.literal(
      noAutoFill = true,
      noCloud = true,
      noExitWarning = true,
      preserveTitle = true
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
    // Keep the CSS and bitmap coordinate systems identical. Reading the canvas'
    // flex-scaled bounding box here produced independent X/Y scale factors in
    // the fullscreen dialog and made Morphic controls visibly distorted.
    canvas.width = math.max(1, config.CanvasWidth)
    canvas.height = math.max(1, config.CanvasHeight)
    canvas.style.width = s"${canvas.width}px"
    canvas.style.height = s"${canvas.height}px"
    canvas.style.position = "relative"
    canvas.style.display = "block"

  override def startWorldCycles(): Unit =
    if !cyclesRunning && editorWorld.nonEmpty then
      cyclesRunning = true
      tickEditor()

  override def pauseWorldCycles(): Unit =
    cyclesRunning = false
    if frameHandle != 0 then dom.window.cancelAnimationFrame(frameHandle)
    frameHandle = 0

  override def onProjectXmlChanged(callback: String => Unit): Unit =
    projectXmlChangedCallback = callback

  private def tickEditor(): Unit =
    if cyclesRunning then
      editorWorld.foreach(_.doOneCycle())
      frameHandle = dom.window.requestAnimationFrame(_ => tickEditor())
      editor.foreach(notifyIfProjectXmlChanged)

  private def initializeProjectChangeTracking(ide: IDEMorph): Unit =
    observedProjectVersion = ide.version
    lastProjectXml = Some(ide.getProjectXML())

  /**
   * Snap updates IDE_Morph.version from recordUnsavedChanges(), its central
   * user-edit notification path. Comparing that number each world cycle is
   * constant-time; the comparatively expensive XML serialization only happens
   * after Snap reports an edit. Comparing the result also filters notifications
   * such as selection changes that do not alter the persisted project.
   */
  private def notifyIfProjectXmlChanged(ide: IDEMorph): Unit =
    if ide.version != observedProjectVersion then
      observedProjectVersion = ide.version
      val xml = ide.getProjectXML()
      if !lastProjectXml.contains(xml) then
        lastProjectXml = Some(xml)
        projectXmlChangedCallback(xml)

  private def stopEditorSession(): Unit =
    pauseWorldCycles()
    editor.foreach(_.destroy())
    editorWorld.foreach(_.destroy())
    editor = None
    editorWorld = None
    observedProjectVersion = 0.0
    lastProjectXml = None

  override def destroy(): Unit =
    stopEditorSession()
