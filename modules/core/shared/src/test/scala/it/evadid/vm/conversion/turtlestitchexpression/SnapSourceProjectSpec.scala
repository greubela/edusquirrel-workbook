package it.evadid.vm.conversion.turtlestitchexpression

import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.vm.conversion.turtlestitchexpression.TurtleStitchProgramModel.*
import munit.FunSuite

class SnapSourceProjectSpec extends FunSuite {

  private val comparisonWithVariableXml =
    """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"></block><block s="doIf"><block s="reportVariadicLessThan"><list><block var="steps"/><l>10</l></list></block><script><block s="forward"><l>1</l></block></script></block></script></scripts></sprite></sprites></stage><variables><variable name="steps"></variable></variables></scene></scenes></project>"""

  test("load keeps list inputs on variadic operator blocks") {
    val project = SourceParsing.parse(comparisonWithVariableXml, SourceLanguage.Snap).project.toTry.get
    val sprite = project.scenes.head.stage.sprites.head
    val doIf = sprite.scripts.head.blocks(1).asInstanceOf[PrimitiveBlock]
    val lessThan = doIf.inputs.collectFirst { case NestedBlock(value) => value }.get.asInstanceOf[PrimitiveBlock]

    assertEquals(lessThan.selector, Some("reportVariadicLessThan"))
    assertEquals(lessThan.inputs.length, 1)

    val listInput = lessThan.inputs.head.asInstanceOf[ListLiteral]
    assertEquals(listInput.items.length, 2)
    assertEquals(listInput.items.head.asInstanceOf[NestedBlock].value.asInstanceOf[PrimitiveBlock].variable, Some("steps"))
    assertEquals(listInput.items(1).asInstanceOf[Literal].value, "10")
  }
  test("non-block project XML preserves authored metadata without browser APIs") {
    val project = SourceParsing.parse(
      """<?xml version="1.0"?><project name="A &amp; B"><notes><![CDATA[Keep <this>]]></notes><scenes select="1"><scene name="first"><stage name="Stage"><sprites><sprite name="Sprite"/></sprites></stage></scene></scenes></project>""", SourceLanguage.Snap).project.toTry.get
    assertEquals(project.name, "A & B")
    assertEquals(project.notes, "Keep <this>")
    assertEquals(project.scenes.head.stage.sprites.head.name, "Sprite")
    assertEquals(SourceParsing.parse("", SourceLanguage.Snap).project.toTry.get.scenes, Vector.empty)
    intercept[it.evadid.vm.code.errors.ProgrammingConversionException](SourceParsing.parse("<project>", SourceLanguage.Snap).project.toTry.get)
  }

}
