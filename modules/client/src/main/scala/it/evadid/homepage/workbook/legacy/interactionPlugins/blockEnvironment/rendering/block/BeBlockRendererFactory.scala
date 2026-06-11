package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.block

import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.control.{BeBlockIfElse, BeBlockSequence, BeBlockStarter, BeBlockWhile}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.data.{BeBlockDefineVariable, BeBlockUseValue}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.define.BeBlockDefineSingleReturnFunction
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.other.{BeBlockComment, BeBlockUnparsable, BeBlockUnsupported}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.use.{BeBlockAssignValue, BeBlockCallSingleReturnFunction}
import todomove.datastructures.core.vm.code.BeExpression
import todomove.datastructures.core.vm.code.controlStructures.{BeIfElse, BeSequence, BeWhile}
import todomove.datastructures.core.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import todomove.datastructures.core.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import todomove.datastructures.core.vm.code.others.BeStartProgram
import todomove.datastructures.core.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}

/**
 * Client-side renderer dispatch for block-environment expressions.
 *
 * The core BeExpression model must not know about BeBlock or SVG rendering classes. This factory is the
 * compatibility seam that replaces the former BeExpressionIO.toBlock() hook while the concrete BeBlock classes
 * are migrated into renderer classes.
 */
object BeBlockRendererFactory {

  def blockFor(expression: BeExpression): BeBlock = expression match {
    case sequence: BeSequence => BeBlockSequence(sequence)
    case ifElse: BeIfElse => BeBlockIfElse(ifElse)
    case whileExpr: BeWhile => BeBlockWhile(whileExpr)
    case function: BeFunctionCall => BeBlockCallSingleReturnFunction(function)
    case assignment: BeAssignVariable => BeBlockAssignValue(assignment.target, assignment.value)
    case value: BeUseValue => BeBlockUseValue(value)
    case variable: BeDefineVariable => BeBlockDefineVariable(variable)
    case functionDefinition: BeDefineFunction => BeBlockDefineSingleReturnFunction(functionDefinition)
    case _: BeStartProgram => BeBlockStarter()
    case unparsable: BeExpressionUnparsable => BeBlockUnparsable(unparsable)
    case unsupported: BeExpressionUnsupported => BeBlockUnsupported(unsupported)
    case comment: BeSingleLineComment => BeBlockComment(comment)
    case unsupported => BeBlockUnsupported(BeExpressionUnsupported(unsupported.getClass.getSimpleName))
  }
}
