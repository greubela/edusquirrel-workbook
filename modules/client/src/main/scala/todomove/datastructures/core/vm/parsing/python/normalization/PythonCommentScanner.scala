package todomove.datastructures.core.vm.parsing.python.normalization

import todomove.datastructures.core.vm.parsing.python.PythonInlineCommentHelper

object PythonCommentScanner {

  /** Delegates to the shared parser for splitting code and trailing inline comments. */
  def splitCodeAndComment(line: String): (String, Option[String]) =
    PythonInlineCommentHelper.splitCodeAndComment(line)
}
