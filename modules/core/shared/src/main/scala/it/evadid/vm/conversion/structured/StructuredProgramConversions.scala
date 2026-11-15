package it.evadid.vm.conversion.structured

import it.evadid.core.datastructures.language.AppLanguage.{English, Java, Python}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.BeAssignVariable
import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import it.evadid.vm.conversion.expressionjava.BeExpressionToJavaStr
import it.evadid.vm.conversion.expressionsnap.SnapProjectXml
import it.evadid.vm.conversion.javaexpression.JavaToBeExpressionParser
import it.evadid.vm.conversion.pythonexpression.PythonToExpression
import it.evadid.vm.conversion.snapexpression.{SnapStateConversion, SnapExpressionSupport}
import it.evadid.vm.conversion.snapxml.SnapXmlParser
import it.evadid.vm.naming.{BeEntityName, NamingStyle}
import it.evadid.vm.types.BeDataValueLiteral
import it.evadid.vm.code.usage.BeUseValue
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{SnapCanvasLayout, SnapCanvasScript}
import scala.collection.immutable.VectorMap

/** Class-level conversions. The reserved Main class represents module/static scope. */
object StructuredProgramConversions {
  import SourceStructure.*
  private val mainName = BeEntityName.fromCodeString("Main")
  private def join(parts: Iterable[String]): String = parts.filter(_.trim.nonEmpty).mkString("\n\n")
  private def print(expression: BeExpression, language: it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage): String = {
    val rendered = expression.structureInfo.toStringInLanguage(language, English, false).trim
    if language == Java then JavaToBeExpressionParser.withoutEntityHints(rendered) else rendered
  }
  private def statements(expression: BeExpression): List[BeExpression] = expression match {
    case BeStartProgram(Some(sequence)) => sequence.body.toList
    case sequence: BeSequence => sequence.body.toList
    case _ => List(expression)
  }
  private def printJava(expression: BeExpression, predefined: Set[String]): String = {
    val printer = new BeExpressionToJavaStr(English, false) {
      private var declared = predefined
      override protected def assignToDefinedVar(name: String, variableType: String, value: BeExpression): String = {
        if declared.contains(name) then s"$name = ${forExpression(value)}"
        else {
          declared += name
          super.assignToDefinedVar(name, variableType, value)
        }
      }
    }
    JavaToBeExpressionParser.withoutEntityHints(printer.forExpression(expression)).trim
  }
  private def methodName(source: String): Option[String] = {
    val header = mask(source.takeWhile(_ != '{'), false)
    if header.contains('=') then None
    else "([A-Za-z_$][\\w$]*)\\s*\\(".r.findFirstMatchIn(header).map(_.group(1))
  }
  private def pythonInstanceReferences(source: String): String =
    "\\bthis\\.".r.findAllMatchIn(mask(source, true)).toVector.reverse.foldLeft(source) { (code, found) =>
      code.take(found.start) + "self." + code.drop(found.end)
    }
  private def methodBody(source: String): String = {
    val clean = mask(source, false)
    val open = clean.indexOf('{')
    val close = clean.lastIndexOf('}')
    require(open >= 0 && close > open, "A method body is incomplete.")
    dedent(source.substring(open + 1, close))
  }
  private def stripModifiers(source: String): String =
    source.replaceFirst("^\\s*(?:(?:public|private|protected|static|final|synchronized)\\s+)+", "")
  private def removeMainCalls(source: String): String =
    source.linesIterator.zip(mask(source, true).linesIterator)
      .filterNot((_, clean) => clean.matches("main\\s*\\(\\s*\\)\\s*"))
      .map(_._1).mkString("\n")

  def javaWithLooseCode(classes: Map[BeEntityName, String], loose: String): ProgrammingStateJavaStructured = {
    val preamble = classes.get(mainName).flatMap(source => javaClasses(source).headOption.map(block => source.take(block.start))).getOrElse("")
    val existing = classes.get(mainName).map(javaMembers).getOrElse(Vector.empty)
    val looseMembers = javaMembers(s"class Main { $loose }")
    val (definitions, executable) = looseMembers.partition { member =>
      "^(?:(?:public|private|protected|static|final)\\s+)*[\\w$<>\\[\\]]+\\s+[\\w$]+\\s*\\(".r.findFirstIn(mask(member, false)).nonEmpty ||
        "^(?:final\\s+)?[A-Za-z_$][\\w$<>\\[\\]]*\\s+[A-Za-z_$][\\w$]*\\s*(?:=|;)".r.findFirstIn(member).nonEmpty
    }
    val staticDefinitions = definitions.map(member => if scope(member) == MemberScope.Static then member else "static " + member)
    val main = existing.find(methodName(_).contains("main"))
    val other = existing.filterNot(methodName(_).contains("main"))
    val body = join(main.map(methodBody).toList ++ executable)
    val entry = s"public static void main(String[] unusableArgs) {\n${indent(body)}\n}"
    val code = preamble + s"class Main {\n${indent(join(other ++ staticDefinitions :+ entry))}\n}"
    new ProgrammingStateJavaStructured(classes.updated(mainName, code))
  }

