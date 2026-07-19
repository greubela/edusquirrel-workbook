package it.evadid.homepage.webElements.editor.code.SnapRenderer

import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.naming.CodeRepresentationConfig

/** A primitive in the Snap palette.
  *
  * `id` is Snap's stable selector (for example `gotoXY`). It is also the value
  * persisted in the `s` attribute of the resulting `<block>` element. The
  * description may replace the visible native label. Underscores are expanded
  * to the corresponding native input type, so `go to x: _ y: _` keeps the two
  * numeric input morphs belonging to `gotoXY`.
  */
case class SnapMorphData(id: String, snap_description_line: String)

/** One named, ordered and allow-listed palette tab. An empty tab is valid.
  */
case class LibraryTab(name: String, selectableElements: List[SnapMorphData])

/** Visibility of the independently configurable parts of the embedded IDE.
  *
  * Snap calls the palette the library. `spriteControls` includes its scripts /
  * costumes / sounds headline, the sprite corral and the stage resize handle.
  * Thus the small, programming-only editor requested by most exercises is
  * `SnapEditorParts(headline = false, stage = false, spriteControls = false)`:
  * it contains the library and scripts workspace, but no costumes tab, project
  * menu, green flag, stage, or sprite controls.
  */
case class SnapEditorParts(
    headline: Boolean = true,
    library: Boolean = true,
    libraryCategories: Boolean = true,
    stage: Boolean = true,
    spriteControls: Boolean = true
)

case class SnapCodeEditorConfig(
    parts: SnapEditorParts = SnapEditorParts(),
    libraryTabs: List[LibraryTab] = Nil,
    DisplayConfig: CodeRepresentationConfig = CodeRepresentationConfig(Python, English, skipUnparsable = false),
    ColorWorkspace: String = "#f6f8fa",
    ColorEmpty: String = "#8c959f",
    CanvasWidth: Int = 900,
    CanvasHeight: Int = 520,
    Padding: Double = 24.0,
    BlockGap: Double = 8.0,
    Indent: Double = 28.0
)

object SnapCodeEditorConfig:
  /** Minimal configuration used by integration tests and manual smoke tests. */
  val Testing: SnapCodeEditorConfig = SnapCodeEditorConfig(
    parts = SnapEditorParts(
      headline = false,
      library = true,
      libraryCategories = true,
      stage = false,
      spriteControls = false
    ),
    libraryTabs = List(
      LibraryTab("One block", List(SnapMorphData("forward", "dummy _"))),
      LibraryTab("Three blocks", List(
        SnapMorphData("turn", "dummy turn _"),
        SnapMorphData("gotoXY", "dummy position _ _"),
        SnapMorphData("clear", "dummy clear")
      ))
    )
  )
