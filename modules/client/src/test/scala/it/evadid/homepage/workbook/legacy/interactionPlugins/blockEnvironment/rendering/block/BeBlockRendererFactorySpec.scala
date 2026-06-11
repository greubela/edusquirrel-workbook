package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.block

import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.control.BeBlockSequence
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.other.BeBlockUnsupported
import munit.FunSuite
import todomove.datastructures.core.vm.code.controlStructures.BeSequence
import todomove.datastructures.core.vm.code.defining.BeDefineVariable
import todomove.datastructures.core.vm.code.errors.BeExpressionUnsupported
import todomove.datastructures.core.vm.code.usage.{BeAssignVariable, BeUseValue}
import todomove.datastructures.core.vm.types.{BeDataType, BeDataValueLiteral}

class BeBlockRendererFactorySpec extends FunSuite {

  private val variable = BeDefineVariable(LanguageMap.universalMap("x"), BeDataType.Int)
  private val literal = BeUseValue(BeDataValueLiteral("1"), Some(variable))

  test("blockFor dispatches core expressions to client-side BeBlock renderers") {
    assert(BeBlockRendererFactory.blockFor(BeSequence.optionalBody(List(literal))).isInstanceOf[BeBlockSequence])
    assert(BeBlockRendererFactory.blockFor(BeAssignVariable(variable, literal)).getClass.getName.contains("BeBlockAssignValueFromExpression"))
    assert(BeBlockRendererFactory.blockFor(BeExpressionUnsupported("unknown")).isInstanceOf[BeBlockUnsupported])
  }
}
