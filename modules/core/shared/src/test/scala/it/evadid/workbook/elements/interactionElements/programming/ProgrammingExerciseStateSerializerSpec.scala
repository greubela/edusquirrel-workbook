package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.BeProgram
import munit.FunSuite

class ProgrammingExerciseStateSerializerSpec extends FunSuite {

  test("legacy pure python deserializes with empty layout") {
    val program = BeProgram.miniProgram()
    val python = program.fullProgram.structureInfo.toStringInLanguage(
      it.evadid.core.datastructures.language.AppLanguage.Python,
      it.evadid.core.datastructures.language.AppLanguage.English,
      false
    )
    val restored = ProgrammingExercise.StateSerializer.deserialize(python)
    assert(restored.canvasLayout.isEmpty)
    assert(ProgrammingExerciseState.pythonOf(restored).nonEmpty)
  }

  test("composite layout roundtrips across serialize/deserialize") {
    val layout = SnapCanvasLayout(
      List(
        SnapCanvasScript(70, 80, 2),
        SnapCanvasScript(200, 150, 1)
      )
    )
    val state = ProgrammingExerciseState(BeProgram.miniProgram(), layout)
    val stored = ProgrammingExercise.StateSerializer.serialize(state)
    assert(stored.startsWith("SNAP_LAYOUT_V1"), clue = stored.take(120))
    assert(stored.contains("---"), clue = stored.take(200))

    val restored = ProgrammingExercise.StateSerializer.deserialize(stored)
    assertEquals(restored.canvasLayout.scripts, layout.scripts)
  }

  test("fingerprint includes layout so position-only changes differ") {
    val program = BeProgram.miniProgram()
    val a = ProgrammingExerciseState(program, SnapCanvasLayout.single(70, 80, 1))
    val b = ProgrammingExerciseState(program, SnapCanvasLayout.single(200, 150, 1))
    assert(ProgrammingExerciseState.fingerprint(a) != ProgrammingExerciseState.fingerprint(b))
  }

  test("numeric call literal survives serialize/deserialize roundtrip") {
    import it.evadid.vm.code.abstractions.BeExpression
    import it.evadid.vm.code.controlStructures.BeSequence
    import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
    import it.evadid.vm.code.others.BeStartProgram
    import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
    import it.evadid.vm.naming.BeEntityName
    import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}

    val param = BeDefineVariable(BeEntityName.fromUniversalNameInParts("arg1"), BeDataType.AnyType)
    val defn = BeDefineFunction(
      List(param),
      None,
      BeExpression.pass,
      BeDefineFunction.functionInfo(BeEntityName.fromUniversalNameInParts("forward"))
    )
    val call = BeFunctionCall(defn, Map(param -> BeUseValue(BeDataValueLiteral("12345"), Some(param))))
    val state = ProgrammingExerciseState(BeProgram(BeStartProgram(BeSequence.optionalBody(List(call)))))
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
}
