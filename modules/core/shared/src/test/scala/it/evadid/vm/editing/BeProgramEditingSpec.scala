package it.evadid.vm.editing

import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.core.datastructures.language.AppLanguage.{BlockDisplay, Python}
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeRepeatNr, BeWhile}
import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.vm.types.BeChildRole.{BodySequence, ConditionInControlStructure, ExpressionInSequence, FunctionParameter}
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}
import it.evadid.workbook.interactions.programming.state.{ProgrammingStateBeExpression, ProgrammingStateRaw}
import munit.FunSuite

class BeProgramEditingSpec extends FunSuite {
  private val main = BeNodePath.root / BodySequence(0)
  private def statement(index: Int) = main / ExpressionInSequence(index)
  private def python(program: BeProgram): String = ProgrammingStateBeExpression(program.fullProgram).toPython.code
  private def template(name: String) = BeBlockLibrary.turtle.templates.map(_.statement).find {
    case call: BeFunctionCall => call.funcDef.functionTypeInfo.displayName.universalInterpretation() == name
    case _ => false
  }.get
  private val forward = template("forward")
  private val right = template("right")
  private val repeat = BeBlockLibrary.turtle.templates.map(_.statement).collectFirst { case loop: BeRepeatNr => loop }.get
  /** The blocks inserted one after another into the main sequence of an empty program. */
  private def programWith(blocks: BeExpression*): BeProgram =
    blocks.zipWithIndex.foldLeft(BeProgram.empty) { case (current, (block, index)) =>
      BeProgramEditing.insert(current, BeSequenceSlot(main, index), block).toOption.get
    }

  test("library templates are parsed statements with an editable loop") {
    assert(BeBlockLibrary.turtle.templates.exists(_.statement.isInstanceOf[BeWhile]))
    assertEquals(repeat.fixedAmount, Some(4))
    assertEquals(repeat.body.body, Nil)
  }

  test("inserting into the empty program produces convertible source") {
    val edited = programWith(forward)
    assert(python(edited).contains("forward(100)"), python(edited))
    assertEquals(BeProgramEditing.find(edited, statement(0)), Some(forward))
  }

  test("slots interleave sequence positions with nested bodies in source order") {
    val program = programWith(forward, repeat, right)
    val loopBody = statement(1) / BodySequence(0)
    val slots = BeProgramEditing.slots(program)
    assertEquals(slots.filter(_.sequence == main).map(_.index), Seq(0, 1, 2, 3))
    assert(slots.indexOf(BeSequenceSlot(loopBody, 0)) > slots.indexOf(BeSequenceSlot(main, 1)))
    assert(slots.indexOf(BeSequenceSlot(loopBody, 0)) < slots.indexOf(BeSequenceSlot(main, 2)))
  }

  test("moving a statement into a later loop adjusts the loop's path after removal") {
    val program = programWith(forward, repeat)
    val moved = BeProgramEditing.move(program, statement(0), BeSequenceSlot(statement(1) / BodySequence(0), 0)).toOption.get
    val loop = BeProgramEditing.find(moved, statement(0)).collect { case loop: BeRepeatNr => loop }.get
    assertEquals(loop.body.body, Seq(forward))
    assertEquals(BeProgramEditing.find(moved, statement(1)), None)
  }

  test("moving within one sequence keeps the requested position") {
    val program = programWith(forward, right)
    val moved = BeProgramEditing.move(program, statement(0), BeSequenceSlot(main, 2)).toOption.get
    assertEquals(BeProgramEditing.find(moved, statement(0)), Some(right))
    assertEquals(BeProgramEditing.find(moved, statement(1)), Some(forward))
  }

  test("illegal edits leave a reason instead of a changed program") {
    val program = programWith(repeat)
    assert(BeProgramEditing.move(program, statement(0), BeSequenceSlot(statement(0) / BodySequence(0), 0)).isLeft)
    assert(BeProgramEditing.insert(program, BeSequenceSlot(main, 5), forward).isLeft)
    val whileLoop = SourceParsing.parse("while True:\n    forward(1)\n", SourceLanguage.Python).parsed().program
    val condition = BeNodePath.root / BodySequence(0) / ExpressionInSequence(0) / ConditionInControlStructure
    assert(!BeProgramEditing.accepts(whileLoop, BeSequenceSlot(condition, 0), forward))
    assert(BeProgramEditing.remove(program, main).isLeft)
  }

  test("removing a statement deletes only that statement") {
    val program = programWith(forward)
    assertEquals(BeProgramEditing.remove(program, statement(0)).map(python), Right(python(BeProgram.empty)))
  }

  private def valueAt(program: BeProgram, path: BeNodePath) = BeProgramEditing.find(program, path)

