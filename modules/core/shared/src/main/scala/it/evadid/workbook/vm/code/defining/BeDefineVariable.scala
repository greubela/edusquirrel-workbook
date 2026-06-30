package it.evadid.workbook.vm.code.defining

import it.evadid.core.datastructures.language.AppLanguage.{Cpp, Java, Python}
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.vm.code.BeDefineStructure
import it.evadid.workbook.vm.code.tree.BeExpressionNode
import it.evadid.workbook.vm.io.BeExpressionIO
import it.evadid.workbook.vm.naming.{BeEntityName, CodeRepresentationConfig}
import it.evadid.workbook.vm.static.BeExpressionStaticInformation
import it.evadid.workbook.vm.types.BeDataType

case class BeDefineVariable(
    name: BeEntityName,
    variableType: BeDataType
) extends BeDefineStructure {

  override def expressionIO: BeExpressionIO = new BeExpressionIO() {
    
    override def toStringWithConfig(config: CodeRepresentationConfig): String = {
      import config.{programmingLanguage, humanLanguage, skipUnparsable}
      val baseName = name.getNameIn(humanLanguage, config.namingStyle)
      val typeHint = variableType.formatTypeForDisplay.getInLanguage(programmingLanguage).trim
      programmingLanguage match {
        case Python =>
          if (typeHint.nonEmpty) s"$baseName: $typeHint" else baseName
        case Java =>
          val javaType = if (typeHint.nonEmpty) typeHint else "Object"
          if (typeHint.nonEmpty) s"$javaType $baseName" else baseName
        case Cpp =>
          val cppType = if (typeHint.nonEmpty) typeHint else "auto"
          if (typeHint.nonEmpty) s"$cppType $baseName" else baseName
        case _ => baseName
      }
    }

    
  }

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation(){


    override def hasSideEffects: Boolean = true 
  }
    
  

  override val toString: String = "BeDefineVariable(" + name.toString + ": " + staticInformationExpression.staticType.toString + ")"
  

}

