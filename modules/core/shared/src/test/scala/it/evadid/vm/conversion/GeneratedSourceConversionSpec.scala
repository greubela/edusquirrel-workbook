package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage, BlockDisplay, ProgrammingLanguage}
import it.evadid.vm.code.errors.BeSourcePlaceholder
import it.evadid.vm.parsing.source.{SourceParsing, SourceLines}
import it.evadid.workbook.interactions.programming.state.*
import munit.FunSuite
import scala.util.Try

/** Deterministic editing sequences exercise parser recovery at every character boundary. */
class GeneratedSourceConversionSpec extends FunSuite {
  private val programs = Seq(
    Python -> "def step(n: float):\n    for i in range(2):\n        forward(n + i)\nstep(3)\n",
    Python -> "class Box:\n    def size(self):\n        return 4\ntext = \"Grüße 🐢\"\nprint(text)\n",
    Java -> "class Main { static void main(String[] args) { for (int i=0;i<2;i++) { Turtle.forward(i+3); } } }",
    Java -> "class Box { int size() { return 4; } } class Main { static void main(String[] args) { System.out.println(\"🐢\"); } }")

  programs.zipWithIndex.foreach { case ((language, program), index) =>
    test(s"$language program $index: every prefix and single-character deletion remains safe to convert") {
      val variants = (0 to program.length).map(program.take) ++ program.indices.map(i => program.take(i) + program.drop(i + 1))
      variants.distinct.foreach { text =>
        val raw = ProgrammingStateRaw(language, text)
        val structured: ProgrammingState = if language == Java then ProgrammingStateJavaStructured(text) else ProgrammingStatePythonStructured(text)
        Seq[ProgrammingState](raw, structured).foreach { state =>
          val before = ProgrammingState.serializer.serialize(state)
          val placeholders = Try(state.toBeExpression.expression.subtree.exists(_.isInstanceOf[BeSourcePlaceholder])).getOrElse(false)
          Seq[ProgrammingLanguage](Python, Java, SnapLanguage, BlockDisplay).foreach { target =>
            val result = state.convertToLanguage(target)
            assert(result.finalState.nonEmpty || result.hasLostFunctionality, clues(text, language, target))
            if target == language then assert(result.finalState.nonEmpty, clues(text, result.conversionInfo))
            if placeholders && target != language then assertEquals(result.finalState, None, clues(text, language, target))
            result.conversionInfo.flatMap(_.sourceError).foreach { error =>
              assert(error.line.forall(_ >= 1), clues(text, error))
              assert(error.column.forall(_ >= 1), clues(text, error))
            }
            assertEquals(ProgrammingState.serializer.serialize(state), before, clues(text, target))
          }
        }
      }
    }
  }
}
