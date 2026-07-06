package it.evadid.vm.code.defining

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.code.BeDefineStructure
import it.evadid.vm.io.BeExpressionIO
import it.evadid.vm.naming.{BeEntityName, CodeRepresentationConfig}
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.BeDataType

case class BeDefineVariable(
                             name: BeEntityName,
                             variableType: BeDataType
                           ) extends BeDefineStructure {

  override def expressionIO: BeExpressionIO = new BeExpressionIO() {

    override def toStringWithConfig(config: CodeRepresentationConfig): String = {
      import config.{humanLanguage, programmingLanguage}
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

  override def staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {


    override def hasSideEffects: Boolean = true
  }


  override val toString: String = "BeDefineVariable(" + name.toString + ": " + staticInformationExpression.staticType.toString + ")"


}

