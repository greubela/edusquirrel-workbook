package it.evadid.vm.code.controlStructures

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.code.tree.BeExtensionPoint
import it.evadid.vm.code.usage.BeUseValue
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.*
import munit.FunSuite
import upickle.default.{read, write}

class ControlStructuresPackageSpec extends FunSuite {
  private def replace(expression: BeExpression, children: Map[BeChildRole, BeExpression]): BeExpression =
    expression.structureInfo.withReplacedChildren(children)

  private val global = BeScope.GlobalScope()
  private val info = BeChildInfo(BeChildRole.NoRole, global)
  private def literal(text: String): BeExpression = BeUseValue(BeDataValueLiteral(text), None)
  private val body = BeSequence.optionalBody(List(literal("1")))
  private val otherBody = BeSequence.optionalBody(List(literal("2")))
  private val condition = BeSequence.conditionalBody(List(literal("true")))

  test("BeSequenceInfo and factories encode unrestricted and conditional bodies") {
    assertEquals(body.sequenceInfo, BeSequenceInfo(None, None))
    assertEquals(condition.sequenceInfo, BeSequenceInfo(Some(BeDataType.Boolean), Some(1)))
    val seqInfo = BeSequenceInfo(Some(BeDataType.Boolean), Some(3))
    assertEquals(read[BeSequenceInfo](write(seqInfo)), seqInfo)
  }

  test("BeSequence infers its last value, supports empty bodies and generates extension points") {
    assertEquals(body.allPossibleBodies, body.body)
    assertEquals(body.staticInformationExpression.staticType, BeDataType.Numeric)
    assertEquals(body.staticInformationExpression.staticValue, Some(BeDataValueLiteral("1")))
    assertEquals(BeSequence.optionalBody(Nil).staticInformationExpression.staticType, BeDataType.Error)
    assertEquals(BeSequence.optionalBody(Nil).staticInformationExpression.staticValue, None)
    assertEquals(condition.staticInformationExpression.staticType, BeDataType.Boolean)
    val children = body.structureInfo.getChildrenAndExtension(global)
    assertEquals(children.count(_.isInstanceOf[BeExtensionPoint]), 2)
    val references = body.structureInfo.getChildrenAsReference(global)
    assertEquals(references.map(_.expr), body.body)
    assertEquals(references.map(_.childInfo.myRoleInParent), Seq(BeChildRole.ExpressionInSequence(0)))
    assertEquals(references.head.childInfo.myScope, BeScope.InSequenceScope(body, global))
    assert(!condition.structureInfo.getChildrenAndExtension(global).exists(_.isInstanceOf[BeExtensionPoint]))
    assertEquals(body.structureInfo.toJavaStyleLines(info).size, 1)
  }

  test("BeSequence replaces a complete body by roles and ignores unrelated roles") {
    val sequence = BeSequence.optionalBody(List(literal("1"), literal("2")))
    val replaced = replace(sequence, Map(
      BeChildRole.ExpressionInSequence(0) -> literal("3"),
      BeChildRole.ExpressionInSequence(1) -> literal("4"),
      BeChildRole.BodySequence(0) -> otherBody))
    assertEquals(replaced, BeSequence.optionalBody(List(literal("3"), literal("4"))))
    assertEquals(replace(BeSequence.optionalBody(Nil), Map.empty), BeSequence.optionalBody(Nil))
  }

  test("BeIfElse exposes each scoped child and replaces only sequence-valued roles") {
    val branch = BeIfElse(condition, body, otherBody)
    assertEquals(branch.allPossibleBodies, List(body, otherBody))
    assertEquals(branch.structureInfo.getChildrenAsReference(global).map(_.expr), Seq(condition, body, otherBody))
    assertEquals(branch.structureInfo.getChildrenAsReference(global).map(_.childInfo.myRoleInParent),
      Seq(BeChildRole.ConditionInControlStructure, BeChildRole.BodySequence(0), BeChildRole.BodySequence(1)))
    assertEquals(replace(branch, Map(BeChildRole.BodySequence(1) -> body)), branch.copy(elseBody = body))
    assertEquals(replace(branch, Map(BeChildRole.ConditionInControlStructure -> literal("false"))), branch)
    assertEquals(branch.structureInfo.toJavaStyleLines(info).size, 5)
    assertEquals(branch.copy(elseBody = BeSequence.optionalBody(Nil)).structureInfo.toJavaStyleLines(info).size, 3)
    assertEquals(branch.staticInformationExpression.syntaxErrors, Seq.empty[BeInfo])
    assertEquals(branch.copy(condition = body).staticInformationExpression.syntaxErrors.map(_.infoType), Seq(BeInfo.SyntaxError.TypeMismatch))
  }

