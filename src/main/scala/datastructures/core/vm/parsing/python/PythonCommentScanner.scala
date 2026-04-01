package datastructures.core.vm.parsing.python

object PythonCommentScanner {

  def splitInlineComment(line: String): (String, Option[String]) = {
    var index = 0
    var commentIndex = -1
    var stringDelimiter: Option[String] = None
    val length = line.length
    while (index < length && commentIndex == -1) {
      stringDelimiter match {
        case Some(delimiter) if delimiter.length == 1 =>
          val current = line.charAt(index)
          if (current == '\\') {
            index = math.min(index + 2, length)
          } else if (current == delimiter.head) {
            stringDelimiter = None
            index += 1
          } else {
            index += 1
          }
        case Some(delimiter) =>
          if (line.startsWith(delimiter, index)) {
            stringDelimiter = None
            index += delimiter.length
          } else {
            index += 1
          }
        case None =>
          if (line.startsWith("\"\"\"", index)) {
            stringDelimiter = Some("\"\"\"")
            index += 3
          } else if (line.startsWith("'''", index)) {
            stringDelimiter = Some("'''")
            index += 3
          } else {
            line.charAt(index) match {
              case '\\' => index = math.min(index + 2, length)
              case '"' =>
                stringDelimiter = Some("\"")
                index += 1
              case '\'' =>
                stringDelimiter = Some("'")
                index += 1
              case '#' =>
                commentIndex = index
              case _ =>
                index += 1
            }
          }
      }
    }
    if (commentIndex >= 0) {
      val codePart = line.substring(0, commentIndex)
      val commentText = line.substring(commentIndex + 1).trim
      (codePart, Some(commentText))
    } else {
      (line, None)
    }
  }
}
