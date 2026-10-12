package it.evadid.vm.types

import it.evadid.core.datastructures.language.AppLanguage.{English, Java, Python}
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.vm.naming.BeEntityName
import munit.FunSuite
import upickle.default.{read, write}

class VmTypesPackageSpec extends FunSuite {
  private val variable = BeDefineVariable(BeEntityName.fromUniversalNameInParts("counter"), BeDataType.Int)

  test("atomic data types validate literals and format language-specific values") {
    val valid = List(BeDataType.Numeric -> " -1.25e2 ", BeDataType.Int -> " -42 ",
      BeDataType.Boolean -> " TRUE ", BeDataType.Date -> "2026-10-07", BeDataType.String -> "")
    valid.foreach((t, literal) => assert(t.isValidLiteral(literal)))
    List(BeDataType.Numeric -> "NaN", BeDataType.Int -> "1.5", BeDataType.Boolean -> "yes",
      BeDataType.Date -> "7/10/2026", BeDataType.Unit -> "None", BeDataType.Error -> "error")
      .foreach((t, literal) => assert(!t.isValidLiteral(literal)))
    assertEquals(BeDataType.Int.formatTypeForDisplay.getInLanguage(Python), "int")
    assertEquals(BeDataType.Numeric.formatTypeForDisplay.getInLanguage(Java), "double")
    assertEquals(BeDataType.String.formatValueForDisplay("hello").getInLanguage(Python), "\"hello\"")
    assertEquals(BeDataType.String.formatValueForDisplay(" 'hello' ").getInLanguage(Python), "'hello'")
    assertEquals(BeDataType.Numeric.formatValueForDisplay(" 1.50 ").getInLanguage(Python), "1.5")
    assertEquals(BeDataType.Numeric.formatValueForDisplay("invalid").getInLanguage(Python), "invalid")
    val custom = BeDataType.BeDataTypeAtomic(BeDataType.Int.formatTypeForDisplay,
      s => BeDataType.String.formatValueForDisplay(s), _ == "ok")
    assert(custom.isValidLiteral("ok"))
    assert(!custom.isValidLiteral("other"))
  }

  test("assignment results distinguish same type, allowed implicit casts and rejection") {
    val same = BeDataType.Int.canTakeValuesFrom(BeDataType.Int)
    assertEquals(same, AssigningPossibleWithSameType(BeDataType.Int))
    assert(same.possibleWithoutSyntaxErrors)
    val cast = BeDataType.Numeric.canTakeValuesFrom(BeDataType.Int)
    assertEquals(cast, AssigningPossibleWithImplicitCast(BeDataType.Numeric))
    assert(cast.possibleWithoutSyntaxErrors)
    val rejected = BeDataType.Int.canTakeValuesFrom(BeDataType.String)
    assertEquals(rejected, AssigningNotPossible())
    assert(!rejected.possibleWithoutSyntaxErrors)
    assertEquals(rejected.resultingType, BeDataType.Error)
    assertEquals(BeDataType.Int.canTakeValuesFrom(BeDataType.AnyType), AssigningPossibleWithImplicitCast(BeDataType.Int))
    assert(!BeDataType.AnyType.isValidLiteral("42"))
    assertEquals(BeDataType.AnyType.formatTypeForDisplay.getInLanguage(Python), "Any")
    assertEquals(BeDataType.AnyType.formatValueForDisplay("x").getInLanguage(Python), "x")
    assert(BeDataType.AnyType.canTakeValuesFrom(BeDataType.Boolean).possibleWithoutSyntaxErrors)
  }

  test("union types intersect, format deterministically, validate and restrict assignments") {
    val union = BeDataType.BeUnionAllowedTypes(Set(BeDataType.Int, BeDataType.Boolean))
    assertEquals(union.formatTypeForDisplay.getInLanguage(Python), "bool|int")
    assert(union.isValidLiteral("17"))
    assert(union.isValidLiteral("false"))
    assert(!union.isValidLiteral("word"))
    assert(union.canTakeValuesFrom(BeDataType.Int).possibleWithoutSyntaxErrors)
    assert(!union.canTakeValuesFrom(BeDataType.String).possibleWithoutSyntaxErrors)
    assert(BeDataType.Int.canTakeValuesFrom(union).possibleWithoutSyntaxErrors)
    assert(!BeDataType.Date.canTakeValuesFrom(union).possibleWithoutSyntaxErrors)
    val other = BeDataType.BeUnionAllowedTypes(Set(BeDataType.Int, BeDataType.Date))
    assertEquals(BeDataType.allowedTypesIntersection(union, other), Some(BeDataType.Int))
    assertEquals(BeDataType.allowedTypesIntersection(union, union), Some(union))
    assertEquals(BeDataType.allowedTypesIntersection(union, BeDataType.BeUnionAllowedTypes(Set.empty)), None)
    assertEquals(BeDataType.typeIntersection(BeDataType.AnyType, union), Some(union))
    assertEquals(BeDataType.typeIntersection(union, BeDataType.AnyType), Some(union))
    assertEquals(BeDataType.BeUnionAllowedTypes(Set.empty).formatTypeForDisplay.getInLanguage(Python), "")
  }

  test("serializable atomic type resolves the exported representation") {
    assertEquals(BeDataType.allAtomic.size, 7)
    BeDataType.allAtomic.foreach { atomic =>
      assertEquals(atomic.toSerializableSubType.toTypedMainType, atomic)
      assertEquals(read[BeDataType](write[BeDataType](atomic)), atomic)
    }
    assertEquals(BeDataType.String.toSerializableSubType, BeDataType.BeSerializableAtomicType("String"))
    assertEquals(BeDataType.BeSerializableAtomicType("BeDataTypeAtomic").toTypedMainType, BeDataType.String)
    intercept[NoSuchElementException](BeDataType.BeSerializableAtomicType("unknown").toTypedMainType)
  }

