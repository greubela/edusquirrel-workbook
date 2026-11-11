package it.evadid.vm.analysis.feedback

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.vm.code.usage.BeUseValue
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral, BeUseValueReference}
import munit.FunSuite

final class VmStaticRulesSpec extends FunSuite {

  private def mkVar(name: String): BeDefineVariable = {
    val nameMap: LanguageMap[HumanLanguage] =
      LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(AppLanguage.English -> name))
    BeDefineVariable(BeEntityName.fromMapInCodeNotation(nameMap), BeDataType.AnyType)
  }

  test("VM_EMPTY_PROGRAM rule should fail for completely empty program") {
    val prog    = BeStartProgram()
    val results = VmStaticRules.runAll(prog)

    val emptyRuleOpt = results.find(_.id == "VM_EMPTY_PROGRAM")
    assert(emptyRuleOpt.nonEmpty, "VM_EMPTY_PROGRAM rule should be present.")
    assert(!emptyRuleOpt.get.passed, "VM_EMPTY_PROGRAM should not pass for empty program.")
  }

  test("VM_UNUSED_VARIABLES should detect unused variable") {
    val v    = mkVar("x")
    val seq  = BeSequence.optionalBody(List(v))
    val prog = BeStartProgram(seq)

    val results = VmStaticRules.runAll(prog)
    val ruleOpt = results.find(_.id == "VM_UNUSED_VARIABLES").getOrElse(
      fail("Expected VM_UNUSED_VARIABLES rule.")
    )

    assert(!ruleOpt.passed, "VM_UNUSED_VARIABLES should fail when variable is never used.")
  }

  test("VM_UNUSED_VARIABLES should pass when variable is used") {
    val v       = mkVar("x")
    val useExpr = BeUseValue(BeUseValueReference(v), Some(v))
    val seq     = BeSequence.optionalBody(List(v, useExpr))
    val prog    = BeStartProgram(seq)

    val results = VmStaticRules.runAll(prog)
    val ruleOpt = results.find(_.id == "VM_UNUSED_VARIABLES").getOrElse(
      fail("Expected VM_UNUSED_VARIABLES rule.")
    )

    assert(ruleOpt.passed, "VM_UNUSED_VARIABLES should pass when variable is used at least once.")
  }
  test("statements after return are unreachable but a final return is valid") {
    val variable = mkVar("unreachable")
    val unreachable = BeStartProgram(BeSequence.optionalBody(List(BeReturn(None), variable)))
    val warning = VmStaticRules.runAll(unreachable, English).find(_.id == "VM_UNREACHABLE_AFTER_RETURN")
      .getOrElse(fail("Expected unreachable-code warning."))
    assert(!warning.passed)
    assertEquals(warning.severity, RuleSeverity.Warning)

    val finalReturn = BeStartProgram(BeSequence.optionalBody(List(variable, BeReturn(None))))
    val rules = VmStaticRules.runAll(finalReturn, English)
    assert(!rules.exists(_.id == "VM_UNREACHABLE_AFTER_RETURN"))
    assert(rules.exists(rule => rule.id == "VM_NO_UNREACHABLE_AFTER_RETURN" && rule.passed))
  }

}
