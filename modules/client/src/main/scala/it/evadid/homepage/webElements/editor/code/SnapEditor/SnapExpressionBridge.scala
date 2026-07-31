package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.vm.code.abstractions.BeExpression

object SnapExpressionBridge {


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





}
