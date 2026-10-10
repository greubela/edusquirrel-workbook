package it.evadid.workbook.elements.interactionElements.programming
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*

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
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXml}
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
      ProgrammingStateSnapXml("<project/>", List("watcher", "comment"))
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

  test("unfinished Java and Python sources roundtrip without parsing or trimming") {
    val states: List[ProgrammingState] = List(
      ProgrammingStateJavaString("  public class Drawing {\r\n    public static void main(\n\n  "),
      ProgrammingStatePythonString("\tdef draw(\r\n    # unfinished\n\n\t "),
      ProgrammingStateJavaString("\nint steps = 12;\nforward(steps);\n\n"),
      ProgrammingStatePythonString("\nforward(12)\r\n\r\n")
    )

    states.foreach { state =>
      val restored = ProgrammingExercise.StateSerializer.deserialize(
        ProgrammingExercise.StateSerializer.serialize(state)
      )
      assertEquals(restored, state)
      assertEquals(ProgrammingState.fingerprint(restored), ProgrammingState.fingerprint(state))
    }
  }

  test("empty and whitespace-only source states retain their representation and content") {
    List("", " ", "\t\r\n\n  ").foreach { code =>
      List[ProgrammingState](ProgrammingStateJavaString(code), ProgrammingStatePythonString(code)).foreach { state =>
        assertEquals(ProgrammingExercise.StateSerializer.deserialize(
          ProgrammingExercise.StateSerializer.serialize(state)
        ), state)
      }
    }
  }

  test("version 2 XML and floating objects retain their exact content") {
    val xml = "\n  " +
      """<project name="stored"><notes>draft &amp; notes</notes><costumes><list><costume id="42"/></list></costumes><scripts><script x="120" y="80"><block s="wait"><l>1</l></block></script></scripts></project>""" +
      "\r\n\n\t "
    val states: List[ProgrammingState] = List(
      ProgrammingStateSnapXml(xml),
      ProgrammingStateSnapXml(xml, List(" watcher ", "comment\nline two\r\n", ""))
    )

    states.foreach { state =>
      assertEquals(ProgrammingExercise.StateSerializer.deserialize(
        ProgrammingExercise.StateSerializer.serialize(state)
      ), state)
    }
  }

  test("CRLF state headers leave the source payload unchanged") {
    val code = " \r\nforward(12);\n\t \r\n"
    val states = List[(String, ProgrammingState)](
      "JAVA" -> ProgrammingStateJavaString(code),
      "PYTHON" -> ProgrammingStatePythonString(code),
      "SNAP_XML" -> ProgrammingStateSnapXml(" \n<project/>\r\n\n")
    )

    states.foreach { (kind, state) =>
      val serialized = ProgrammingExercise.StateSerializer.serialize(state)
      val payload = serialized.stripPrefix(s"${ProgrammingExercise.StateHeader}\n$kind\n")
      val stored = s"${ProgrammingExercise.StateHeader}\r\n$kind\r\n$payload"
      assertEquals(ProgrammingExercise.StateSerializer.deserialize(stored), state)
    }

    val floating = ProgrammingStateSnapXml("\n<project/>\r\n ", List(" watcher\n"))
    val stored = s"${ProgrammingExercise.StateHeader}\r\nSNAP_XML_WITH_FLOATING\r\n" +
      upickle.default.write(floating.legacyFloatingObjects) + "\r\n" + floating.snapXml
    assertEquals(ProgrammingExercise.StateSerializer.deserialize(stored), floating)
  }

  test("fingerprint is the stored xml so position-only xml differs") {
    val a = ProgrammingStateSnapXml("""<project><scripts><script x="70" y="80"></script></scripts></project>""")
    val b = ProgrammingStateSnapXml("""<project><scripts><script x="200" y="150"></script></scripts></project>""")
    assert(ProgrammingStateSnapXml.fingerprint(a) != ProgrammingStateSnapXml.fingerprint(b))
  }

  private val legacyFloatingTag = "ProgrammingStateSnapXMLWithAdditionalFloatingObjects"
  private val historicalProgrammingPackage = "it.evadid.workbook.elements.interactionElements.programming."
  private val nestedProgrammingPackage = historicalProgrammingPackage + "state.ProgrammingState.ProgrammingState."
  private val exactLegacyXml =
    "\n\t" + """<project name="Grüße"><notes>unknown &amp; saved</notes><scripts><script x="19" y="27"><block s="unrecognized"><l> \ </l></block></script></scripts></project>""" + "\r\n \t"
  private val opaqueObjects = List(
    " watcher ", "comment\nline two\r\n", "", """<unrecognized kind="opaque"/>""", "Grüße \\")
  private val opaqueObjectsJson =
    """[" watcher ","comment\nline two\r\n","","<unrecognized kind=\"opaque\"/>","Grüße \\"]"""

  private def legacyJson(tag: String, objectsJson: String = opaqueObjectsJson): String =
    """{"$type":""" + upickle.default.write(tag) +
      ""","snapXml":""" + upickle.default.write(exactLegacyXml) +
      ""","additionalFloatingObjects":""" + objectsJson + "}"

  test("literal legacy version 2 floating state becomes one Snap state without losing opaque strings") {
    val stored = "PROGRAMMING_STATE_V2\nSNAP_XML_WITH_FLOATING\n" + opaqueObjectsJson + "\n" + exactLegacyXml
    val expected = ProgrammingStateSnapXml(exactLegacyXml, opaqueObjects)
    val restored = ProgrammingExercise.StateSerializer.deserialize(stored)
    assertEquals(restored, expected)
    assertEquals(restored.toSnapXml, expected)
    assertEquals(ProgrammingExercise.StateSerializer.serialize(restored), stored)
    assertEquals(ProgrammingExercise.StateSerializer.deserialize(
      ProgrammingExercise.StateSerializer.serialize(restored)), expected)
    val ProgrammingStateSnapXml(extracted) = expected
    assertEquals(extracted, exactLegacyXml)
    assertEquals(expected.copy(), expected)
  }

  test("legacy floating JSON tags preserve XML and opaque strings and keep the older reader wire shape") {
    val expected = ProgrammingStateSnapXml(exactLegacyXml, opaqueObjects)
    val directJson = upickle.default.writeJs[ProgrammingStateSnapXml](expected)
    assertEquals(directJson("$type").str, legacyFloatingTag)
    assertEquals(directJson.obj.keySet.toSet, Set("$type", "snapXml", "additionalFloatingObjects"))
    assertEquals(upickle.default.read[ProgrammingStateSnapXml](directJson), expected)
    val tags = List(
      legacyFloatingTag,
      historicalProgrammingPackage + legacyFloatingTag,
      nestedProgrammingPackage + legacyFloatingTag)
    tags.foreach { tag =>
      val restored = upickle.default.read[ProgrammingState](legacyJson(tag))
      assertEquals(restored, expected)
      val written = upickle.default.writeJs[ProgrammingState](restored)
      assertEquals(written.obj.keySet.toSet, Set("$type", "snapXml", "additionalFloatingObjects"))
      assertEquals(written("$type").str, legacyFloatingTag)
      assertEquals(written("snapXml").str, exactLegacyXml)
      assertEquals(upickle.default.read[List[String]](written("additionalFloatingObjects")), opaqueObjects)
      assertEquals(upickle.default.read[ProgrammingState](written), expected)
      assertEquals(ProgrammingExercise.StateSerializer.deserialize(
        ProgrammingExercise.StateSerializer.serialize(restored)), expected)
    }
  }

  test("empty legacy floating lists normalize to the ordinary Snap wire representation") {
    val expected = ProgrammingStateSnapXml(exactLegacyXml)
    val directJson = upickle.default.writeJs[ProgrammingStateSnapXml](expected)
    assertEquals(directJson.obj.keySet.toSet, Set("$type", "snapXml"))
    assertEquals(upickle.default.read[ProgrammingStateSnapXml](directJson), expected)
    val restored = List(
      ProgrammingExercise.StateSerializer.deserialize(
        "PROGRAMMING_STATE_V2\nSNAP_XML_WITH_FLOATING\n[]\n" + exactLegacyXml),
      upickle.default.read[ProgrammingState](legacyJson(legacyFloatingTag, "[]")))
    restored.foreach { state =>
      assertEquals(state, expected)
      assertEquals(state.toSnapXml.legacyFloatingObjects, Nil)
      assertEquals(ProgrammingExercise.StateSerializer.serialize(state),
        "PROGRAMMING_STATE_V2\nSNAP_XML\n" + exactLegacyXml)
      val json = upickle.default.writeJs[ProgrammingState](state)
      assertEquals(json("$type").str, "ProgrammingStateSnapXml")
      assertEquals(json.obj.keySet.toSet, Set("$type", "snapXml"))
      assertEquals(upickle.default.read[ProgrammingState](json), expected)
    }
  }

  test("malformed legacy floating payloads reject instead of replacing saved work with the mini project") {
    val taggedPrefix = "PROGRAMMING_STATE_V2\nSNAP_XML_WITH_FLOATING\n"
    val malformedTagged = List(
      taggedPrefix + opaqueObjectsJson,
      taggedPrefix + "[broken]\n" + exactLegacyXml,
      taggedPrefix + """["keep",12]""" + "\n" + exactLegacyXml,
      taggedPrefix + """{"objects":["keep"]}""" + "\n" + exactLegacyXml)
    malformedTagged.foreach { stored =>
      intercept[IllegalArgumentException](ProgrammingExercise.StateSerializer.deserialize(stored))
    }
    val malformedJson = List(
      legacyJson(legacyFloatingTag, """["keep",12]"""),
      legacyJson(legacyFloatingTag, "null"),
      """{"$type":"ProgrammingStateSnapXMLWithAdditionalFloatingObjects","snapXml":"<project/>"}""",
      """{"$type":"ProgrammingStateSnapXMLWithAdditionalFloatingObjects","additionalFloatingObjects":["keep"]}""",
      """{"$type":"ProgrammingStateSnapXMLWithAdditionalFloatingObjects","snapXml":42,"additionalFloatingObjects":["keep"]}""")
    malformedJson.foreach { stored =>
      intercept[Exception](upickle.default.read[ProgrammingState](stored))
    }
  }

  test("all four regular programming JSON states read historical and current type aliases") {
    val states = List[(String, ProgrammingState)](
      "ProgrammingStateSnapXml" -> ProgrammingStateSnapXml(exactLegacyXml),
      "ProgrammingStatePythonString" -> ProgrammingStatePythonString("\tdef draw(\r\n\n "),
      "ProgrammingStateJavaString" -> ProgrammingStateJavaString("\nclass Drawing {\r\n\t "),
      "ProgrammingStateBeExpression" -> ProgrammingStateBeExpression(BeExpression.pass))
    states.foreach { (simpleTag, state) =>
      val json = upickle.default.writeJs[ProgrammingState](state)
      assertEquals(upickle.default.read[ProgrammingState](json), state)
      state match {
        case value: ProgrammingStateSnapXml =>
          assertEquals(upickle.default.writeJs(value), json)
          assertEquals(upickle.default.read[ProgrammingStateSnapXml](json), value)
        case value: ProgrammingStatePythonString =>
          assertEquals(upickle.default.writeJs(value), json)
          assertEquals(upickle.default.read[ProgrammingStatePythonString](json), value)
        case value: ProgrammingStateJavaString =>
          assertEquals(upickle.default.writeJs(value), json)
          assertEquals(upickle.default.read[ProgrammingStateJavaString](json), value)
        case value: ProgrammingStateBeExpression =>
          assertEquals(upickle.default.writeJs(value), json)
          assertEquals(upickle.default.read[ProgrammingStateBeExpression](json), value)
      }
      val currentPrefix =
        if simpleTag == "ProgrammingStateBeExpression" then historicalProgrammingPackage + "state.ProgrammingState."
        else nestedProgrammingPackage
      List(simpleTag, historicalProgrammingPackage + simpleTag, currentPrefix + simpleTag).foreach { tag =>
        val fixture = ujson.Obj.from(json.obj.iterator)
        fixture("$type") = ujson.Str(tag)
        assertEquals(upickle.default.read[ProgrammingState](fixture), state)
      }
    }
  }

  test("Snap fingerprints distinguish opaque list boundaries, empty strings and metadata-only changes") {
    val states = List(
      ProgrammingStateSnapXml(exactLegacyXml),
      ProgrammingStateSnapXml(exactLegacyXml, List("a", "b")),
      ProgrammingStateSnapXml(exactLegacyXml, List("a" + 0.toChar + "b")),
      ProgrammingStateSnapXml(exactLegacyXml, List("")),
      ProgrammingStateSnapXml(exactLegacyXml, List("b", "a")))
    val fingerprints = states.map(ProgrammingState.fingerprint)
    assertEquals(fingerprints.distinct.size, states.size)
    states.foreach { state =>
      val restored = ProgrammingExercise.StateSerializer.deserialize(
        ProgrammingExercise.StateSerializer.serialize(state))
      assertEquals(ProgrammingState.fingerprint(restored), ProgrammingState.fingerprint(state))
    }
  }

  test("opaque legacy data blocks editable text conversion while pure expression extraction still works") {
    val plain = ProgrammingStateSnapXml.mini
    val retained = plain.copy(legacyFloatingObjects = opaqueObjects)
    val before = ProgrammingExercise.StateSerializer.serialize(retained)
    intercept[IllegalArgumentException](retained.toPython)
    intercept[IllegalArgumentException](retained.toJava)
    assertEquals(retained.toBeExpressionState, plain.toBeExpressionState)
    assertEquals(retained.toSnapXml, retained)
    assertEquals(ProgrammingExercise.StateSerializer.serialize(retained), before)
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

  test("checked Java VM states keep their original source in the existing Java wire format") {
    val source = ProgrammingStateJavaString("\r\npublic class Drawing { public static void main(String[] args) { " +
      "int n = 2147483647; Turtle.forward(n++); Turtle.forward(n); } }\r\n\t ")
    val expression = source.toBeExpressionState
    val stored = ProgrammingExercise.StateSerializer.serialize(expression)
    assertEquals(stored, ProgrammingExercise.StateSerializer.serialize(source))
    val restored = ProgrammingExercise.StateSerializer.deserialize(stored)
    assertEquals(restored.toJava, source)
    assertEquals(restored.toBeExpressionState.deriveTurtleCommands, expression.deriveTurtleCommands)
    assertEquals(ProgrammingState.fingerprint(expression), ProgrammingState.fingerprint(source))

    val json = upickle.default.write[ProgrammingState](expression)
    assertEquals(upickle.default.read[ProgrammingState](json), source)
    val subtypeJson = upickle.default.write[ProgrammingStateBeExpression](expression)
    val subtype = upickle.default.read[ProgrammingStateBeExpression](subtypeJson)
    assertEquals(subtype.toJava, source)
    assertEquals(subtype.deriveTurtleCommands, expression.deriveTurtleCommands)
  }
}
