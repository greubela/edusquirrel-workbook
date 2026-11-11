package it.evadid.homepage.webElements.sqlEditor

import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapPaletteCatalog

/** Editor templates and comparison buttons, separate from shared SQL clause handling. */
object SqlEditorPalette {
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
}
