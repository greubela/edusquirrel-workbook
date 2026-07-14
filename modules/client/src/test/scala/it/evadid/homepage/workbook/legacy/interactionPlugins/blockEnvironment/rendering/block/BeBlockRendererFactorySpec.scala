package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.block

import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.control.BeBlockSequence
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.other.BeBlockUnsupported
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.code.errors.BeExpressionUnsupported
import it.evadid.vm.code.usage.{BeAssignVariable, BeUseValue}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}
import munit.FunSuite

class BeBlockRendererFactorySpec extends FunSuite {

  private val variable = BeDefineVariable(BeEntityName.fromUniversalNameInParts("x"), BeDataType.Int)
  private val literal = BeUseValue(BeDataValueLiteral("1"), Some(variable))

  test("blockFor dispatches core expressions to client-side BeBlock renderers") {
    assert(BeBlockRendererFactory.blockFor(BeSequence.optionalBody(List(literal))).isInstanceOf[BeBlockSequence])
    assert(BeBlockRendererFactory.blockFor(BeAssignVariable(variable, literal)).getClass.getName.contains("BeBlockAssignValueFromExpression"))
    assert(BeBlockRendererFactory.blockFor(BeExpressionUnsupported("unknown")).isInstanceOf[BeBlockUnsupported])
  }
}
