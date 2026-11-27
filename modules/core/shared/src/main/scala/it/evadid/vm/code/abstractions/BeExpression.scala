package it.evadid.vm.code.abstractions

import it.evadid.core.datastructures.tree.nodeImpl.NodeBasedTreeImpl
import it.evadid.vm.code.controlStructures.*
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable, BeDependency, BeUnknownDependency}
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import it.evadid.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.vm.code.tree.*
import it.evadid.vm.code.usage.*
import it.evadid.vm.io.BeExpressionStructureInfo
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*
import it.evadid.vm.types.BeScope.GlobalScope
import upickle.default.*
import scala.reflect.ClassTag

object BeExpression {

  val pass: BeSequence = BeSequence.optionalBody(List())

  private[vm] given ReadWriter[BeDefineFunction] = macroRW

  private[vm] given ReadWriter[BeDefineClass] = macroRW

  private[vm] given ReadWriter[BeDefineVariable] = macroRW


  // BeExpression is open, so its case-class codecs have no automatic sealed
  // parent tag. Explicitly tag them before merging the recursive expression codec.
  private inline def taggedExpressionRW[T <: BeExpression](using tag: ClassTag[T], mirror: scala.deriving.Mirror.Of[T]): ReadWriter[T] = {
    val name = tag.runtimeClass.getName
    val shortName = tag.runtimeClass.getSimpleName
    val reader = annotate(macroR[T], "$type", name, shortName)
    val writer = annotate(macroW[T].asInstanceOf[ObjectWriter[T]], "$type", name, shortName)
    ReadWriter.join(using reader, writer)
  }

  given rw: ReadWriter[BeExpression] = ReadWriter.merge(
    taggedExpressionRW[BeFor],
    taggedExpressionRW[BeIfElse],
    taggedExpressionRW[BeRepeatNr],

    taggedExpressionRW[BeSequence],
    taggedExpressionRW[BeWhile],

    taggedExpressionRW[BeExpressionUnparsable],
    taggedExpressionRW[BeExpressionUnsupported],
    taggedExpressionRW[BeSingleLineComment],


    taggedExpressionRW[BeReturn],
    taggedExpressionRW[BeStartProgram],

    taggedExpressionRW[BeCurrentInstance],
    taggedExpressionRW[BeMemberAccess],
    taggedExpressionRW[BeAssignMember],
    taggedExpressionRW[BeAssignVariable],
    taggedExpressionRW[BeFunctionCall],
    taggedExpressionRW[BeUseValue],
    taggedExpressionRW[BeDefineVariable],
    taggedExpressionRW[BeDefineFunction],
    taggedExpressionRW[BeDefineClass],
    taggedExpressionRW[BeDependency],
    taggedExpressionRW[BeUnknownDependency],
  )


}


trait BeExpression {

  lazy val staticInformationExpression: BeExpressionStaticInformation = {
    println("[WARN] No static information implemented for " + getClass.getSimpleName)
    new BeExpressionStaticInformation() {}
  }

  lazy val structureInfo: BeExpressionStructureInfo[?]

  /** This expression and its descendants, depth first in source order. */
  def subtree: Vector[BeExpression] =
    this +: structureInfo.getChildren(false, GlobalScope()).toVector.flatMap {
      case BeExpressionReference(_, child) => child.subtree
      case _ => Vector.empty
    }

  /** The calls of `module`'s functions in this subtree, in source order. */
  def moduleCalls(module: it.evadid.vm.modules.BeModule[?]): Vector[BeFunctionCall] =
    subtree.collect { case call: BeFunctionCall if module.owns(call.funcDef) => call }

  def recToTree(withExtensions: Boolean, myPosition: BeChildInfo): BeExpressionTree = {
    val root: BeExpressionNode = BeExpressionReference(myPosition, this)

    val children: Seq[(BeExpressionNode, Option[BeExpressionTree])] = structureInfo.getChildren(withExtensions, myPosition.myScope).map {
      case exprNode@BeExpressionReference(childPosition: BeChildInfo, childExpr: BeExpression) => (exprNode, Some(childExpr.recToTree(withExtensions, childPosition)))
      case exprNode@BeExtensionPoint(isRequired, childPosition, willBeUsedAsType) => (exprNode, None)
    }

    val empty: BeExpressionTree = NodeBasedTreeImpl.empty[BeExpressionNode]()
    val rooted: BeExpressionTree = empty.addAsLastChild(empty.rootPosition, root)
    children.foldLeft(rooted) { case (tree, (expr, opTree)) => opTree match {
      case Some(expressionTree: BeExpressionTree) => tree.addSubtreeAsLastChild(tree.rootPosition.forChild(0), expressionTree)
      case _ => tree.addAsLastChild(tree.rootPosition.forChild(0), expr)
    }}
  }

  def staticInformationSubtree: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

    private lazy val allExprChildren: List[BeExpression] = subtree.toList

    override def staticType: BeDataType = staticInformationExpression.staticType

    override def staticValue: Option[BeDataValue] = staticInformationExpression.staticValue

    override def syntaxErrors: Seq[BeInfo] = staticInformationExpression.syntaxErrors ++ allExprChildren.flatMap(_.staticInformationExpression.syntaxErrors)

    override def hasSideEffects: Boolean = staticInformationExpression.hasSideEffects || allExprChildren.exists(_.staticInformationExpression.hasSideEffects)

    /*
    override def getDefinitions: BeDefineStructure = new BeDefineStructure() {

      private lazy val myDefs: BeDefineStructure = staticInformationExpression.getDefinitions

      override lazy val definedClasses: List[BeDefineClass] = myDefs.definedClasses ++ allExprChildren.flatMap(_.staticInformationExpression.getDefinitions.definedClasses)

      override lazy val definedFunctions: List[BeDefineFunction] = myDefs.definedFunctions ++ allExprChildren.flatMap(_.staticInformationExpression.getDefinitions.definedFunctions)

      override lazy val definedVariables: List[BeDefineVariable] = myDefs.definedVariables ++ allExprChildren.flatMap(_.staticInformationExpression.getDefinitions.definedVariables)

      override lazy val allDefinedStructures: List[BeDefineStructure] = myDefs.allDefinedStructures ++ allExprChildren.flatMap(_.staticInformationExpression.getDefinitions.allDefinedStructures)
    }*/

  }

  //def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = this


}