  /** Main is a presentation wrapper, not an additional runtime call. unusedCode stays a definition. */
  def moduleSource(state: ProgrammingStatePythonStructured): String = {
    val main = pythonBlocks(state.mainScript, "def").find(block => block.indent == 0 && block.name == "main")
    main match {
      case None => state.mainScript
      case Some(entry) =>
        val rest = remove(state.mainScript, Seq(entry.start -> entry.end))
        join(List(removeMainCalls(rest), entry.body))
    }
  }

  def toExpression(state: ProgrammingStatePythonStructured): ProgrammingStateBeExpression =
    ProgrammingStateBeExpression(PythonToExpression.program(join(state.definedClasses.values.toList :+ moduleSource(state))).fullProgram)

  def fromExpression(state: ProgrammingStateBeExpression): ProgrammingStatePythonStructured = {
    val source = ExpressionToPython.printedPython(state.expression)
    val parts = SourceStructure.python(source)
    val module = moduleSource(new ProgrammingStatePythonStructured(parts.definedClasses, parts.remaining))
    val functions = pythonBlocks(module, "def").filter(_.indent == 0)
    val rest = remove(module, functions.map(f => f.start -> f.end)).trim
    val main = if rest.nonEmpty then s"def main():\n${indent(rest)}\n\nmain()" else ""
    new ProgrammingStatePythonStructured(parts.definedClasses, join(functions.map(_.source) :+ main))
  }

  def toJava(state: ProgrammingStatePythonStructured): ProgrammingStateJavaStructured = {
    val code = moduleSource(state)
    val nodes = statements(PythonToExpression.program(code).fullProgram)
    val definitions = Vector.newBuilder[String]
    val startup = Vector.newBuilder[String]
    val moduleVariables = nodes.collect {
      case assignment: BeAssignVariable => assignment.target.name.getNameIn(English, NamingStyle.CamelCase)
      case variable: BeDefineVariable => variable.name.getNameIn(English, NamingStyle.CamelCase)
    }.toSet
    var globalNames = Set.empty[String]
    nodes.foreach {
      case function: BeDefineFunction => definitions += "static " + printJava(function,
        moduleVariables ++ function.inputs.map(_.name.getNameIn(English, NamingStyle.CamelCase)))
      case assignment: BeAssignVariable if !globalNames.contains(assignment.target.name.universalInterpretation()) =>
        val name = assignment.target.name.universalInterpretation()
        globalNames += name
        val value = print(assignment.value, Java)
        val inferred = if value.matches("[-+]?\\d+") then "int" else assignment.target.variableType.formatTypeForDisplay.getInLanguage(Java)
        definitions += s"static $inferred ${assignment.target.name.getNameIn(English, NamingStyle.CamelCase)} = $value;"
      case variable: BeDefineVariable => definitions += "static " + print(variable, Java).stripSuffix(";") + ";"
      case other => startup += printJava(BeStartProgram(BeSequence.optionalBody(List(other))), moduleVariables)
    }
    val entry = s"public static void main(String[] unusableArgs) {\n${indent(join(startup.result()))}\n}"
    val classes = VectorMap.from(state.definedClasses.iterator.map { (name, source) => name -> pythonClassToJava(source) })
    val generatedMain = join(definitions.result() :+ entry)
    val main = classes.get(mainName) match {
      case Some(instanceClass) =>
        val open = instanceClass.indexOf('{')
        instanceClass.take(open + 1) + "\n" + indent(generatedMain) + "\n" + instanceClass.drop(open + 1)
      case None => s"class Main {\n${indent(generatedMain)}\n}"
    }
    new ProgrammingStateJavaStructured(classes.updated(mainName, main))
  }

