package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.data

import it.evadid.workbook.vm.types.BeDataType
import todomove.webElementsOld.webElements.svg.shapes.BeShape
import todomove.webElementsOld.webElements.svg.shapes.BeShape.BeShapeContainerable
import todomove.webElementsOld.webElements.svg.shapes.datatypes.{BooleanShape, DateShape, DuckShape, NumericShape, StringShape, UnitShape}

object BeDataTypeShapeAdapter:
  def shapeFor(dataType: BeDataType): BeShape = dataType match
    case BeDataType.String => StringShape
    case BeDataType.Numeric => NumericShape
    case BeDataType.Int => NumericShape
    case BeDataType.Boolean => BooleanShape
    case BeDataType.Date => DateShape
    case BeDataType.Unit => UnitShape
    case BeDataType.Error => NumericShape
    case BeDataType.AnyType => DuckShape
    case _: BeDataType.BeUnionAllowedTypes => DuckShape
    case _: BeDataType.BeDataTypeAtomic => DuckShape

  def containerShapeFor(dataType: BeDataType): Option[BeShapeContainerable] = shapeFor(dataType) match
    case containerable: BeShapeContainerable => Some(containerable)
    case _ => None
