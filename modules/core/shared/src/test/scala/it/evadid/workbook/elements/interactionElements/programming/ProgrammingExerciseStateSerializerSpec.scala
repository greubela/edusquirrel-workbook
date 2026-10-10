package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExercise
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtlePythonBridge
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXMLWithAdditionalFloatingObjects, ProgrammingStateSnapXml}
import munit.FunSuite

class ProgrammingExerciseStateSerializerSpec extends FunSuite {

  test("Snap state companion constructs and extracts XML without recursion") {
    val xml = "<project><scripts/></project>"
    val state = ProgrammingStateSnapXml(xml)
    assertEquals(state.snapXml, xml)
    val ProgrammingStateSnapXml(extracted) = state
    assertEquals(extracted, xml)
    assertEquals(state.copy().snapXml, xml)
  }

  test("serialize writes SNAP_XML_V1 and deserialize roundtrips xml") {
    val xml = """<project name="stored"><scenes></scenes></project>"""
    val stored = ProgrammingExercise.StateSerializer.serialize(ProgrammingStateSnapXml(xml))
    assert(stored.startsWith("PROGRAMMING_STATE_V2\nSNAP_XML"), clue = stored.take(80))
    assert(stored.contains(xml), clue = stored)
    val restored = ProgrammingExercise.StateSerializer.deserialize(stored)
    assertEquals(restored.toSnapXml.snapXml, xml)
  }

  test("raw project xml deserializes without header") {
    val xml = """<project name="raw"><scenes></scenes></project>"""
    val restored = ProgrammingExercise.StateSerializer.deserialize(xml)
    assertEquals(restored.toSnapXml.snapXml, xml)
  }

  test("legacy pure python migrates to snap xml") {
    val program = BeProgram.miniProgram()
    val python = SnapTurtlePythonBridge.printedPython(program.fullProgram)
    val restored = ProgrammingExercise.StateSerializer.deserialize(python)
    assert(restored.toSnapXml.snapXml.contains("<project"), clue = restored.toSnapXml.snapXml.take(120))
    assert(restored.toSnapXml.snapXml.contains("""s="forward""""), clue = restored.toSnapXml.snapXml)
    val stored = ProgrammingExercise.StateSerializer.serialize(restored)
    assert(stored.startsWith("PROGRAMMING_STATE_V2\nSNAP_XML"), clue = stored.take(80))
  }

  test("all textual programming states roundtrip with their representation") {
    val states: List[ProgrammingState] = List(
      ProgrammingStatePythonString("print('hello')"),
      ProgrammingStateJavaString("class Main {}"),
      ProgrammingStateSnapXMLWithAdditionalFloatingObjects("<project/>", List("watcher", "comment"))
    )

    states.foreach { state =>
      assertEquals(ProgrammingExercise.StateSerializer.deserialize(
        ProgrammingExercise.StateSerializer.serialize(state)
      ), state)
    }
  }

  test("versioned source code preserves leading and trailing whitespace") {
    val states: List[ProgrammingState] = List(
      ProgrammingStatePythonString("  print('hello') \n\n"),
      ProgrammingStateJavaString("\nclass Main {}\n")
    )
    states.foreach { state =>
      assertEquals(ProgrammingExercise.StateSerializer.deserialize(
        ProgrammingExercise.StateSerializer.serialize(state)
      ), state)
    }
  }

  test("unknown version 2 representation falls back to the default Snap project") {
    val restored = ProgrammingExercise.StateSerializer.deserialize("PROGRAMMING_STATE_V2\nRUBY\nputs 1")
    assertEquals(restored, ProgrammingStateSnapXml.mini)
  }

  test("fingerprint is the stored xml so position-only xml differs") {
    val a = ProgrammingStateSnapXml("""<project><scripts><script x="70" y="80"></script></scripts></project>""")
    val b = ProgrammingStateSnapXml("""<project><scripts><script x="200" y="150"></script></scripts></project>""")
    assert(ProgrammingStateSnapXml.fingerprint(a) != ProgrammingStateSnapXml.fingerprint(b))
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
    val state = ProgrammingStateSnapXml.fromProgram(BeProgram(BeStartProgram(BeSequence.optionalBody(List(call)))))
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
    val stored = ProgrammingExercise.StateSerializer.serialize(ProgrammingStateSnapXml(xml))
    val restored = ProgrammingExercise.StateSerializer.deserialize(stored)
    assert(restored.toSnapXml.snapXml.contains("""s="wait""""), clue = restored.toSnapXml.snapXml)
    assertEquals(restored.toSnapXml.snapXml, xml)
  }
}