  def toPython(state: ProgrammingStateJavaStructured): ProgrammingStatePythonStructured = {
    val modules = Vector.newBuilder[String]
    val classes = Vector.newBuilder[(BeEntityName, String)]
    state.definedClasses.foreach { (name, source) =>
      if name == mainName then {
        val (staticMembers, instanceMembers) = javaMembers(source).partition(scope(_) == MemberScope.Static)
        staticMembers.foreach { member =>
          if methodName(member).contains("main") then {
            val body = javaToPython(methodBody(member))
            modules += s"def main():\n${indent(if body.isEmpty then "pass" else body)}\n\nmain()"
          } else modules += javaToPython(stripModifiers(member))
        }
        if instanceMembers.nonEmpty then classes += name -> javaClassToPython(s"class Main {\n${join(instanceMembers)}\n}")
      } else classes += name -> javaClassToPython(source)
    }
    val module = join(modules.result())
    val functions = pythonBlocks(module, "def").filter(f => f.indent == 0 && f.name == "main")
    val remainder = removeMainCalls(remove(module, functions.map(f => f.start -> f.end))).trim
    val entry = functions.headOption.map(_.source + "\n\nmain()").getOrElse("")
    new ProgrammingStatePythonStructured(VectorMap.from(classes.result()), join(List(remainder, entry)))
  }

  private def javaToPython(source: String): String =
    ExpressionToPython.printedPython(JavaToBeExpressionParser.parse(source)).trim

  private def javaClassToPython(source: String): String = {
    val name = javaClasses(source).head.name
    val members = javaMembers(source)
    val (fields, callable) = members.partition(methodName(_).isEmpty)
    val instanceInitializers = fields.filter(scope(_) == MemberScope.Instance).map { member =>
      "self." + javaToPython(stripModifiers(member))
    }
    val constructors = callable.filter(methodName(_).contains(name))
    require(constructors.size <= 1, "Overloaded Java constructors cannot be represented by one Python initializer.")
    val initializer = constructors.headOption.map { constructor =>
      val converted = javaToPython("void " + stripModifiers(constructor))
      val function = pythonBlocks(converted, "def").head
      val header = function.header.replaceFirst("def [^(]+\\(", "def __init__(self, ").replace("(self, )", "(self)")
      header + "\n" + indent(join(instanceInitializers :+ function.body))
    }.orElse(Option.when(instanceInitializers.nonEmpty)(s"def __init__(self):\n${indent(join(instanceInitializers))}"))
    val methods = callable.filterNot(methodName(_).contains(name)).map { member =>
      val converted = pythonInstanceReferences(javaToPython(stripModifiers(member)))
      if scope(member) == MemberScope.Static then "@staticmethod\n" + converted
      else converted.replaceFirst("def ([^(]+)\\(", "def $1(self, ").replace("(self, )", "(self)")
    }
    val body = fields.filter(scope(_) == MemberScope.Static).map(member => javaToPython(stripModifiers(member))) ++ initializer.toList ++ methods
    s"class $name:\n${indent(if body.isEmpty then "pass" else join(body))}"
  }

  private def pythonClassToJava(source: String): String = {
    val block = pythonBlocks(source, "class").head
    val functions = pythonBlocks(block.body, "def").filter(_.indent == 0)
    val remaining = remove(block.body, functions.map(f => f.start -> f.end)).linesIterator.filterNot(_.startsWith("@")).mkString("\n")
    val fields = statements(PythonToExpression.program(remaining).fullProgram).map(print(_, Java)).filter(_.nonEmpty)
      .map(code => "static " + code.stripSuffix(";") + ";")
    val instanceFields = functions.filter(_.name == "__init__").flatMap { function =>
      val declarations = function.body.linesIterator.map(_.trim).filter(_.startsWith("self.")).map(_.stripPrefix("self.")).mkString("\n")
      statements(PythonToExpression.program(declarations).fullProgram).collect {
        case BeAssignVariable(target, _) => target.copy(initValue = None)
        case variable: BeDefineVariable => variable.copy(initValue = None)
      }
    }.distinctBy(_.name)
    val methods = functions.map { function =>
      val instance = function.header.matches("(?s).*\\(\\s*self(?:\\s*[,)]).*" )
      val withoutSelf = function.source.replaceFirst("\\(\\s*self\\s*,?\\s*", "(")
      val selfReferences = "\\bself\\.".r.findAllMatchIn(mask(withoutSelf, true)).map(m => m.start -> m.end).toVector
      val normalized = remove(withoutSelf, selfReferences)
      val printed = printJava(PythonToExpression.program(normalized).fullProgram,
        instanceFields.map(_.name.getNameIn(English, NamingStyle.CamelCase)).toSet)
      if function.name == "__init__" then printed.replaceFirst("^[\\w<>]+\\s+\\w+\\s*\\(", block.name + "(")
      else (if instance then "" else "static ") + printed
    }
    s"class ${block.name} {\n${indent(join(fields ++ instanceFields.map(field => print(field, Java) + ";") ++ methods))}\n}"
  }

