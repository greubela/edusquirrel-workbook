package interactionPlugins.blockEnvironment.programming.blocks.function

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.model.language.LanguageMap
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.blocks.displayUtility.*
import interactionPlugins.blockEnvironment.programming.blocks.traits.*
import interactionPlugins.blockEnvironment.programming.connection.{BeValueRole, FunctionParameter, FunctionReturnValue}
import interactionPlugins.blockEnvironment.programming.rendering.*
import interactionPlugins.blockEnvironment.programming.{BeBlockContext, BeDataType}
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.rendering.BeShape.FunctionCallShape

import scala.collection.mutable

case class BeBlockCallUnitFunction(
                                    function: BeFunction,
                                  ) extends BeBlockParent with BeBlockLogic with BeBlockStructureUsing {


  private def roleToBlock(curRole: BeValueRole, existingValueChildren: Map[BeValueRole, BeBlockValue]): BeBlock = {
    if (existingValueChildren.contains(curRole)) {
      existingValueChildren(curRole)
    } else curRole match {
      case FunctionParameter(index, parType) => BeBlockOptionalValue(Set(parType))
      case FunctionReturnValue(index, parType) => BeBlockOptionalValue(Set(parType))
    }
  }

  override def nodeInsertionsForDisplay(existingChildren: List[BeBlock], existingValueChildrenWithPosition: Map[BeValueRole, (BeBlockValue, Int)]): List[(Int, BeBlock)] = {
    val functionNameDisplay = BeBlockDisplayText(function.name)
    val nonExistingParameter = function.parameter.filter(!existingValueChildrenWithPosition.contains(_))
    val parameterDummiesToInsert = nonExistingParameter.map(curPar => (curPar.nr, BeBlockOptionalValue(Set(curPar.evaluatesTo))))

    parameterDummiesToInsert ++ List((0, functionNameDisplay))
  }

  def displayShape: BeShape = FunctionCallShape
  def parentDisplay: BeParentDisplay = HBoxParent(true, new Dimension[Double](20, 5))


}
