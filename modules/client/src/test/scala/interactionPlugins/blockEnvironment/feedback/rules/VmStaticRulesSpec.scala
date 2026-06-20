package interactionPlugins.blockEnvironment.feedback.rules

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.rules.VmStaticRules
import todomove.datastructures.core.vm.code.controlStructures.BeSequence
import todomove.datastructures.core.vm.code.defining.BeDefineVariable
import todomove.datastructures.core.vm.code.others.BeStartProgram
import todomove.datastructures.core.vm.code.usage.BeUseValue
import todomove.datastructures.core.vm.types.{BeDataType, BeDataValueLiteral, BeUseValueReference}
import munit.FunSuite

final class VmStaticRulesSpec extends FunSuite {

  private def mkVar(name: String): BeDefineVariable = {
    val nameMap: LanguageMap[HumanLanguage] =
      LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(AppLanguage.English -> name))
    BeDefineVariable(nameMap, BeDataType.AnyType)
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
}