  def fromSnap(xml: String): ProgrammingStatePythonStructured = {
    val unsupported = SnapExpressionSupport.unsupportedSnapSelectors(xml)
    require(unsupported.isEmpty, s"Unsupported Snap blocks: ${unsupported.mkString(", ")}")
    require(SnapXmlParser.elements(xml, "project").nonEmpty, "Snap conversion needs a complete project.")
    val definitions = SnapXmlParser.elements(xml, "block-definition").map(SnapStateConversion.renderDefinition)
    val variables = SnapXmlParser.elements(xml, "variables").flatMap(container => SnapXmlParser.children(container.inner))
      .filter(_.tag == "variable").groupBy(_.attrOrEmpty("name")).toVector.map { (name, values) =>
        val literal = SnapXmlParser.children(values.head.inner).headOption.map(v => SnapXmlParser.unescape(v.inner)).getOrElse("0")
        val value = if literal.toDoubleOption.nonEmpty then literal else if literal == "true" then "True" else if literal == "false" then "False" else ujson.write(literal)
        s"$name = $value"
      }
    val scripts = SnapXmlParser.elements(xml, "scripts").flatMap(container => SnapXmlParser.children(container.inner).filter(_.tag == "script"))
    val (started, loose) = scripts.partition(script => SnapXmlParser.children(script.inner).headOption.exists(_.attrOrEmpty("s") == "receiveGo"))
    def body(scripts: Seq[SnapXmlParser.Element]) = scripts.toList.flatMap(s => SnapStateConversion.statements(s.inner, 0)).mkString("\n")
    val unused = Option.when(loose.nonEmpty)(s"def unusedCode():\n${indent(if body(loose).isEmpty then "pass" else body(loose))}")
    val main = Option.when(started.nonEmpty)(s"def main():\n${indent(if body(started).isEmpty then "pass" else body(started))}\n\nmain()")
    new ProgrammingStatePythonStructured(Map.empty, join(variables ++ definitions ++ unused.toList ++ main.toList))
  }

  def toSnap(state: ProgrammingStatePythonStructured): ProgrammingStateSnapXml = {
    // Classes deliberately have no Snap representation; only module/static scope is exported.
    val source = moduleSource(state)
    val loose = pythonBlocks(source, "def").find(f => f.indent == 0 && BeEntityName.fromCodeString(f.name).universalInterpretation() == "unused_code")
    val active = loose.fold(source)(block => remove(source, Seq(block.start -> block.end)))
    val parsed = statements(PythonToExpression.program(active).fullProgram)
    // Initial module literals are saved project variables, not extra startup blocks.
    def saveInitializers(nodes: List[BeExpression], names: Set[BeEntityName]): List[BeExpression] = nodes match {
      case (function: BeDefineFunction) :: rest => function :: saveInitializers(rest, names)
      case BeAssignVariable(target, value @ BeUseValue(_: BeDataValueLiteral, _)) :: rest if !names.contains(target.name) =>
        target.copy(initValue = Some(value)) :: saveInitializers(rest, names + target.name)
      case rest => rest
    }
    val activeNodes = saveInitializers(parsed, Set.empty)
    val looseNodes = loose.toList.flatMap(block => statements(PythonToExpression.program(block.body).fullProgram))
    val expression = BeStartProgram(BeSequence.optionalBody(activeNodes ++ looseNodes))
    SnapExpressionSupport.validateSubset(expression).fold(error => throw IllegalArgumentException(error), identity)
    val mainCount = SnapExpressionSupport.scriptStatementCount(activeNodes)
    val looseCount = SnapExpressionSupport.scriptStatementCount(looseNodes)
    val layout = SnapCanvasLayout(List(
      Option.when(mainCount > 0)(SnapCanvasScript(156, 66, mainCount, hasReceiveGo = true)),
      Option.when(looseCount > 0)(SnapCanvasScript(156, 350, looseCount, hasReceiveGo = false))).flatten)
    ProgrammingStateSnapXml(SnapProjectXml.toXml(expression, canvasLayout = layout))
  }
}