  test("BeWhile preserves unrelated children and diagnoses non-Boolean conditions") {
    val loop = BeWhile(condition, body)
    assertEquals(loop.allPossibleBodies, List(body))
    assertEquals(loop.structureInfo.getChildrenAsReference(global).map(_.expr), Seq(condition, body))
    assertEquals(replace(loop, Map(BeChildRole.BodySequence(0) -> otherBody)), loop.copy(body = otherBody))
    assertEquals(replace(loop, Map(BeChildRole.ConditionInControlStructure -> otherBody)), loop.copy(condition = otherBody))
    assertEquals(replace(loop, Map(BeChildRole.BodySequence(0) -> literal("7"))), loop)
    assertEquals(loop.structureInfo.toJavaStyleLines(info).size, 3)
    assertEquals(loop.staticInformationExpression.syntaxErrors, Seq.empty[BeInfo])
    assertEquals(loop.copy(condition = body).staticInformationExpression.syntaxErrors.map(_.infoType), Seq(BeInfo.SyntaxError.TypeMismatch))
  }

  test("BeRepeatNr accepts zero and positive counts and reports negative counts") {
    List(0, 1, 10).foreach(n => assertEquals(BeRepeatNr(n, body).staticInformationExpression.syntaxErrors, Seq.empty[BeInfo]))
    val loop = BeRepeatNr(-1, body)
    assertEquals(loop.staticInformationExpression.syntaxErrors.map(_.infoType), Seq(BeInfo.SyntaxError.InvalidLiteralValue))
    assertEquals(loop.allPossibleBodies, List(body))
    assertEquals(loop.structureInfo.getChildrenAsReference(global).map(_.expr), Seq(body))
    assertEquals(replace(loop, Map(BeChildRole.BodySequence(0) -> otherBody)), loop.copy(body = otherBody))
    assertEquals(replace(loop, Map(BeChildRole.BodySequence(0) -> literal("7"))), loop)
    assertEquals(loop.structureInfo.toJavaStyleLines(info).size, 3)
  }

  test("BeFor exposes start, end and scoped body and supports independent replacements") {
    val variable = BeDefineVariable(BeEntityName.fromUniversalNameInParts("i"), BeDataType.Int)
    val loop = BeFor(variable, literal("1"), literal("3"), body)
    val children = loop.structureInfo.getChildrenAsReference(global)
    assertEquals(children.map(_.expr), Seq(loop.start, loop.end, body))
    assertEquals(children.map(_.childInfo.myRoleInParent), Seq(BeChildRole.ExpressionInSequence(0), BeChildRole.ExpressionInSequence(1), BeChildRole.BodySequence(0)))
    assertEquals(children.last.childInfo.myScope, BeScope.InSequenceScope(body, global))
    assertEquals(loop.allPossibleBodies, List(body))
    assertEquals(replace(loop, Map(BeChildRole.ExpressionInSequence(0) -> literal("2"))), loop.copy(start = literal("2")))
    assertEquals(replace(loop, Map(BeChildRole.ExpressionInSequence(1) -> literal("4"), BeChildRole.BodySequence(0) -> otherBody)), loop.copy(end = literal("4"), body = otherBody))
    assertEquals(replace(loop, Map(BeChildRole.BodySequence(0) -> literal("7"))), loop)
    assertEquals(loop.structureInfo.toJavaStyleLines(info).size, 3)
  }

  test("every control structure round-trips through the shared expression codec") {
    val variable = BeDefineVariable(BeEntityName.fromUniversalNameInParts("i"), BeDataType.Int)
    val expressions: List[BeExpression] = List(body, condition, BeIfElse(condition, body, otherBody),
      BeWhile(condition, body), BeRepeatNr(3, body), BeFor(variable, literal("1"), literal("3"), body))
    expressions.foreach(expression => assertEquals(read[BeExpression](write(expression)), expression))
  }
  test("partial and sparse sequence replacements preserve all unreplaced children") {
    val sequence = BeSequence.optionalBody(List(literal("1"), literal("2")))
    assertEquals(replace(sequence, Map(BeChildRole.ExpressionInSequence(1) -> literal("3"))),
      BeSequence.optionalBody(List(literal("1"), literal("3"))))
    assertEquals(replace(sequence, Map(BeChildRole.ExpressionInSequence(4) -> literal("5"))),
      BeSequence.optionalBody(List(literal("1"), literal("2"), literal("5"))))
    assertEquals(replace(sequence, Map(BeChildRole.BodySequence(0) -> otherBody)), sequence)
  }
  test("sequence extension roles use body indices and typed empty bodies remain extensible") {
    val extensions = body.structureInfo.getChildrenAndExtension(global).collect { case point: BeExtensionPoint => point }
    assertEquals(extensions.map(_.childInfo.myRoleInParent), Seq(BeChildRole.ExpressionInSequence(0), BeChildRole.ExpressionInSequence(1)))
    val empty = BeSequence(Nil, BeSequenceInfo(Some(BeDataType.Boolean)))
    assert(empty.structureInfo.getChildrenAndExtension(global).nonEmpty)
  }

}
