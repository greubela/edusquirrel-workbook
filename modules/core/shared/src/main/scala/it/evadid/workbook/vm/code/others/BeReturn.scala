package it.evadid.workbook.vm.code.others

import it.evadid.workbook.vm.types.BeChildRole.ExpressionInSequence
import it.evadid.core.datastructures.language.AppLanguage.*

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.vm.code.BeExpression
import it.evadid.workbook.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.workbook.vm.io.BeExpressionIO
import it.evadid.workbook.vm.types.{BeChildPosition, BeChildRole, BeScope}

case class BeReturn(value: Option[BeExpression]) extends BeExpression {

  override def expressionIO: BeExpressionIO = new BeExpressionIO {
    override def toStringInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String = {
      val valueString = value.map(_.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable).replaceAll("\n", " "))
      val base = valueString match {
        case Some(text) if text.nonEmpty => s"return $text"
        case _ => "return"
      }

      programmingLanguage match {
        case Python => base
        case Java | JavaScript | Rust | Cpp => if (base.endsWith(";")) base else base + ";"
        case _ => base
      }
    }
  }

  override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] =
    value.map(expr => BeExpressionReference(BeChildPosition(ExpressionInSequence(0), parentScope), expr)).toList


  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = {
    val replacement = newChildren.collectFirst { case (ExpressionInSequence(_), expr) => expr }
    replacement.map(expr => copy(value = Some(expr))).getOrElse(this)
  }
}
