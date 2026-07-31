package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.naming.CodeRepresentationConfig
import it.evadid.vm.types.BeDataType

/** A primitive in the Snap palette.
 *
 * `id` is Snap's stable selector (for example `gotoXY`). It is also the value
 * persisted in the `s` attribute of the resulting `<block>` element. The
 * description may replace the visible native label. Underscores are expanded
 * to the corresponding native input type, so `go to x: _ y: _` keeps the two
 * numeric input morphs belonging to `gotoXY`.
 */
case class LibraryBlock(id: String, snap_description_line: String, associatedExpression: BeExpression)

/** One named, ordered and allow-listed palette tab. An empty tab is valid.
 */
case class LibraryTab(id: String, name: String, selectableElements: List[LibraryBlock])

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
                            headline: Boolean = true, // file saving etc
                            palette: Boolean = true, // library
                            libraryCategories: Boolean = true, //
                            stage: Boolean = true, // executable area right
                            spriteControls: Boolean = true // scripts / costumes / sound headline
                          )

case class SnapEditorVisuals(
                              ColorWorkspace: String = "#f6f8fa",
                              ColorEmpty: String = "#8c959f",
                              CanvasWidth: Int = 900,
                              CanvasHeight: Int = 520,
                              Padding: Double = 24.0,
                              BlockGap: Double = 8.0,
                              Indent: Double = 28.0
                            )

case class SnapCodeEditorConfig(
                                 parts: SnapEditorParts = SnapEditorParts(),
                                 libraryTabs: List[LibraryTab] = Nil,
                                 codeRepresentation: CodeRepresentationConfig = CodeRepresentationConfig(Python, English, skipUnparsable = false),
                                 visuals: SnapEditorVisuals = SnapEditorVisuals()
                               )

object SnapCodeEditorConfig:
  /** Minimal configuration used by integration tests and manual smoke tests. */
  val Testing: SnapCodeEditorConfig = SnapCodeEditorConfig(
    parts = SnapEditorParts(
      headline = false,
      palette = true,
      libraryCategories = true,
      stage = false,
      spriteControls = false
    ),
    libraryTabs = List(
      LibraryTab("id-1","One block", List(
        LibraryBlock("forward", "turtle.forward(_)", simpleFunc("forward1", "par1"))
      )),
      LibraryTab("id-2","Three blocks", List(
        LibraryBlock("turn", "dummy turn _", simpleFunc("forward2", "par2")),
        LibraryBlock("gotoXY", "dummy position _ _", simpleFunc("forward3", "par3")),
        LibraryBlock("clear", "dummy clear", simpleFunc("forward4", "par4"))
      ))
    )
  )

  private def simpleFunc(name: String, par: String): BeExpression = {
    BeProgram.createSimpleFunc(name, List(par), List(BeDataType.Numeric), List("1000"), None).fullProgram
  }
