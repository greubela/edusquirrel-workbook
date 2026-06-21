package it.evadid.workbook.vm.parsing.python.normalization

import it.evadid.workbook.vm.parsing.python.PythonInlineCommentHelper

object PythonCommentScanner {

  /** Delegates to the shared parser for splitting code and trailing inline comments. */
  def splitCodeAndComment(line: String): (String, Option[String]) =
    PythonInlineCommentHelper.splitCodeAndComment(line)
}
