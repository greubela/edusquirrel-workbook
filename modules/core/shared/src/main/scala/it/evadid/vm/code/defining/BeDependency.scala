package it.evadid.vm.code.defining

import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.vm.code.abstractions.{BeDefineStructure, BeExpression}
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.controlflow.ControlFlowType.ControlFlowDown
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*

/** Definitions supplied by the execution environment, plus the imports/helpers needed by standalone source. */
final case class BeDependency(
    structureName: BeEntityName,
    addDefinitions: Map[ProgrammingLanguage, String],
    override val definedClasses: List[BeDefineClass] = Nil,
    override val definedFunctions: List[BeDefineFunction] = Nil,
    override val definedVariables: List[BeDefineVariable] = Nil
) extends BeDefineStructure derives upickle.default.ReadWriter {
  def definition(language: ProgrammingLanguage): Option[String] = addDefinitions.get(language)
  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {}
  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo[BeDependency](this) {
    override def withReplacedChildren(children: Map[BeChildRole, BeExpression]): BeDependency = {
      val updated = allDefinedStructures.zipWithIndex.map((value, index) =>
        children.get(BeChildRole.ExpressionInSequence(index)).collect { case definition: BeDefineStructure => definition }.getOrElse(value))
      copy(definedClasses = updated.collect { case value: BeDefineClass => value },
        definedFunctions = updated.collect { case value: BeDefineFunction => value },
        definedVariables = updated.collect { case value: BeDefineVariable => value })
    }
    override def toJavaStyleLines(info: BeChildInfo): Seq[BeSegmentedCodeElement] = asExpressionLine(ControlFlowDown(), info)
    override def getChildrenAndExtension(scope: BeScope): Seq[BeExpressionNode] =
      allDefinedStructures.zipWithIndex.map((value, index) => BeExpressionReference(BeChildInfo(BeChildRole.ExpressionInSequence(index), scope), value))
  }
}
