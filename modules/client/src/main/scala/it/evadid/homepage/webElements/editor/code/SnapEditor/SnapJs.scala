package it.evadid.homepage.webElements.editor.code.SnapEditor

import scala.scalajs.js

/** Snap's JavaScript objects mark missing members with either `undefined` or `null`. */
private[SnapEditor] object SnapJs {
  def isAbsent(value: js.Any): Boolean = js.isUndefined(value) || value == null

  def present(value: js.Dynamic): Option[js.Dynamic] = Option.unless(isAbsent(value))(value)
}
