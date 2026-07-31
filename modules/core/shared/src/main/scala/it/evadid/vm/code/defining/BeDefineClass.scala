package it.evadid.vm.code.defining

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.abstractions.{BeDefineStructure, BeExpression}
import it.evadid.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import it.evadid.vm.controlflow.ControlFlowType.ControlFlowDown
import it.evadid.vm.io.{BeExpressionStructureInfo, BeSegmentedCodeElement}
import it.evadid.vm.naming.{BeEntityName, CodeRepresentationConfig}
import it.evadid.vm.static.BeExpressionStaticInformation
import it.evadid.vm.types.*

case class BeDefineClass(
                          name: BeEntityName,
                          attributes: List[BeDefineVariable],
                          methods: List[BeDefineFunction],
                       //   bodyExtras: List[BeExpression] = Nil
                        )
  extends BeDefineStructure {

  override def definedClasses: List[BeDefineClass] = List(this)

  override lazy val staticInformationExpression: BeExpressionStaticInformation = new BeExpressionStaticInformation() {

    override def syntaxErrors: Seq[BeInfo] = methods.flatMap(curMethod => {
      val inClass = curMethod.functionTypeInfo.isMethodInClass
      if (inClass.isEmpty)
        Some(BeInfo(LanguageMap.universalMap("Method must have an object it´s called on!"), BeInfo.SyntaxError.StructureMismatch))
      else if (inClass.get.name.universalInterpretation() != BeDefineClass.this.name.universalInterpretation())
        Some(BeInfo(LanguageMap.universalMap("Method must live in the class its defined in!"), BeInfo.SyntaxError.StructureMismatch))
      else None
    })

    override def hasSideEffects: Boolean = true

  }

  override lazy val structureInfo: BeExpressionStructureInfo[?] = new BeExpressionStructureInfo[BeDefineClass](this) {
    override def withReplacedChildren(newChildren: Map[BeChildRole, BeExpression]): BeDefineClass = {
      copy(
        attributes = attributes.zipWithIndex.map { (attribute, index) =>
          newChildren.get(BeChildRole.AttributeInClass(index)).collect {
            case replacement: BeDefineVariable => replacement
          }.getOrElse(attribute)
        },
        methods = methods.zipWithIndex.map { (method, index) =>
          newChildren.get(BeChildRole.MethodInClass(index)).collect {
            case replacement: BeDefineFunction => replacement
          }.getOrElse(method)
        }
      )
    }

    override def toJavaStyleLines(myInfo: BeChildInfo): Seq[BeSegmentedCodeElement] = asExpressionLine(ControlFlowDown, myInfo)

    override def getChildrenAndExtension(myScope: BeScope): Seq[BeExpressionNode] = {
      val attributeChildren = attributes.zipWithIndex.map((attribute, index) =>
        BeExpressionReference(BeChildInfo(BeChildRole.AttributeInClass(index), myScope), attribute)
      )
      val methodChildren = methods.zipWithIndex.map((method, index) =>
        BeExpressionReference(BeChildInfo(BeChildRole.MethodInClass(index), myScope), method)
      )
      attributeChildren ++ methodChildren
    }
  }


}

object BeDefineClass {
  case class MethodSignature(name: BeEntityName, inputs: List[BeDefineVariable], output: Option[BeDefineVariable])

  def apply(name: LanguageMap[HumanLanguage], attributes: List[BeDefineVariable], methods: List[BeDefineFunction]): BeDefineClass =
    BeDefineClass(BeEntityName.fromMapInCodeNotation(name.asInstanceOf[LanguageMap[HumanLanguage]]), attributes, methods)

  def fromLanguageMap(name: LanguageMap[HumanLanguage], attributes: List[BeDefineVariable], methods: List[BeDefineFunction]): BeDefineClass =
    apply(name, attributes, methods)

  def withMethods(name: BeEntityName, attributes: List[BeDefineVariable], methodSignatures: List[MethodSignature]): BeDefineClass = {
    val ownerInfoClass = BeDefineClass(name, attributes, Nil)
    val methods = methodSignatures.map { signature =>
      BeDefineFunction(
        signature.inputs,
        signature.output,
        BeSequence.optionalBody(List(BeExpression.pass)),
        BeDefineFunction.methodFunctionInfo(ownerInfoClass, signature.name)
      )
    }
    BeDefineClass(name, attributes, methods)
  }
}
