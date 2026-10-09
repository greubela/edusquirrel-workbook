package it.evadid.homepage.webElements.sqlEditor

class SqlBlocksSpec extends munit.FunSuite {
  test("top-level clauses form blocks and keep every byte") {
    val sql = "SELECT name, count(*) FROM people WHERE active = 1 GROUP BY name HAVING count(*) > 2 ORDER BY name LIMIT 10;"
    val blocks = SqlBlocks.split(sql)
    assertEquals(blocks.map(SqlBlocks.label), List("SELECT", "FROM", "WHERE", "GROUP BY", "HAVING", "ORDER BY", "LIMIT"))
    assertEquals(SqlBlocks.join(blocks), sql)
  }
  test("quoted keywords, comments and nested queries never split clauses") {
    val sql = "SELECT 'FROM it''s WHERE', `ORDER BY`, (SELECT x FROM nested WHERE id = 1) /* JOIN t */ FROM people -- WHERE ignored\nWHERE name = 'GROUP BY';"
    val blocks = SqlBlocks.split(sql)
    assertEquals(blocks.size, 3)
    assertEquals(SqlBlocks.join(blocks), sql)
  }
  test("arbitrary SQL, incomplete drafts and multiple statements remain lossless") {
    List("", "CREATE TABLE t (id INT);", "SELECT 'unfinished FROM x", "# WHERE\nSELECT 1; SELECT 2;", "SELECT /* FROM unfinished", "  SELECT \"a\\\"FROM\" FROM t;").foreach { sql =>
      assertEquals(SqlBlocks.join(SqlBlocks.split(sql)), sql)
    }
  }
  test("compound join and delete keywords stay together") {
    assertEquals(SqlBlocks.split("SELECT * FROM a LEFT OUTER JOIN b ON a.id=b.id").map(SqlBlocks.label), List("SELECT", "FROM", "LEFT OUTER JOIN"))
    assertEquals(SqlBlocks.split("DELETE FROM t WHERE id=1").map(SqlBlocks.label), List("DELETE FROM", "WHERE"))
  }
  test("adding a clause places it before the statement terminator and trailing comments") {
    assertEquals(SqlBlocks.appendClause("SELECT * FROM students; -- keep comment", "LIMIT 10"),
      "SELECT * FROM students\nLIMIT 10; -- keep comment")
    assertEquals(SqlBlocks.appendClause("SELECT ';' FROM t /* ; */", "LIMIT 1"),
      "SELECT ';' FROM t /* ; */\nLIMIT 1\n")
    assertEquals(SqlBlocks.appendClause("", "SELECT 1"), "SELECT 1\n")
  }
  test("comparison blocks reuse Snap palette selectors with SQL spelling") {
    assertEquals(SqlBlocks.comparisonOperators, List("<", ">", "=", "<=", ">=", "<>"))
  }
  test("leading whitespace stays attached to the first clause") {
    val source = "  SELECT * FROM students;"
    assertEquals(SqlBlocks.split(source).map(SqlBlocks.label), List("SELECT", "FROM"))
    assertEquals(SqlBlocks.join(SqlBlocks.split(source)), source)
  }
  test("blocks can move both directions and boundary moves are harmless") {
    assertEquals(SqlBlocks.move(List("a", "b", "c"), 0, 2), List("b", "c", "a"))
    assertEquals(SqlBlocks.move(List("a", "b", "c"), 2, 0), List("c", "a", "b"))
    assertEquals(SqlBlocks.move(List("a"), 0, -1), List("a"))
  }
}
