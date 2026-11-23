package it.evadid.facades.internal

/** VS Code's indentation on Enter and on a closing brace, decided from the text around the cursor only,
  * so it also holds in method snippets and unfinished code that a syntax tree cannot parse.
  */
private[evadid] object CodeIndentation {
  private val closers = Map('{' -> '}', '(' -> ')', '[' -> ']')
  private val pythonDedent = "\\s*(return|pass|break|continue|raise)\\b.*".r

  /** Replace `deleteBefore` characters before the cursor and the selection by `insert`; the cursor ends `cursor` characters into it. */
  final case class Edit(deleteBefore: Int, insert: String, cursor: Int)

  private def leading(line: String): String = line.takeWhile(c => c == ' ' || c == '\t')

  private def unclosedBrace(text: String): Boolean = text.count(_ == '{') > text.count(_ == '}')

  def enter(before: String, after: String, unit: String, python: Boolean): Edit = {
    val lineBefore = before.substring(before.lastIndexOf('\n') + 1)
    val lineAfter = after.takeWhile(_ != '\n')
    val base = leading(lineBefore)
    val opener = lineBefore.reverse.dropWhile(_.isWhitespace).headOption.filter(closers.contains)
    val indent =
      if opener.nonEmpty || (python && lineBefore.trim.endsWith(":")) then base + unit
      else if python && pythonDedent.matches(lineBefore) && base.endsWith(unit) then base.dropRight(unit.length)
      else base
    // Blank lines keep no trailing indentation.
    val deleteBefore = if lineBefore.isBlank then lineBefore.length else 0
    val closesHere = opener.exists(open => lineAfter.trim.headOption.contains(closers(open)))
    val insert =
      if closesHere then s"\n$indent\n$base"
      // An opened block gets its closing brace right away, as long as nothing closes it yet.
      else if opener.contains('{') && lineAfter.isBlank && unclosedBrace(before + after) then s"\n$indent\n$base}"
      else s"\n$indent"
    Edit(deleteBefore, insert, 1 + indent.length)
  }

  /** Indentation for a closing brace typed on a blank line: that of the line opening its block. */
  def closingBrace(before: String, unit: String): Option[String] = {
    val lineStart = before.lastIndexOf('\n') + 1
    Option.when(before.substring(lineStart).isBlank) {
      val opening = (lineStart - 1 to 0 by -1).foldLeft((0, Option.empty[Int])) {
        case ((depth, found @ Some(_)), _) => (depth, found)
        case ((depth, None), index) => before(index) match {
          case '}' => (depth + 1, None)
          case '{' if depth == 0 => (depth, Some(index))
          case '{' => (depth - 1, None)
          case _ => (depth, None)
        }
      }._2
      opening.fold(leading(before.substring(lineStart)).dropRight(unit.length))(index =>
        leading(before.substring(before.lastIndexOf('\n', index) + 1)))
    }
  }
}
