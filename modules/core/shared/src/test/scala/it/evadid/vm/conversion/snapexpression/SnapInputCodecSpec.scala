package it.evadid.vm.conversion.snapexpression

import it.evadid.core.datastructures.color.RGBColor
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.code.usage.BeUseValue
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral, BeUseValueReference}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog.SnapInputKind
import munit.FunSuite

class SnapInputCodecSpec extends FunSuite {
  private def literal(value: String) = BeUseValue(BeDataValueLiteral(value), None)

  test("quotes are stripped only when they enclose the value") {
    assertEquals(SnapInputCodec.stripQuotes(" \"red\" "), "red")
    assertEquals(SnapInputCodec.stripQuotes("'red'"), "red")
    assertEquals(SnapInputCodec.stripQuotes("'red"), "'red")
    assertEquals(SnapInputCodec.stripQuotes("\""), "\"")
    assertEquals(it.evadid.vm.types.StringLiteral.quote("a\"b\\c"), "\"a\\\"b\\\\c\"")
  }

  test("colors are read from Snap RGBA lists and web colours") {
    assertEquals(SnapInputCodec.parseColor("'255,0,0'"), Some(RGBColor(255, 0, 0, 255)))
    assertEquals(SnapInputCodec.parseColor("0,128,0,0.5"), Some(RGBColor(0, 128, 0, 128)))
    assertEquals(SnapInputCodec.parseColor("0,128,0,200"), Some(RGBColor(0, 128, 0, 200)))
    assertEquals(SnapInputCodec.parseColor("0,300,0"), None)
    assertEquals(SnapInputCodec.parseColor("1,x,2"), None)
    assertEquals(SnapInputCodec.parseColor("\"blue\""), Some(RGBColor(0, 0, 255, 255)))
    assertEquals(SnapInputCodec.parseColor("''"), None)
    assertEquals(SnapInputCodec.parseColor("not a colour"), None)
  }

  test("colors are written as named Python colours, rgb() text and Snap RGBA") {
    assertEquals(SnapInputCodec.pythonQuotedColor(RGBColor(255, 0, 0)), "\"red\"")
    assertEquals(SnapInputCodec.pythonQuotedColor(RGBColor(1, 2, 3)), "\"rgb(1, 2, 3)\"")
    assertEquals(SnapInputCodec.pythonQuotedColorFromRaw("0,0,255"), Some("\"blue\""))
    assertEquals(SnapInputCodec.snapRgbaString(RGBColor(1, 2, 3, 255)), "1,2,3,1")
    assertEquals(SnapInputCodec.snapRgbaString(RGBColor(1, 2, 3, 0)), "1,2,3,0")
    assertEquals(SnapInputCodec.snapRgbaString(RGBColor(1, 2, 3, 51)), "1,2,3,0.2")
    assertEquals(SnapInputCodec.snapColorXml(RGBColor(1, 2, 3)), "<color>1,2,3,1</color>")
  }

  test("literals render per slot kind with escaped text") {
    assertEquals(SnapInputCodec.renderLiteral(SnapInputKind.Color, "'red'"), "<color>255,0,0,1</color>")
    assertEquals(SnapInputCodec.renderLiteral(SnapInputKind.Color, "'<x>'"), "<l>&lt;x&gt;</l>")
    assertEquals(SnapInputCodec.renderLiteral(SnapInputKind.Bool, "True"), "<l>true</l>")
    assertEquals(SnapInputCodec.renderLiteral(SnapInputKind.Bool, "FALSE"), "<l>false</l>")
    assertEquals(SnapInputCodec.renderLiteral(SnapInputKind.Bool, "maybe"), "<l>maybe</l>")
    assertEquals(SnapInputCodec.renderLiteral(SnapInputKind.String, "a&'b\""), "<l>a&amp;&apos;b&quot;</l>")
  }

  test("slot validation accepts literals, variables and conditions of the matching kind") {
    val variable = BeUseValue(BeUseValueReference(BeDefineVariable(BeEntityName.fromLiteral("c"), BeDataType.AnyType)), None)
    assert(SnapInputCodec.isValidForKind(SnapInputKind.Color, literal("'red'")))
    assert(!SnapInputCodec.isValidForKind(SnapInputKind.Color, literal("'nope'")))
    assert(SnapInputCodec.isValidForKind(SnapInputKind.Color, variable))
    assert(!SnapInputCodec.isValidForKind(SnapInputKind.Color, BeSequence.optionalBody(Nil)))
    assert(SnapInputCodec.isValidForKind(SnapInputKind.Bool, literal("True")))
    assert(SnapInputCodec.isValidForKind(SnapInputKind.Numeric, literal("3")))
    assert(SnapInputCodec.isValidForKind(SnapInputKind.String, variable))
  }
}
