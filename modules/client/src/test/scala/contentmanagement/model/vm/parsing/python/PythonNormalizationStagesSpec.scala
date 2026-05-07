package contentmanagement.model.vm.parsing.python

import datastructures.core.vm.parsing.python.PythonNormalizer
import datastructures.core.vm.parsing.python.normalization.PythonCommentScanner
import munit.FunSuite

class PythonNormalizationStagesSpec extends FunSuite {

  private val normalizer = new PythonNormalizer()

  test("comment scanner ignores hashes inside strings") {
    val (code, comment) = PythonCommentScanner.splitCodeAndComment("value = '# not comment' # real comment")
    assertEquals(code, "value = '# not comment' ")
    assertEquals(comment, Some("real comment"))
  }

  test("comment scanner handles escaped quotes before comment") {
    val (code, comment) = PythonCommentScanner.splitCodeAndComment("msg = \"a \\\"#\\\" b\" # trailing")
    assertEquals(code, "msg = \"a \\\"#\\\" b\" ")
    assertEquals(comment, Some("trailing"))
  }

  test("raw line extraction splits inline comments into dedicated raw lines") {
    val source = "value = 1  # keep\n"
    val lines = normalizer.extractRawLines(normalizer.normalizeLineEndings(source))
    assertEquals(lines.map(_.text), List("value = 1", "# keep"))
    assertEquals(lines.map(_.indent), List(0, 0))
  }

  test("elif chains normalize to nested else-if blocks") {
    val source =
      """if score > 10:
        |    result = "high"
        |elif score == 10:
        |    score += 1
        |elif score == 0:
        |    result = "empty"
        |else:
        |    result = "low"
        |""".stripMargin

    val normalized = normalizer.normalizePython(source)
    assert(normalized.contains("else:\n    if score == 10:"))
    assert(normalized.contains("else:\n        if score == 0:"))
    assert(!normalized.contains("elif"))
  }

  test("parse + render stages preserve synthesized comment lines") {
    val source = "x = 1 # first\ny = 2 # second"
    val raw = normalizer.extractRawLines(normalizer.normalizeLineEndings(source))
    val parsed = normalizer.parseStatements(raw)
    val rendered = normalizer.renderNormalizedOutput(parsed)
    assert(rendered.contains("# first"))
    assert(rendered.contains("# second"))
  }
}
