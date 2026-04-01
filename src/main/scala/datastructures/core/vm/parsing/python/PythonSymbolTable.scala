package datastructures.core.vm.parsing.python

import datastructures.core.language.LanguageMap
import datastructures.core.vm.code.BeExpression
import datastructures.core.vm.code.controlStructures.BeSequence
import datastructures.core.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import datastructures.core.vm.types.BeDataType.AnyType
import datastructures.core.vm.types.{AssigningNotPossible, AssigningPossibleWithImplicitCast, AssigningPossibleWithSameType, BeDataType}

import scala.collection.mutable

object PythonSymbolTable {
  sealed trait KnownStructure {
    def name: String
  }

  object KnownStructure {
    final case class Variable(name: String, variable: BeDefineVariable) extends KnownStructure
    final case class Function(name: String, function: BeDefineFunction) extends KnownStructure
    final case class Operator(name: String, function: BeDefineFunction) extends KnownStructure
    final case class Class(name: String, clazz: BeDefineClass) extends KnownStructure
  }

  val defaultKnownStructures: Seq[KnownStructure] =
    DefaultDefinitions.operatorDefinitionsWithSymbols.map { case (symbol, function) =>
      KnownStructure.Operator(symbol, function)
    } ++ DefaultDefinitions.builtinFunctionDefinitions.map { case (name, function) =>
      KnownStructure.Function(name, function)
    }

  final case class CurrentlyKnownStructures(
                                             variables: Map[String, BeDefineVariable],
                                             functions: Map[String, BeDefineFunction],
                                             operators: Map[(String, Int), List[BeDefineFunction]],
                                             classes: Map[String, BeDefineClass]
                                           ) {
    def addVariable(name: String, variable: BeDefineVariable): CurrentlyKnownStructures =
      copy(variables = variables.updated(name, variable))

    def addFunction(name: String, function: BeDefineFunction): CurrentlyKnownStructures =
      copy(functions = functions.updated(name, function))

    def addOperator(name: String, function: BeDefineFunction): CurrentlyKnownStructures = {
      val key = name -> function.inputs.length
      val existing = operators.getOrElse(key, List.empty)
      val updatedList =
        if (existing.exists(_ eq function)) existing.map(cur => if (cur eq function) function else cur)
        else existing :+ function
      copy(
        functions = functions.updated(name, function),
        operators = operators.updated(key, updatedList)
      )
    }

    def addClass(name: String, clazz: BeDefineClass): CurrentlyKnownStructures =
      copy(classes = classes.updated(name, clazz))

    def +(structure: KnownStructure): CurrentlyKnownStructures = structure match {
      case KnownStructure.Variable(name, variable) => addVariable(name, variable)
      case KnownStructure.Function(name, function) => addFunction(name, function)
      case KnownStructure.Operator(name, function) => addOperator(name, function)
      case KnownStructure.Class(name, clazz) => addClass(name, clazz)
    }
  }

  object CurrentlyKnownStructures {
    val empty: CurrentlyKnownStructures =
      CurrentlyKnownStructures(Map.empty, Map.empty, Map.empty[(String, Int), List[BeDefineFunction]], Map.empty)

    def fromKnown(structures: Seq[KnownStructure]): CurrentlyKnownStructures =
      structures.foldLeft(empty)(_ + _)
  }

  final class ParseContext(initialKnownStructures: CurrentlyKnownStructures) {
    private var currentlyKnownStructures: CurrentlyKnownStructures = initialKnownStructures
    private var scopes: List[mutable.LinkedHashMap[String, BeDefineVariable]] = {
      val baseScope = mutable.LinkedHashMap[String, BeDefineVariable]()
      baseScope ++= initialKnownStructures.variables
      List(baseScope)
    }
    private val variablesBuffer = mutable.ListBuffer[BeDefineVariable]()
    variablesBuffer ++= initialKnownStructures.variables.values
    private val functionsBuffer = mutable.ListBuffer[BeDefineFunction]()
    private val classesBuffer = mutable.ListBuffer[BeDefineClass]()
    classesBuffer ++= initialKnownStructures.classes.values
    private val functionsByName = mutable.LinkedHashMap[String, BeDefineFunction]()
    functionsByName ++= initialKnownStructures.functions
    private val operatorFunctions = mutable.LinkedHashMap[(String, Int), List[BeDefineFunction]]()
    operatorFunctions ++= initialKnownStructures.operators

    initialKnownStructures.operators.foreach { case ((symbol, _), functions) =>
      functions.foreach { function =>
        if (!functionsByName.contains(symbol)) functionsByName.update(symbol, function)
        if (!functionsBuffer.exists(_ eq function)) functionsBuffer += function
      }
    }
    initialKnownStructures.functions.foreach { case (_, function) =>
      if (!functionsBuffer.exists(_ eq function)) functionsBuffer += function
    }

    def pushScope(): Unit = scopes = mutable.LinkedHashMap[String, BeDefineVariable]() :: scopes
    def popScope(): Unit = scopes = scopes.tail

    def assignVariable(name: String, dataType: BeDataType): BeDefineVariable = {
      lookupVariable(name).getOrElse {
        val variable = BeDefineVariable(LanguageMap.universalMap(name), dataType)
        currentScope.update(name, variable)
        registerVariable(name, variable)
        variable
      }
    }

