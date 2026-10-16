package it.evadid.workbook.elements.interactionElements.programming

import munit.FunSuite
import upickle.default.*
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*

class ProgrammingStateSerializationSpec extends FunSuite {
  test("current programming states round-trip as JSON, MessagePack and interaction content") {
    val xml = ProgrammingStateSnapXml("\n<project name='🐢'><notes>draft &amp; notes</notes><scripts/></project>\r\n", List("watcher\n", "", "opaque"))
    val states: List[ProgrammingState] = List(
      ProgrammingStatePythonString("\tdef unfinished(\r\n  "),
      ProgrammingStateJavaString("\r\npublic class Drawing {\t"), xml,
      xml.toSnapScalaModel.toOption.get, ProgrammingStateBeExpression(BeExpression.pass))
    val interaction = ProgrammingExercise("current-codec")
    states.foreach { state =>
      assertEquals(read[ProgrammingState](write(state)), state)
      assertEquals(readBinary[ProgrammingState](writeBinary(state)), state)
      val restored = interaction.serializerInteractionContent.deserialize(interaction.serializerInteractionContent.serialize(state))
      assertEquals(restored, state)
      assertEquals(ProgrammingState.serializer.deserialize(ProgrammingState.serializer.serialize(state)), state)
      assertEquals(restored.fingerprint(), state.fingerprint())
    }
  }
  test("empty source, Unicode, line separators and opaque strings stay content") {
    List("", " ", "\t\r\n\n  ", "🐢\r\n\u0000").foreach { code =>
      val states: List[ProgrammingState] = List(ProgrammingStatePythonString(code), ProgrammingStateJavaString(code),
        ProgrammingStateSnapXml(code, List("a" + 0.toChar + "b", "comment\r\n")))
      states.foreach(state => assertEquals(read[ProgrammingState](write(state)), state))
    }
  }
  test("checked Java expression states serialize their source and recover executable semantics") {
    val java = ProgrammingStateJavaString("\r\nclass Drawing { public static void main(String[] args) { Turtle.forward(12); } }\t\n")
    val expression = java.toBeExpressionState
    val restored = read[ProgrammingState](write[ProgrammingState](expression))
    assert(restored.isInstanceOf[ProgrammingStateBeExpression])
    assertEquals(restored.toJava, java)
    assertEquals(restored.fingerprint(), expression.fingerprint())
    assertEquals(restored.toBeExpressionState.deriveTurtleCommands, expression.deriveTurtleCommands)
    val binary = readBinary[ProgrammingState](writeBinary[ProgrammingState](expression))
    assertEquals(binary.toJava, java)
  }
  test("both programming interactions use the current implicit state codec") {
    val state = ProgrammingStateJavaString("\nclass Drawing {\r\n")
    List(ProgrammingExercise("regular").serializerInteractionContent,
      ProgrammingExerciseFullJava("java").serializerInteractionContent).foreach { serializer =>
      assertEquals(serializer.serialize(state), write[ProgrammingState](state))
      assertEquals(serializer.deserialize(serializer.serialize(state)), state)
    }
  }
  test("populating Java analysis and editable views leaves each saved representation unchanged") {
    val java = ProgrammingStateJavaString("\r\n\tclass CacheWire {\r\n" +
      " public static void main(String[] args) { Turtle.forward(12); }\r\n}\r\n// preserved comment\r\n\t ")
    val expression = java.toBeExpressionState
    List[ProgrammingState](java, expression).foreach { state =>
      val stored = ProgrammingState.serializer.serialize(state)
      val json = write[ProgrammingState](state)
      val binary = writeBinary[ProgrammingState](state)
      val fingerprint = state.fingerprint()

      assert(java.isClassProgram)
      java.toJavaVmProgram.toOption.get
      java.toPython
      java.toSnapXml
      expression.toPython
      expression.toSnapXml

      List(state, read[ProgrammingState](json), readBinary[ProgrammingState](binary),
        ProgrammingState.serializer.deserialize(stored)).foreach { value =>
        assertEquals(ProgrammingState.serializer.serialize(value), stored)
        assertEquals(write[ProgrammingState](value), json)
        assertEquals(writeBinary[ProgrammingState](value).toSeq, binary.toSeq)
        assertEquals(value.fingerprint(), fingerprint)
        assertEquals(value.toJava, java)
      }
    }
  }
  test("malformed current payloads fail explicitly instead of substituting another answer") {
    List("not JSON", "{\"$type\":\"unknown\"}").foreach { source =>
      intercept[Exception](ProgrammingState.serializer.deserialize(source))
    }
  }
}
