package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}
import munit.FunSuite

class ProgrammingExerciseStateSerializerSpec extends FunSuite {

  test("serialize writes SNAP_XML_V1 and deserialize roundtrips xml") {
    val xml = """<project name="stored"><scenes></scenes></project>"""
    val stored = ProgrammingExercise.StateSerializer.serialize(ProgrammingExerciseState(xml))
    assert(stored.startsWith("SNAP_XML_V1"), clue = stored.take(80))
    assert(stored.contains(xml), clue = stored)
    val restored = ProgrammingExercise.StateSerializer.deserialize(stored)
    assertEquals(restored.snapXml, xml)
  }

  test("raw project xml deserializes without header") {
    val xml = """<project name="raw"><scenes></scenes></project>"""
    val restored = ProgrammingExercise.StateSerializer.deserialize(xml)
    assertEquals(restored.snapXml, xml)
  }

  test("legacy pure python migrates to snap xml") {
    val program = BeProgram.miniProgram()
    val python = SnapTurtlePythonBridge.printedPython(program.fullProgram)
    val restored = ProgrammingExercise.StateSerializer.deserialize(python)
    assert(restored.snapXml.contains("<project"), clue = restored.snapXml.take(120))
    assert(restored.snapXml.contains("""s="forward""""), clue = restored.snapXml)
    val stored = ProgrammingExercise.StateSerializer.serialize(restored)
    assert(stored.startsWith("SNAP_XML_V1"), clue = stored.take(80))
  }

  test("fingerprint is the stored xml so position-only xml differs") {
    val a = ProgrammingExerciseState("""<project><scripts><script x="70" y="80"></script></scripts></project>""")
    val b = ProgrammingExerciseState("""<project><scripts><script x="200" y="150"></script></scripts></project>""")
    assert(ProgrammingExerciseState.fingerprint(a) != ProgrammingExerciseState.fingerprint(b))
  }

  test("numeric call literal survives python migrate then xml roundtrip") {
    val param = BeDefineVariable(BeEntityName.fromUniversalNameInParts("arg1"), BeDataType.AnyType)
    val defn = BeDefineFunction(
      List(param),
      None,
      BeExpression.pass,
      BeDefineFunction.functionInfo(BeEntityName.fromUniversalNameInParts("forward"))
    )
    val call = BeFunctionCall(defn, Map(param -> BeUseValue(BeDataValueLiteral("12345"), Some(param))))
    val state = ProgrammingExerciseState.fromProgram(BeProgram(BeStartProgram(BeSequence.optionalBody(List(call)))))
    val stored = ProgrammingExercise.StateSerializer.serialize(state)
    assert(stored.contains("12345"), clue = stored)
    assert(!stored.contains("arg1 ="), clue = stored)

    val restored = ProgrammingExercise.StateSerializer.deserialize(stored)
    val again = ProgrammingExercise.StateSerializer.serialize(restored)
    assert(again.contains("12345"), clue = again)
  }

  test("legacy named call args still restore literal on deserialize") {
    val stored = "forward(arg1 = 99)"
    val restored = ProgrammingExercise.StateSerializer.deserialize(stored)
    val again = ProgrammingExercise.StateSerializer.serialize(restored)
    assert(again.contains("99"), clue = again)
  }

  test("unsupported snap blocks survive serialize/deserialize") {
    val xml =
      """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script x="70" y="80"><block s="wait"><l>1</l></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val stored = ProgrammingExercise.StateSerializer.serialize(ProgrammingExerciseState(xml))
    val restored = ProgrammingExercise.StateSerializer.deserialize(stored)
    assert(restored.snapXml.contains("""s="wait""""), clue = restored.snapXml)
    assertEquals(restored.snapXml, xml)
  }
}
