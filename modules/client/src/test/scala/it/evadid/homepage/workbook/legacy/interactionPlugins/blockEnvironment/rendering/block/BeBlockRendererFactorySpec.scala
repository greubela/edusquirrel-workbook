package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.block

import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.control.BeBlockSequence
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.other.BeBlockUnsupported
import munit.FunSuite
import it.evadid.workbook.vm.code.controlStructures.BeSequence
import it.evadid.workbook.vm.code.defining.BeDefineVariable
import it.evadid.workbook.vm.code.errors.BeExpressionUnsupported
import it.evadid.workbook.vm.code.usage.{BeAssignVariable, BeUseValue}
import it.evadid.workbook.vm.types.{BeDataType, BeDataValueLiteral}

class BeBlockRendererFactorySpec extends FunSuite {

  private val variable = BeDefineVariable(LanguageMap.universalMap[HumanLanguage]("x"), BeDataType.Int)
  private val literal = BeUseValue(BeDataValueLiteral("1"), Some(variable))

  test("blockFor dispatches core expressions to client-side BeBlock renderers") {
    assert(BeBlockRendererFactory.blockFor(BeSequence.optionalBody(List(literal))).isInstanceOf[BeBlockSequence])
    assert(BeBlockRendererFactory.blockFor(BeAssignVariable(variable, literal)).getClass.getName.contains("BeBlockAssignValueFromExpression"))
    assert(BeBlockRendererFactory.blockFor(BeExpressionUnsupported("unknown")).isInstanceOf[BeBlockUnsupported])
  }
}
