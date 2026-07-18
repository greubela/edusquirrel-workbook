import it.evadid.vm.parsing.python.clean.PythonAstParserSimple
import it.evadid.vm.parsing.python.clean.PyAST.*

object TestParser {
  def main(args: Array[String]): Unit = {
    val testCases = List(
      "100 + 20",
      "1 + 2",
      "42",
      "x = 100 + 20"
    )
    
    testCases.foreach { code =>
      println(s"Testing: $code")
      val result = PythonAstParserSimple.parse(code)
      result match {
        case Right(program) =>
          println(s"  Success! Statements: ${program.statements.size}")
          program.statements.foreach { stmtWithLine =>
            println(s"    Line ${stmtWithLine.lineNumber}: ${stmtWithLine.statement}")
          }
        case Left(error) =>
          println(s"  Error: ${error.getMessage}")
      }
      println()
    }
  }
}
