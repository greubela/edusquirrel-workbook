package it.evadid.vm.editing

import it.evadid.core.datastructures.language.AppLanguage.{BlockDisplay, Python}
import it.evadid.vm.BeProgram
import it.evadid.vm.code.controlStructures.{BeRepeatNr, BeWhile}
import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.vm.conversion.pythonexpression.PythonToExpression
import it.evadid.vm.types.BeChildRole.{BodySequence, ConditionInControlStructure, ExpressionInSequence, FunctionParameter}
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStateBeExpression, ProgrammingStateRaw}
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

  test("library templates are parsed statements with an editable loop") {
    assert(BeBlockLibrary.turtle.templates.exists(_.statement.isInstanceOf[BeWhile]))
    assertEquals(repeat.fixedAmount, Some(4))
    assertEquals(repeat.body.body, Nil)
  }

  test("inserting into the empty program produces convertible source") {
    val edited = BeProgramEditing.insert(BeProgram.empty, BeSequenceSlot(main, 0), forward).toOption.get
    assert(python(edited).contains("forward(100)"), python(edited))
    assertEquals(BeProgramEditing.find(edited, statement(0)), Some(forward))
  }

  test("slots interleave sequence positions with nested bodies in source order") {
    val program = Seq(forward, repeat, right).zipWithIndex.foldLeft(BeProgram.empty) { case (current, (block, index)) =>
      BeProgramEditing.insert(current, BeSequenceSlot(main, index), block).toOption.get
    }
    val loopBody = statement(1) / BodySequence(0)
    val slots = BeProgramEditing.slots(program)
    assertEquals(slots.filter(_.sequence == main).map(_.index), Seq(0, 1, 2, 3))
    assert(slots.indexOf(BeSequenceSlot(loopBody, 0)) > slots.indexOf(BeSequenceSlot(main, 1)))
    assert(slots.indexOf(BeSequenceSlot(loopBody, 0)) < slots.indexOf(BeSequenceSlot(main, 2)))
  }

  test("moving a statement into a later loop adjusts the loop's path after removal") {
    val program = Seq(forward, repeat).zipWithIndex.foldLeft(BeProgram.empty) { case (current, (block, index)) =>
      BeProgramEditing.insert(current, BeSequenceSlot(main, index), block).toOption.get
    }
    val moved = BeProgramEditing.move(program, statement(0), BeSequenceSlot(statement(1) / BodySequence(0), 0)).toOption.get
    val loop = BeProgramEditing.find(moved, statement(0)).collect { case loop: BeRepeatNr => loop }.get
    assertEquals(loop.body.body, Seq(forward))
    assertEquals(BeProgramEditing.find(moved, statement(1)), None)
  }

  test("moving within one sequence keeps the requested position") {
    val program = Seq(forward, right).zipWithIndex.foldLeft(BeProgram.empty) { case (current, (block, index)) =>
      BeProgramEditing.insert(current, BeSequenceSlot(main, index), block).toOption.get
    }
    val moved = BeProgramEditing.move(program, statement(0), BeSequenceSlot(main, 2)).toOption.get
    assertEquals(BeProgramEditing.find(moved, statement(0)), Some(right))
    assertEquals(BeProgramEditing.find(moved, statement(1)), Some(forward))
  }

  test("illegal edits leave a reason instead of a changed program") {
    val program = BeProgramEditing.insert(BeProgram.empty, BeSequenceSlot(main, 0), repeat).toOption.get
    assert(BeProgramEditing.move(program, statement(0), BeSequenceSlot(statement(0) / BodySequence(0), 0)).isLeft)
    assert(BeProgramEditing.insert(program, BeSequenceSlot(main, 5), forward).isLeft)
    val whileLoop = PythonToExpression.program("while True:\n    forward(1)\n")
    val condition = BeNodePath.root / BodySequence(0) / ExpressionInSequence(0) / ConditionInControlStructure
    assert(!BeProgramEditing.accepts(whileLoop, BeSequenceSlot(condition, 0), forward))
    assert(BeProgramEditing.remove(program, main).isLeft)
  }

  test("removing a statement deletes only that statement") {
    val program = BeProgramEditing.insert(BeProgram.empty, BeSequenceSlot(main, 0), forward).toOption.get
    assertEquals(BeProgramEditing.remove(program, statement(0)).map(python), Right(python(BeProgram.empty)))
  }

  test("literal input validates against the parameter type") {
    val program = BeProgramEditing.insert(BeProgram.empty, BeSequenceSlot(main, 0), forward).toOption.get
    val distance = statement(0) / FunctionParameter(0)
    assertEquals(BeLiteralInput.expectedType(program, distance), BeDataType.Numeric)
    assertEquals(BeLiteralInput.update(program, distance, "abc"), Left(BeLiteralInput.Rejected(BeLiteralInput.Kind.Number)))
    val edited = BeLiteralInput.update(program, distance, " 42.5 ").toOption.get
    assertEquals(BeLiteralInput.literalAt(edited, distance).map(_.value), Some(BeDataValueLiteral("42.5")))
    assert(python(edited).contains("forward(42.5)"), python(edited))
  }

  test("repeat counts must be whole numbers and booleans are normalized") {
    val program = BeProgramEditing.insert(BeProgram.empty, BeSequenceSlot(main, 0), repeat).toOption.get
    val count = statement(0) / ExpressionInSequence(0)
    assertEquals(BeLiteralInput.update(program, count, "2.5").left.map(_.expected), Left(BeLiteralInput.Kind.Integer))
    assertEquals(BeLiteralInput.update(program, count, "6").map(BeProgramEditing.find(_, statement(0)).collect { case r: BeRepeatNr => r.fixedAmount }),
      Right(Some(Some(6))))
    assertEquals(BeLiteralInput.parse(BeDataType.Boolean, " TRUE "), Right(BeDataValueLiteral("true")))
    assertEquals(BeLiteralInput.parse(BeDataType.Date, "11.10.1999").left.map(_.expected), Left(BeLiteralInput.Kind.Date))
    assert(BeLiteralInput.parse(BeDataType.String, "  spaced ").contains(BeDataValueLiteral("  spaced ")))
  }

  test("expression states are edited in the block representation") {
    val state = ProgrammingStateRaw(Python, "forward(10)\n").toBeExpression
    assertEquals(state.associatedLanguage, BlockDisplay)
    val converted = ProgrammingStateRaw(Python, "forward(10)\n").convertToLanguage(BlockDisplay).finalState
    assertEquals(converted, Some(state))
    assert(state.convertToLanguage(Python).finalState.get.toString.contains("forward(10)"))
  }
}
