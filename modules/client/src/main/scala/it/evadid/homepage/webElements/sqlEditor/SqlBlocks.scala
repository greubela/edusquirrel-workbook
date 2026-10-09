package it.evadid.homepage.webElements.sqlEditor

import it.evadid.workbook.elements.interactionElements.programming.SnapPaletteCatalog

/** Lossless clause blocks: strings, comments and nested queries stay inside their clause.
  * Unsupported syntax remains editable as a raw SQL block; switching tabs never rewrites SQL.
  */
object SqlBlocks {
  // Reuse the established comparison selector order, with SQL-specific spelling.
  val comparisonOperators: List[String] = SnapPaletteCatalog.OperatorSelectors.flatMap {
    case "reportVariadicLessThan" => Some("<")
    case "reportVariadicGreaterThan" => Some(">")
    case "reportVariadicEquals" => Some("=")
    case "reportVariadicLessThanOrEquals" => Some("<=")
    case "reportVariadicGreaterThanOrEquals" => Some(">=")
    case "reportVariadicNotEquals" => Some("<>")
    case _ => None
  }
  val palette: List[(String, String)] = List(
    "SELECT" -> "SELECT *", "FROM" -> "FROM table_name", "WHERE" -> "WHERE column_name = 1",
    "SELECT AVG" -> "SELECT AVG(column_name) AS average_value",
    "SELECT COUNT" -> "SELECT COUNT(column_name) AS value_count",
    "JOIN" -> "JOIN other_table ON table_name.id = other_table.id",
    "GROUP BY" -> "GROUP BY column_name", "HAVING" -> "HAVING COUNT(*) > 1",
    "ORDER BY" -> "ORDER BY column_name", "LIMIT" -> "LIMIT 100",
    "INSERT" -> "INSERT INTO table_name (column_name) VALUES (1)",
    "UPDATE" -> "UPDATE table_name SET column_name = 1", "DELETE" -> "DELETE FROM table_name",
    "SQL" -> "-- SQL command")
  private val clauses = "(?i)\\b(SELECT|FROM|WHERE|(?:LEFT|RIGHT|INNER|CROSS|FULL)(?:\\s+OUTER)?\\s+JOIN|JOIN|GROUP\\s+BY|HAVING|ORDER\\s+BY|LIMIT|UNION(?:\\s+ALL)?|INSERT\\s+INTO|UPDATE|SET|DELETE\\s+FROM|VALUES)\\b".r

  def label(source: String): String =
    clauses.findPrefixOf(source.trim).map(_.toUpperCase).getOrElse("SQL")

  def split(source: String): List[String] = {
    if source.isEmpty then Nil
    else {
      val masked = maskTopLevel(source)
      val boundaries = clauses.findAllMatchIn(masked).map(_.start).filter(index => index > 0 && source.take(index).trim.nonEmpty).toList
      val positions = (0 :: boundaries) :+ source.length
      positions.sliding(2).map(pair => source.substring(pair.head, pair(1))).toList
    }
  }
  private def maskTopLevel(source: String): String = {
    val masked = source.toCharArray
    var depth = 0
    var quote = ' '
    var lineComment = false
    var blockComment = false
    var index = 0
    while index < source.length do {
      val current = source(index)
      val next = if index + 1 < source.length then source(index + 1) else 0.toChar
      if lineComment then {
        masked(index) = ' '
        if current == '\n' then lineComment = false
      } else if blockComment then {
        masked(index) = ' '
        if current == '*' && next == '/' then {
          masked(index + 1) = ' '; index += 1; blockComment = false
        }
      } else if quote != ' ' then {
        masked(index) = ' '
        if current == '\\' && next != 0.toChar then { masked(index + 1) = ' '; index += 1 }
        else if current == quote then {
          if next == quote then { masked(index + 1) = ' '; index += 1 }
          else quote = ' '
        }
      } else if current == '\'' || current == '"' || current == '`' then {
        masked(index) = ' '; quote = current
      } else if (current == '-' && next == '-') || current == '#' then {
        masked(index) = ' '; lineComment = true
      } else if current == '/' && next == '*' then {
        masked(index) = ' '; masked(index + 1) = ' '; index += 1; blockComment = true
      } else if current == '(' then { depth += 1; masked(index) = ' ' }
      else if current == ')' then { depth = math.max(0, depth - 1); masked(index) = ' ' }
      else if depth > 0 then masked(index) = ' '
      index += 1
    }
    new String(masked)
  }
  /** New clauses go before the terminating semicolon, including when comments follow it. */
  def appendClause(source: String, clause: String): String = {
    val last = maskTopLevel(source).lastIndexWhere(character => !character.isWhitespace)
    if last >= 0 && source(last) == ';' then
      source.take(last) + "\n" + clause + ";" + source.drop(last + 1)
    else source + (if source.isEmpty || source.endsWith("\n") then "" else "\n") + clause + "\n"
  }
  def join(blocks: List[String]): String = blocks.mkString
  def move[A](blocks: List[A], from: Int, to: Int): List[A] =
    if from < 0 || to < 0 || from >= blocks.size || to >= blocks.size then blocks
    else {
      val item = blocks(from)
      val remaining = blocks.patch(from, Nil, 1)
      remaining.patch(to, List(item), 0)
    }
}
