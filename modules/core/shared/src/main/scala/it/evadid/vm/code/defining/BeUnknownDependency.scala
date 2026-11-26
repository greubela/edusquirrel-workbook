package it.evadid.vm.code.defining

import it.evadid.vm.code.abstractions.{BeDefineStructure, BeExpression}
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.controlflow.ControlFlowType.ControlFlowDown
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*

import scala.reflect.ClassTag

/** What a program uses without defining it and without a configured [[BeDependency]] providing it: the
  * definitions it would need to compile. A call `System.out2.println(x)` needs a class `System` whose
  * attribute `out2` holds an object of a class with a method `println`; `helper(1)` needs a function `helper`.
  *
  * Attributes and methods of a root class (one the program names directly) are static; attributes of the
  * synthesized intermediate classes and their methods belong to instances. Signatures accept any value.
  * Built by [[it.evadid.vm.conversion.dependencies.UnknownDependencies]].
  */
final case class BeUnknownDependency(
    rootClasses: List[BeDefineClass],
    memberClasses: List[BeDefineClass],
    override val definedFunctions: List[BeDefineFunction]
) extends BeDefineStructure derives upickle.default.ReadWriter {
  override def definedClasses: List[BeDefineClass] = rootClasses ++ memberClasses

  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {}
  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo[BeUnknownDependency](this) {
    override def withReplacedChildren(children: Map[BeChildRole, BeExpression]): BeUnknownDependency = {
      def replaced[T <: BeDefineStructure: ClassTag](values: List[T], offset: Int): List[T] = values.zipWithIndex.map((value, index) =>
        children.get(BeChildRole.ExpressionInSequence(offset + index)).collect { case definition: T => definition }.getOrElse(value))
      BeUnknownDependency(replaced(rootClasses, 0), replaced(memberClasses, rootClasses.size),
        replaced(definedFunctions, rootClasses.size + memberClasses.size))
    }
    override def toJavaStyleLines(info: BeChildInfo): Seq[BeSegmentedCodeElement] = asExpressionLine(ControlFlowDown, info)
    override def getChildrenAndExtension(scope: BeScope): Seq[BeExpressionNode] =
      allDefinedStructures.zipWithIndex.map((value, index) => BeExpressionReference(BeChildInfo(BeChildRole.ExpressionInSequence(index), scope), value))
  }
}