  test("values are typed expressions checked against the parameter type") {
    val program = programWith(forward)
    val distance = statement(0) / FunctionParameter(0)
    assert(BeValueInput.isValuePosition(program, distance))
    assertEquals(BeValueInput.expectedType(program, distance), BeDataType.Numeric)
    assertEquals(BeValueInput.textAt(program, distance), Some("100"))
    assertEquals(BeValueInput.update(program, distance, "abc"), Left(BeValueInput.Rejected(BeValueInput.Kind.Number)))
    assertEquals(BeValueInput.update(program, distance, "forward(3)").left.map(_.expected), Left(BeValueInput.Kind.Number))
    val edited = BeValueInput.update(program, distance, " 42.5 ").toOption.get
    assertEquals(valueAt(edited, distance).collect { case BeUseValue(value, _) => value }, Some(BeDataValueLiteral("42.5")))
    assert(python(edited).contains("forward(42.5)"), python(edited))
  }

  test("operators on literals are computed and other calls keep their folded arguments") {
    val program = programWith(forward)
    val distance = statement(0) / FunctionParameter(0)
    val sum = BeValueInput.update(program, distance, "3+5").toOption.get
    assertEquals(BeValueInput.textAt(sum, distance), Some("8"))
    assertEquals(BeValueInput.textAt(BeValueInput.update(program, distance, "(2 + 4) * 1.5 - 1").toOption.get, distance), Some("8"))
    val call = BeValueInput.update(program, distance, "add(3+5, 3)").toOption.get
    assertEquals(BeValueInput.textAt(call, distance), Some("add(8, 3)"))
    assert(python(call).contains("forward(add(8, 3))"), python(call))
    // The call's literal arguments stay editable values.
    assertEquals(BeValueInput.textAt(call, distance / FunctionParameter(0)), Some("8"))
    assert(BeValueInput.isValuePosition(call, distance / FunctionParameter(0)))
  }

  test("calls bind to the program's own functions and inputs may only use its variables") {
    val program = SourceParsing.parse("def add(a: float, b: float) -> float:\n    return a + b\n\nforward(1)\n", SourceLanguage.Python).parsed().program
    val forwardPath = BeProgramEditing.slots(program).collectFirst { case slot if slot.sequence == main => slot }
      .map(slot => main / ExpressionInSequence(slot.index)).get
    val sequencePath = BeProgramEditing.find(program, main).collect { case seq: it.evadid.vm.code.controlStructures.BeSequence =>
      main / ExpressionInSequence(seq.body.indexWhere(_.isInstanceOf[BeFunctionCall])) }.get
    val distance = sequencePath / FunctionParameter(0)
    val edited = BeValueInput.update(program, distance, "add(1, 2*3)").toOption.get
    val defined = edited.fullProgram.subtree.collect { case d: it.evadid.vm.code.defining.BeDefineFunction
      if d.functionTypeInfo.displayName.universalInterpretation() == "add" => d }.head
    assertEquals(valueAt(edited, distance).collect { case call: BeFunctionCall => call.funcDef }, Some(defined))
    assertEquals(BeValueInput.textAt(edited, distance), Some("add(1, 6)"))
    assertEquals(BeValueInput.update(program, distance, "undefinedName + 1").left.map(_.expected), Left(BeValueInput.Kind.Number))
    assert(forwardPath.roles.nonEmpty)
  }

  test("repeat counts must be whole numbers and literals keep their typed meaning") {
    val program = programWith(repeat)
    val count = statement(0) / ExpressionInSequence(0)
    assertEquals(BeValueInput.update(program, count, "2.5").left.map(_.expected), Left(BeValueInput.Kind.Integer))
    assertEquals(BeValueInput.update(program, count, "2*3").map(BeProgramEditing.find(_, statement(0)).collect { case r: BeRepeatNr => r.fixedAmount }),
      Right(Some(Some(6))))
    assertEquals(BeValueInput.parse(program, BeDataType.Boolean, " TRUE "), Right(BeUseValue(BeDataValueLiteral("true"), None)))
    assertEquals(BeValueInput.parse(program, BeDataType.Boolean, "3 < 5"), Right(BeUseValue(BeDataValueLiteral("true"), None)))
    assertEquals(BeValueInput.parse(program, BeDataType.Date, "2024-05-01"), Right(BeUseValue(BeDataValueLiteral("2024-05-01"), None)))
    assertEquals(BeValueInput.parse(program, BeDataType.Date, "11.10.1999").left.map(_.expected), Left(BeValueInput.Kind.Date))
    assertEquals(BeValueInput.parse(program, BeDataType.String, "  spaced words "), Right(BeUseValue(BeDataValueLiteral("  spaced words "), None)))
    // Untyped positions (e.g. calls to functions without annotations) still compute expressions.
    assertEquals(BeValueInput.parse(program, BeDataType.AnyType, "3+5"), Right(BeUseValue(BeDataValueLiteral("8"), None)))
    assert(BeValueInput.parse(program, BeDataType.AnyType, "add(3+5, 3)").exists(_.isInstanceOf[BeFunctionCall]))
  }

  test("expression states are edited in the block representation") {
    val state = ProgrammingStateRaw(Python, "forward(10)\n").toBeExpression
    assertEquals(state.associatedLanguage, BlockDisplay)
    val converted = ProgrammingStateRaw(Python, "forward(10)\n").convertToLanguage(BlockDisplay).finalState
    assertEquals(converted, Some(state))
    assert(state.convertToLanguage(Python).finalState.get.toString.contains("forward(10)"))
  }
}
