package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.facades.Snap.{IDEMorph, WorldMorph}
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import org.scalajs.dom
import org.scalajs.dom.html.Canvas

/** All live Morphic resources have the same session lifetime. */
private[SnapEditor] final case class SnapSessionResources(
    editorWorld: Option[WorldMorph] = None,
    editor: Option[IDEMorph] = None,
    mountedCanvas: Option[Canvas] = None,
    mountedConfig: Option[SnapCodeEditorConfig] = None,
    resizeObserver: Option[dom.ResizeObserver] = None,
    fitRafHandle: Int = 0,
    fitDebounceHandle: Int = 0,
    frameHandle: Int = 0,
    stageMirrorRafHandle: Int = 0,
    stageMirrorIdleFrames: Int = 0,
    cyclesRunning: Boolean = false,
    projectXmlChangedCallback: String => Unit = _ => (),
    lastProjectXmlCheckAt: Double = 0.0,
    greenFlagStepMs: Double = 20.0,
    lastStageMirrorAt: Double = 0.0,
    savedFlashTime: Option[Double] = None,
    savedSingleStepping: Option[Boolean] = None
)
