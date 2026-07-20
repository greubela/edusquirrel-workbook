package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditorConfig.simpleFunc
import it.evadid.vm.BeProgram
import it.evadid.vm.code.BeExpression
import it.evadid.vm.types.BeDataType

object SnapExpressionBridge {


  val testBlocks: List[LibraryTab] = List(
    LibraryTab("id-1", "One block", List(
      LibraryBlock("forward", "turtle.forward(_)", simpleFunc("forward1", "par1"))
    )),
    LibraryTab("id-2", "Three blocks", List(
      LibraryBlock("turn", "dummy turn _", simpleFunc("forward2", "par2")),
      LibraryBlock("gotoXY", "dummy position _ _", simpleFunc("forward3", "par3")),
      LibraryBlock("clear", "dummy clear", simpleFunc("forward4", "par4"))
    ))
  )


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

  case class LanguageMapSnapIdentifier(stableSnapId: String) {
    def toLanguageMapId: LanguageMapContentId = ???
  }

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


  private def simpleFunc(name: String, par: String): BeExpression = {
    BeProgram.createSimpleFunc(name, List(par), List(BeDataType.Numeric), List("1000"), None).fullProgram
  }
  
}
