package it.evadid.homepage.webElements.sqlEditor

class SqlEditorPaletteSpec extends munit.FunSuite {
  test("comparison blocks reuse Snap palette selectors with SQL spelling") {
    assertEquals(SqlEditorPalette.comparisonOperators, List("<", ">", "=", "<=", ">=", "<>"))
  }
}
