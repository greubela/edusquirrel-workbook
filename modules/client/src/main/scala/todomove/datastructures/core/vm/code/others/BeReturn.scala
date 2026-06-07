package todomove.datastructures.core.vm.code.others

import todomove.datastructures.core.vm.types.BeChildRole.ExpressionInSequence
import it.evadid.core.datastructures.language.AppLanguage.*

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import todomove.datastructures.core.vm.code.BeExpression
import todomove.datastructures.core.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import todomove.datastructures.core.vm.io.BeExpressionIO
import todomove.datastructures.core.vm.types.{BeChildPosition, BeChildRole, BeScope}

case class BeReturn(value: Option[BeExpression]) extends BeExpression {

  override def expressionIO: BeExpressionIO = new BeExpressionIO {
    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, skipUnparsable: Boolean = false): String = {
      val valueString = value.map(_.expressionIO.getInLanguage(programmingLanguage, humanLanguage, skipUnparsable).replaceAll("\n", " "))
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