    def defineVariable(name: String, dataType: BeDataType): BeDefineVariable = {
      val variable = BeDefineVariable(LanguageMap.universalMap(name), dataType)
      currentScope.update(name, variable)
      registerVariable(name, variable)
      variable
    }

    def lookupVariable(name: String): Option[BeDefineVariable] = scopes.collectFirst { case scope if scope.contains(name) => scope(name) }
    private def currentScope: mutable.LinkedHashMap[String, BeDefineVariable] = scopes.head

    def registerVariable(name: String, variable: BeDefineVariable): Unit = {
      if (!variablesBuffer.exists(_ eq variable)) variablesBuffer += variable
      currentlyKnownStructures = currentlyKnownStructures.addVariable(name, variable)
    }

    def registerFunction(name: String, function: BeDefineFunction, isOperator: Boolean = false): Unit = {
      functionsByName.update(name, function)
      if (!functionsBuffer.exists(_ eq function)) functionsBuffer += function
      currentlyKnownStructures =
        if (isOperator) currentlyKnownStructures.addOperator(name, function)
        else currentlyKnownStructures.addFunction(name, function)
    }

    def registerClass(name: String, clazz: BeDefineClass): Unit = {
      if (!classesBuffer.exists(_ eq clazz)) classesBuffer += clazz
      currentlyKnownStructures = currentlyKnownStructures.addClass(name, clazz)
    }

    def snapshotStructures: CurrentlyKnownStructures = currentlyKnownStructures

    def resolveFunction(name: String, arity: Int): BeDefineFunction =
      functionsByName.getOrElse(name, {
        val params = (0 until arity).map(index => BeDefineVariable(LanguageMap.universalMap(s"arg$index"), AnyType)).toList
        val placeholder = BeDefineFunction(params, None, BeSequence.optionalBody(Nil), BeDefineFunction.functionInfo(LanguageMap.universalMap(name)))
        functionsByName.update(name, placeholder)
        placeholder
      })

    def ensureFunctionArity(name: String, function: BeDefineFunction, arity: Int): BeDefineFunction = {
      if (arity <= function.inputs.length) function
      else {
        val additional = (function.inputs.length until arity).map { index =>
          BeDefineVariable(LanguageMap.universalMap(s"arg$index"), AnyType)
        }.toList
        val updated = function.copy(inputs = function.inputs ++ additional)
        functionsByName.update(name, updated)
        val idx = functionsBuffer.indexWhere(_ eq function)
        if (idx >= 0) functionsBuffer.update(idx, updated)
        val operatorKey = name -> updated.inputs.length
        if (operatorFunctions.contains(operatorKey)) {
          val existing = operatorFunctions(operatorKey)
          val replaced =
            if (existing.exists(_ eq function)) existing.map(cur => if (cur eq function) updated else cur)
            else existing :+ updated
          operatorFunctions.update(operatorKey, replaced)
          currentlyKnownStructures = currentlyKnownStructures.addOperator(name, updated)
        } else {
          currentlyKnownStructures = currentlyKnownStructures.addFunction(name, updated)
        }
        updated
      }
    }

    def resolveOperator(symbol: String, arity: Int, arguments: List[BeExpression]): BeDefineFunction = {
      val key = symbol -> arity
      operatorFunctions.get(key) match {
        case Some(candidates) if candidates.nonEmpty =>
          val scored = candidates.zipWithIndex.flatMap { case (candidate, index) =>
            val assignmentResults = candidate.inputs.zip(arguments).map { case (param, argument) =>
              param.variableType.canTakeValuesFrom(argument.staticInformationExpression.staticType)
            }

            if (assignmentResults.exists(_.isInstanceOf[AssigningNotPossible])) None
            else {
              val score = assignmentResults.map {
                case _: AssigningPossibleWithSameType => 0
                case _: AssigningPossibleWithImplicitCast => 1
                case _ => 2
              }.sum
              Some((candidate, score, index))
            }
          }

          scored.sortBy { case (_, score, index) => (score, index) }.headOption.map(_._1).getOrElse(candidates.head)
        case _ =>
          val params = (0 until arity).map { index =>
            val paramName = index match {
              case 0 => "left"
              case 1 => "right"
              case other => s"arg$other"
            }
            BeDefineVariable(LanguageMap.universalMap(paramName), AnyType)
          }.toList
          val outputVar = Some(BeDefineVariable(LanguageMap.universalMap("result"), AnyType))
          val function = BeDefineFunction(params, outputVar, BeExpression.pass, BeDefineFunction.operatorInfo(symbol, 1))
          registerOperator(symbol, function)
          function
      }
    }

    private def registerOperator(symbol: String, function: BeDefineFunction): Unit = {
      val key = symbol -> function.inputs.length
      val existing = operatorFunctions.getOrElse(key, List.empty)
      val updatedList =
        if (existing.exists(_ eq function)) existing.map(cur => if (cur eq function) function else cur)
        else existing :+ function
      operatorFunctions.update(key, updatedList)
      registerFunction(symbol, function, isOperator = true)
    }

    def definedClasses: List[BeDefineClass] = classesBuffer.toList
    def definedFunctions: List[BeDefineFunction] = functionsBuffer.toList
    def definedVariables: List[BeDefineVariable] = variablesBuffer.toList
    def currentStructures: CurrentlyKnownStructures = currentlyKnownStructures
  }
}
