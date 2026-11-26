package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.facades.Snap
import it.evadid.facades.Snap.*
import it.evadid.homepage.webElements.editor.code.SnapEditor.{CanvasVisibility, SnapCodeEditorConfig}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw
import org.scalajs.dom
import org.scalajs.dom.CanvasRenderingContext2D
import org.scalajs.dom.html.Canvas
import scala.scalajs.js

import SnapProjectRuntime.*

/** Renders a project image in a temporary native world and always releases it. */
private[SnapEditor] object SnapPreviewRenderer:
  def render(state: ProgrammingStateRaw, canvas: Canvas, config: SnapCodeEditorConfig,
      language: AppLanguage.HumanLanguage): Unit =
    Snap.useLanguage(language)

    preserveKeyboardState {
      val sourceCanvas = dom.document.createElement("canvas").asInstanceOf[Canvas]
      sourceCanvas.width = config.visuals.CanvasWidth
      sourceCanvas.height = config.visuals.CanvasHeight

      val world = Snap.world(sourceCanvas).createLoadedInstance()
      try {
        // A preview is an image of the scripts themselves, not another configured
        // IDE. Pane-hiding and palette settings can otherwise collapse the source
        // ScriptsMorph before scriptsPicture() takes its snapshot.
        val ide = createPreviewEditor(world, state)
        try {
          layoutEditor(world, ide, sourceCanvas)
          runStartupCycles(world)

          // fullImage() includes the ScriptsMorph workspace at its absolute Morphic
          // position and can consequently contain only its background. Snap's own
          // export path uses scriptsPicture(), which crops and composites its visible
          // script children instead.
          val scriptsImage = ide.currentSprite.scripts.scriptsPicture().getOrElse(
            dom.document.createElement("canvas").asInstanceOf[Canvas]
          )
          canvas.width = math.max(1, CanvasSize.roundedWidth(scriptsImage))
          canvas.height = math.max(1, CanvasSize.roundedHeight(scriptsImage))
          canvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D].drawImage(scriptsImage, 0, 0)
          CanvasVisibility.warnIfUnexpectedlyEmpty("SnapPreviewRenderer", state.code, canvas)
        } finally ide.destroy()
      } finally world.destroy()
    }

  private def createPreviewEditor(world: WorldMorph, state: ProgrammingStateRaw): IDEMorph =
    val ide = Snap.editor(js.Dynamic.literal(
      noUserSettings = true,
      noAutoFill = true,
      noCloud = true,
      noExitWarning = true,
      preserveTitle = true
    )).createLoadedInstance()
    ide.openIn(world)
    restorePrimitiveBlockDictionary()
    ide.rawOpenProjectString(canonicalXml(state))
    ensureMissingGlobalVariables(ide, canonicalXml(state), refreshPalette = false)
    ide

  /** Replace this editor instance's primitive provider, rather than mutating
    * SpriteMorph.prototype.blockTemplates globally. Multiple editors can therefore use different
    * exercise libraries on the same page.
    */
  private[SnapEditor] def preserveKeyboardState[A](render: => A): A = {
    val restore = Option(dom.document.getElementById("morphic_keyboard")).map { element =>
      val keyboard = element.asInstanceOf[Snap.KeyboardInput]
      val value = keyboard.value
      val start = keyboard.selectionStart
      val end = keyboard.selectionEnd
      val direction = keyboard.selectionDirection
      val style = keyboard.style.cssText
      () => {
        keyboard.value = value
        keyboard.setSelectionRange(start, end, direction)
        keyboard.style.cssText = style
      }
    }
    try render
    finally restore.foreach(_())
  }