  test("data type codecs round-trip empty, nested and unrestricted unions") {
    val nested = BeDataType.BeUnionAllowedTypes(Set(BeDataType.Int, BeDataType.AnyType,
      BeDataType.BeUnionAllowedTypes(Set(BeDataType.String, BeDataType.Boolean))))
    val values: List[BeDataType] = List(BeDataType.AnyType, BeDataType.BeUnionAllowedTypes(Set.empty), nested)
    values.foreach(t => assertEquals(read[BeDataType](write(t)), t))
    assertEquals(read[BeDataType.BeUnionType](write[BeDataType.BeUnionType](nested)), nested)
    intercept[Exception](read[BeDataType]("42"))
  }

  test("all literal value categories infer types and preserve original display text") {
    List("42" -> BeDataType.Numeric, " -1.2e3 " -> BeDataType.Numeric,
      " FALSE " -> BeDataType.Boolean, "2026-10-07" -> BeDataType.Date,
      "hello" -> BeDataType.String, "" -> BeDataType.String).foreach { (text, expected) =>
      val literal = BeDataValueLiteral(text)
      assertEquals(literal.currentType, expected)
      assertEquals(literal.displayAsString, text)
      assertEquals(read[BeDataValue](write[BeDataValue](literal)), literal)
    }
    val unit = BeDataValueUnit()
    assertEquals(unit.currentType, BeDataType.Unit)
    assertEquals(unit.displayAsString, "")
    assertEquals(read[BeDataValue](write[BeDataValue](unit)), unit)
    val reference = BeUseValueReference(variable)
    assertEquals(reference.currentType, BeDataType.Int)
    assertEquals(read[BeDataValue](write[BeDataValue](reference)), reference)
  }

  test("every numbered child role increments and fixed roles remain unchanged") {
    import BeChildRole.*
    val numbered: List[(BeChildRole, BeChildRole)] = List(BodySequence(2) -> BodySequence(3),
      ExpressionInSequence(2) -> ExpressionInSequence(3), AttributeInClass(2) -> AttributeInClass(3),
      MethodInClass(2) -> MethodInClass(3), FunctionParameter(2) -> FunctionParameter(3), ReturnValue(2) -> ReturnValue(3))
    numbered.foreach((before, after) => {
      assertEquals(before.withIncrementedNrOrThis, after)
      assertEquals(read[BeChildRole](write[BeChildRole](before)), before)
    })
    val fixed = List(NoRole, ConditionInControlStructure, ValueInAssignment,
      ValueForVariable(variable), RecentlyInsertedInto(BodySequence(0)))
    fixed.foreach(role => {
      assertEquals(role.withIncrementedNrOrThis, role)
      assertEquals(read[BeChildRole](write[BeChildRole](role)), role)
    })
    val info = BeChildInfo(BodySequence(1), BeScope.GlobalScope())
    assertEquals(info.toString, "BodySequence(1)")
    assertEquals(read[BeChildInfo](write(info)), info)
  }

  test("scope classes retain ancestors from nearest to farthest") {
    val global = BeScope.GlobalScope()
    val body = BeSequence.optionalBody(Nil)
    val cls = BeDefineClass(BeEntityName.fromUniversalNameInParts("Example"), Nil, Nil)
    val classScope = BeScope.InClassScope(cls, global)
    val func = BeDefineFunction(Nil, None, body, BeDefineFunction.lambdaFunctionInfo())
    val functionScope = BeScope.InFunctionScope(func, classScope)
    val sequenceScope = BeScope.InSequenceScope(body, functionScope)
    assertEquals(global.parentScopes, Nil)
    assertEquals(classScope.parentScopes, List(global))
    assertEquals(functionScope.parentScopes, List(classScope, global))
    assertEquals(sequenceScope.parentScopes, List(functionScope, classScope, global))
    assert(sequenceScope.isSubScope(global))
    assert(sequenceScope.isSubScope(functionScope))
    assert(!global.isSubScope(sequenceScope))
    assert(!sequenceScope.isSubScope(sequenceScope))
    List(global, classScope, functionScope, sequenceScope).foreach(scope =>
      assertEquals(read[BeScope](write[BeScope](scope)), scope))
  }

  test("BeInfo produces errors and warnings only when the assignment requires them") {
    assertEquals(BeInfo.typeMismatchInfo(" condition ", BeDataType.Int, BeDataType.Int), None)
    val warning = BeInfo.typeMismatchInfo("condition", BeDataType.Numeric, BeDataType.Int).get
    assertEquals(warning.infoType, BeInfo.WarningType.ImplicitTypeCast)
    assert(warning.message.getInLanguage(English).startsWith("Implicit Cast:"))
    val error = BeInfo.typeMismatchInfo(" condition ", BeDataType.Int, BeDataType.String).get
    assertEquals(error.infoType, BeInfo.SyntaxError.TypeMismatch)
    assert(error.message.getInLanguage(English).startsWith("condition must be able"))
    assertEquals(BeInfo.SyntaxError.values.length, 6)
    assertEquals(BeInfo.RuntimeError.values.toList, List(BeInfo.RuntimeError.DivideByZero, BeInfo.RuntimeError.InvalidReference))
  }
}
