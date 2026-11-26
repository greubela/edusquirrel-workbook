package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.facades.Snap.{IDEMorph, WorldMorph}
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditorConfig
import org.scalajs.dom.html.Canvas

/** All live Morphic resources have the same session lifetime. */
private[SnapEditor] final case class SnapSessionResources(
    editorWorld: Option[WorldMorph] = None,
    editor: Option[IDEMorph] = None,
    mountedCanvas: Option[Canvas] = None,
    mountedConfig: Option[SnapCodeEditorConfig] = None,
    frameHandle: Int = 0,
    cyclesRunning: Boolean = false
)
