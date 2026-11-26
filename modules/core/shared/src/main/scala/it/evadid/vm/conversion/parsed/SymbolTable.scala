package it.evadid.vm.conversion.parsed

import it.evadid.vm.types.BeDataType.AnyType

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.vm.naming.{BeEntityName, KnownBeEntityNames}
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.types.{AssigningNotPossible, AssigningPossibleWithImplicitCast, AssigningPossibleWithSameType, BeDataType}

object SymbolTable {
  sealed trait KnownStructure derives upickle.default.ReadWriter {
    def name: String
  }

  object KnownStructure {
    final case class Variable(name: String, variable: BeDefineVariable) extends KnownStructure derives upickle.default.ReadWriter

    final case class Function(name: String, function: BeDefineFunction) extends KnownStructure derives upickle.default.ReadWriter

    final case class Operator(name: String, function: BeDefineFunction) extends KnownStructure derives upickle.default.ReadWriter

    final case class Class(name: String, clazz: BeDefineClass) extends KnownStructure derives upickle.default.ReadWriter
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
                                           ) derives upickle.default.ReadWriter {
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

    def removeOperator(key: (String, Int), function: BeDefineFunction): CurrentlyKnownStructures =
      copy(operators = operators.updatedWith(key)(_.map(_.filterNot(_ eq function))))

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

  /** What is known while a parsed program is bound to definitions. A context is an immutable value: each operation
    * that learns something returns the extended context together with its result, and the builder passes it on.
    *
    * @param structures            definitions that names, operators and class types resolve against
    * @param scopes                variable scopes, innermost first; the last one is the module scope
    * @param functionsByName       call-resolution cache: known functions, declared signatures and the definitions
    *                              made for unknown names
    * @param declaredCallableNames functions and classes declared in the source; they shadow known names
    * @param definedVariables      every variable defined so far, in order, including those of closed scopes
    * @param definedFunctions      every registered function and operator, in order
    * @param definedClasses        every class, replaced in place when it is registered again
    * @param currentClass          the class whose method body is read, for `self`/`this`
    */
  final case class ParseContext(
      structures: CurrentlyKnownStructures,
      scopes: List[Map[String, BeDefineVariable]],
      functionsByName: Map[String, BeDefineFunction],
      declaredCallableNames: Set[String],
      definedVariables: List[BeDefineVariable],
      definedFunctions: List[BeDefineFunction],
      definedClasses: List[BeDefineClass],
      currentClass: Option[BeDefineClass]) {

    /** A fresh context on what this one knows so far, with the same declared names, e.g. for a class or method body. */
    def child(currentClass: Option[BeDefineClass] = None): ParseContext =
      ParseContext.initial(structures, declaredCallableNames, currentClass)

    // Variables

    def pushScope: ParseContext = copy(scopes = Map.empty :: scopes)

    def popScope: ParseContext = copy(scopes = scopes.tail)

    def lookupVariable(name: String): Option[BeDefineVariable] = scopes.collectFirst { case scope if scope.contains(name) => scope(name) }

    /** The visible variable `name`, defined in the innermost scope if there is none. */
    def assignVariable(name: String, dataType: BeDataType): (BeDefineVariable, ParseContext) =
      lookupVariable(name).map(_ -> this).getOrElse(defineVariable(name, dataType))

    /** A new variable `name` in the innermost scope, hiding outer ones. */
    def defineVariable(name: String, dataType: BeDataType): (BeDefineVariable, ParseContext) = {
      val variable = newVariable(name, dataType)
      (variable, copy(scopes = scopes.head.updated(name, variable) :: scopes.tail).registerVariable(name, variable))
    }

    /** The module-level variable `name`, defined there if no module code has assigned it yet. */
    def moduleVariable(name: String, dataType: BeDataType): (BeDefineVariable, ParseContext) =
      scopes.last.get(name).map(_ -> this).getOrElse {
        val variable = newVariable(name, dataType)
        (variable, copy(scopes = scopes.init :+ scopes.last.updated(name, variable)).registerVariable(name, variable))
      }

    def registerVariable(name: String, variable: BeDefineVariable): ParseContext =
      copy(definedVariables = withAdded(definedVariables, variable), structures = structures.addVariable(name, variable))

    private def newVariable(name: String, dataType: BeDataType) = BeDefineVariable(BeEntityName.fromUniversalNameInParts(name), dataType)

    // Functions

    /** Binds a function's signature before its body is read, so recursive calls know its parameters and
      * result; [[registerFunction]] then replaces it with the built definition.
      */
    def declareFunction(name: String, signature: BeDefineFunction): ParseContext =
      copy(functionsByName = functionsByName.updated(name, signature), structures = structures.addFunction(name, signature))

    def registerFunction(name: String, function: BeDefineFunction, isOperator: Boolean = false): ParseContext =
      copy(functionsByName = functionsByName.updated(name, function), definedFunctions = withAdded(definedFunctions, function),
        structures = if isOperator then structures.addOperator(name, function) else structures.addFunction(name, function))

    /** Reserve declarations before reading their bodies or any calls in the block. */
    def reserveCallableNames(names: Set[String]): ParseContext = {
      val added = names -- declaredCallableNames
      copy(declaredCallableNames = declaredCallableNames ++ added, functionsByName = functionsByName -- added)
    }

    private def hasSourceBinding(name: String): Boolean = {
      val owner = name.takeWhile(_ != '.')
      declaredCallableNames.contains(owner) || lookupVariable(owner).nonEmpty || structures.classes.contains(owner)
    }

    /** The definition a call of `name` binds to: a cached one, or one made for a turtle command, a data library
      * method, a known name such as `print`, or else an unknown function with `arity` untyped parameters.
      * A callable parameter/local shadows a cached builtin or turtle alias in this scope and is not cached.
      */
    def resolveFunction(name: String, arity: Int): (BeDefineFunction, ParseContext) =
      if lookupVariable(name.takeWhile(_ != '.')).nonEmpty then (newFunction(name, arity), this)
      else functionsByName.get(name).map(_ -> this).getOrElse {
        val function = newFunction(name, arity)
        (function, copy(functionsByName = functionsByName.updated(name, function)))
      }

    private def newFunction(name: String, arity: Int): BeDefineFunction = {
      val sourceBinding = hasSourceBinding(name)
      val libraryMethod = if sourceBinding then None
        else TurtleCommand.resolveTurtleMethod(name).orElse(it.evadid.vm.simulation.data.DataLibraries.resolveMethod(name))
      libraryMethod.map { method =>
        // Preserve the class signature while binding only the arguments supplied by this call.
        if arity < method.inputs.size then method.copy(inputs = method.inputs.take(arity)) else method
      }.getOrElse {
        val canonical = if sourceBinding then None else KnownBeEntityNames.resolveCanonicalNames(name)
        BeDefineFunction(positionalParameters(0, arity), None, BeSequence.optionalBody(Nil),
          BeDefineFunction.functionInfo(canonical.getOrElse(BeEntityName.fromUniversalNameInParts(name))),
          Option.when(canonical.contains(KnownBeEntityNames.resolveSystemMethods("print").get))(it.evadid.vm.code.defining.BeIntrinsic.Print))
      }
    }

    /** `function`, widened with untyped parameters when a call passes more than `arity` arguments. A widened
      * operator moves from its old arity to the new one.
      */
    def ensureFunctionArity(name: String, function: BeDefineFunction, arity: Int): (BeDefineFunction, ParseContext) =
      if arity <= function.inputs.length then (function, this)
      else {
        val updated = function.copy(inputs = function.inputs ++ positionalParameters(function.inputs.length, arity))
        val previousKey = name -> function.inputs.length
        val isOperator = structures.operators.get(previousKey).exists(_.exists(_ eq function))
        (updated, copy(
          functionsByName = functionsByName.updated(name, updated),
          definedFunctions = definedFunctions.map(existing => if existing eq function then updated else existing),
          structures = if isOperator then structures.removeOperator(previousKey, function).addOperator(name, updated)
            else structures.addFunction(name, updated)))
      }

    private def positionalParameters(from: Int, until: Int): List[BeDefineVariable] =
      (from until until).map(index => newVariable(s"arg$index", AnyType)).toList

    // Operators

    /** The operator overload whose parameters fit the arguments best; an untyped operator is made for unknown ones. */
    def resolveOperator(symbol: String, arity: Int, arguments: List[BeExpression]): (BeDefineFunction, ParseContext) =
      structures.operators.get(symbol -> arity).filter(_.nonEmpty) match {
        case Some(candidates) => (bestCandidate(candidates, arguments), this)
        case None =>
          val parameters = (0 until arity).map {
            case 0 => "left"
            case 1 => "right"
            case other => s"arg$other"
          }.map(newVariable(_, AnyType)).toList
          val function = BeDefineFunction(parameters, Some(newVariable("result", AnyType)), BeExpression.pass, BeDefineFunction.operatorInfo(symbol, 1))
          (function, registerFunction(symbol, function, isOperator = true))
      }

    private def bestCandidate(candidates: List[BeDefineFunction], arguments: List[BeExpression]): BeDefineFunction = {
      val scored = candidates.zipWithIndex.flatMap { case (candidate, index) =>
        val assignmentResults = candidate.inputs.zip(arguments).map { case (param, argument) =>
          param.variableType.canTakeValuesFrom(argument.staticInformationExpression.staticType)
        }
        Option.when(!assignmentResults.exists(_.isInstanceOf[AssigningNotPossible])) {
          val score = assignmentResults.map {
            case _: AssigningPossibleWithSameType => 0
            case _: AssigningPossibleWithImplicitCast => 1
            case _ => 2
          }.sum
          (candidate, score, index)
        }
      }
      scored.sortBy { case (_, score, index) => (score, index) }.headOption.map(_._1).getOrElse(candidates.head)
    }

    // Classes

    def registerClass(name: String, clazz: BeDefineClass): ParseContext = {
      val existing = definedClasses.indexWhere(_.name.universalInterpretation() == clazz.name.universalInterpretation())
      copy(definedClasses = if existing < 0 then definedClasses :+ clazz else definedClasses.updated(existing, clazz),
        structures = structures.addClass(name, clazz))
    }

    def lookupClass(name: String): Option[BeDefineClass] =
      structures.classes.get(name).orElse(structures.classes.values.find(_.name.universalInterpretation() == BeEntityName.fromCodeString(name).universalInterpretation()))

    def classForType(dataType: BeDataType): Option[BeDefineClass] = dataType match {
      case BeDataType.ClassType(name) => structures.classes.values.find(_.name.universalInterpretation() == name.universalInterpretation())
      case _ => None
    }

    /** `items` with `item` appended unless that very instance is already present. */
    private def withAdded[T <: AnyRef](items: List[T], item: T): List[T] = if items.exists(_ eq item) then items else items :+ item
  }

  object ParseContext {
    /** A context that knows `known`; known operators also answer calls by their symbol. */
    def initial(known: CurrentlyKnownStructures, declaredNames: Set[String] = Set.empty,
        currentClass: Option[BeDefineClass] = None): ParseContext = {
      val operators = known.operators.toList.flatMap { case ((symbol, _), functions) => functions.map(symbol -> _) }
      val functions = operators.map(_._2) ++ known.functions.values
      ParseContext(
        structures = known,
        scopes = List(known.variables),
        functionsByName = operators.foldLeft(known.functions) { case (cache, (symbol, function)) =>
          if cache.contains(symbol) then cache else cache.updated(symbol, function) },
        declaredCallableNames = declaredNames,
        definedVariables = known.variables.values.toList,
        definedFunctions = functions.foldLeft(List.empty[BeDefineFunction])((defined, function) =>
          if defined.exists(_ eq function) then defined else defined :+ function),
        definedClasses = known.classes.values.toList,
        currentClass = currentClass)
    }
  }
}
