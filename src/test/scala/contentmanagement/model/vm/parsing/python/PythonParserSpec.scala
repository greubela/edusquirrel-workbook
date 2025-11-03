package contentmanagement.model.vm.parsing.python

import contentmanagement.model.language.AppLanguage.{English, Python}
import contentmanagement.model.vm.code.controlStructures.BeSequence
import contentmanagement.model.vm.code.defining.BeDefineFunction
import contentmanagement.model.vm.code.errors.BeExpressionUnparsable
import interactionPlugins.blockEnvironment.programming.BeProgram
import munit.FunSuite

class PythonParserSpec extends FunSuite {

  test("round trip matches main app example") {
    val somePython =
      """x = 3
        |def greeting(name: str) -> str:
        |    return 'Hello ' + name
        |
        |def increase(nr):
        |   nr = nr + 3
        |   x = 5
        |
        |greeting('hi')
        |increase(5)
        |""".stripMargin

    //println("python before:\n" + somePython)

    val parsingResult = PythonParser.parsePythonWithDetails(somePython)
    val expression = parsingResult.codeExpression

    assert(!expression.isInstanceOf[BeExpressionUnparsable], "parsing produced an unparsable expression")

    val generated = expression.getInLanguage(Python, English)


    //println("python after:\n" + generated)
    assertEquals(normalize(generated), normalize(somePython))

    val reparsed = PythonParser.parsePythonWithDetails(generated)
    val regenerated = reparsed.codeExpression.getInLanguage(Python, English)
    assertEquals(normalize(regenerated), normalize(generated))

    assert(
      parsingResult.definedFunctions.exists(_.functionTypeInfo.funcType.isInstanceOf[BeDefineFunction.Operator]),
      "expected operator functions to be recorded"
    )
  }

  test("round trip from mini program expression") {
    val sourceExpression = BeProgram.miniProgramExpression()
    val generated = sourceExpression.getInLanguage(Python, English)

    val parsed = PythonParser.parsePythonWithDetails(generated)
    parsed.codeExpression match {
      case seq: BeSequence => assertEquals(seq.body.length, 2)
      case other => fail(s"Expected a sequence after parsing mini program, but received ${other.getClass.getSimpleName}")
    }

    val regenerated = parsed.codeExpression.getInLanguage(Python, English)
    assertEquals(normalize(regenerated), normalize(generated))
  }

  private def normalize(code: String): String = {
    val unifiedNewlines = code.replace("\r\n", "\n").replace('\r', '\n')
    val lines = unifiedNewlines.split("\n", -1).toList
    val withoutEmpty = lines.filterNot(_.trim.isEmpty)
    val withoutLeading = withoutEmpty.dropWhile(_.trim.isEmpty)
    val withoutTrailing = withoutLeading.reverse.dropWhile(_.trim.isEmpty).reverse
    val cleaned = withoutTrailing.map(_.replaceAll("\\s+$", ""))
    cleaned.mkString("\n")
  }
}
