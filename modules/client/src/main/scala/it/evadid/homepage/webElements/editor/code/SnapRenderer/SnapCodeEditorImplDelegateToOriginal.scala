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
  private var lastProjectXml: Option[String] = None
  private var lastProjectXmlCheckAt = 0.0

  private val ProjectXmlCheckIntervalMs = 250.0

  override def mount(owner: Owner): Unit =
    ()

  override def renderEditorInto(initProgram: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit =
    stopEditorSession()
    require(canvas.isConnected, "Snap's interactive canvas must be mounted before WorldMorph is created")
    sizeEditorCanvas(canvas, config)

    val world = new WorldMorph(canvas, false)
    require(
      world.worldCanvas eq canvas,
      "WorldMorph did not retain the mounted editor canvas used to register input listeners"
    )
    keepKeyboardHandlerInEditor(world, canvas)
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
    // WorldMorph registers mouse/touch listeners synchronously in its
    // constructor. Make this exact mounted canvas an explicit input target;
    // creating or copying a second canvas would only copy pixels, not those
    // listeners or the Morphic world behind them.
    canvas.tabIndex = 0
    canvas.style.pointerEvents = "auto"
    canvas.style.setProperty("touch-action", "none")

  private def keepKeyboardHandlerInEditor(world: WorldMorph, canvas: Canvas): Unit =
    // Morphic creates one hidden textarea on document.body and focuses it when
    // an input slot is edited. A modal <dialog> makes body siblings inert, so
    // mouse events still reach the canvas but the textarea cannot receive keys.
    // Moving the shared handler below the mounted canvas keeps it in the same
    // focus scope without changing Morphic's keyboard/IME event pipeline.
    Option(canvas.parentElement).foreach(_.appendChild(world.keyboardHandler))
    world.keyboardHandler.setAttribute("aria-hidden", "true")
    world.keyboardHandler.tabIndex = -1
    world.keyboardHandler.style.pointerEvents = "none"
    world.keyboardHandler.style.opacity = "0"

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
      val now = dom.window.performance.now()
      if now - lastProjectXmlCheckAt >= ProjectXmlCheckIntervalMs then
        lastProjectXmlCheckAt = now
        editor.foreach(notifyIfProjectXmlChanged)

  private def initializeProjectChangeTracking(ide: IDEMorph): Unit =
    lastProjectXml = Some(ide.getProjectXML())
    lastProjectXmlCheckAt = dom.window.performance.now()

  /**
   * Compare the persisted project itself rather than IDE_Morph.version. That
   * value is only updated on Snap edit paths which call recordUnsavedChanges,
   * and therefore is not a reliable content revision. Polling is throttled so
   * serialization does not happen on every animation frame.
   */
  private def notifyIfProjectXmlChanged(ide: IDEMorph): Unit =
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
    lastProjectXml = None
    lastProjectXmlCheckAt = 0.0

  override def destroy(): Unit =
    stopEditorSession()
