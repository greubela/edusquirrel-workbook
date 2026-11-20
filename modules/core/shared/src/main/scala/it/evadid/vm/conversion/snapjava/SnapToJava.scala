package it.evadid.vm.conversion.snapjava

import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapGeneratedImages

import it.evadid.vm.conversion.snapexpression.SnapControlFlow
import it.evadid.vm.conversion.snapexpression.SnapCustomBlockRules
import it.evadid.vm.conversion.snapxml.SnapXmlParser

import it.evadid.vm.conversion.javaexpression.checked.JavaTurtleResolution as R
import it.evadid.workbook.elements.interactionElements.programming.state.*

import it.evadid.vm.conversion.javaediting.JavaEditingMetadata.*

object SnapToJava {
  /** Lossless editing conversion: every script must be representable in Java. */
  def fromSnap(state: ProgrammingStateSnapXml): Either[String, ProgrammingStateJavaString] = attempt {
    bounded(state.snapXml)
    fromCleanSnap(state.copy(snapXml = SnapGeneratedImages.remove(state.snapXml)))
  }

  /** Conversion for running the project: like Snap itself, scripts without a green-flag hat are not executed,
   * so they are ignored instead of rejected. The stored project is not changed. */
  def forExecution(state: ProgrammingStateSnapXml): Either[String, ProgrammingStateJavaString] = attempt {
    bounded(state.snapXml)
    fromCleanSnap(state.copy(snapXml = withoutLooseScripts(SnapGeneratedImages.remove(state.snapXml))))
  }

  private def startsWithGreenFlag(script: SnapXmlParser.Element): Boolean =
    script.tag == "script" && SnapXmlParser.children(script.inner).headOption
      .exists(block => block.tag == "block" && block.attrOrEmpty("s") == "receiveGo")

  private def withoutLooseScripts(xml: String): String =
    SnapXmlParser.elements(xml, "scripts").filter(_.inner.nonEmpty).reverse.foldLeft(xml) { (current, container) =>
      val innerStart = container.start + container.outer.indexOf('>') + 1
      val kept = SnapXmlParser.children(container.inner).filter(startsWithGreenFlag).map(_.outer).mkString
      current.substring(0, innerStart) + kept + current.substring(innerStart + container.inner.length)
    }

  private def fromCleanSnap(state: ProgrammingStateSnapXml): ProgrammingStateJavaString = {
    val document = SnapXmlParser.children(state.snapXml.trim)
    ensure(document.size == 1 && document.head.tag == "project" && document.head.outer == state.snapXml.trim,
      "Read a complete Snap project; malformed XML cannot be converted.")
    val notes = SnapXmlParser.elements(state.snapXml, "notes").map(node => noteContent(node.inner)).filter(_.startsWith(NotesPrefix)).distinct
    ensure(notes.size == 1, "Exactly one typed Java metadata note is required.")
    val meta = readMetadata(notes.head.substring(NotesPrefix.length))
    ensure(SnapXmlParser.elements(state.snapXml, "scene").size == 1 && SnapXmlParser.elements(state.snapXml, "sprite").size == 1,
      "Only one scene and one Turtle sprite can be converted.")
    // main() is the green-flag script; every other Java method is a global custom command block.
    val helpers = meta.methods.filterNot(_.name == meta.entry.name)
    val definitions = SnapCustomBlockRules.globalDefinitions(state.snapXml)
    ensure(definitions.size == helpers.size && definitions.map(_.spec).distinct.size == definitions.size &&
      SnapCustomBlockRules.localDefinitions(state.snapXml).isEmpty, "Every Java helper method must have one global command block.")
    ensure(SnapCustomBlockRules.obsoleteCalls(state.snapXml).isEmpty, "A custom block call has no matching definition.")
    val allowedTags = Set("project", "notes", "scenes", "scene", "palette", "category", "hidden", "headers", "code", "blocks", "primitives",
      "stage", "costumes", "sounds", "list", "variables", "sprites", "sprite", "scripts", "script", "creator", "origCreator", "origName",
      "block-definition", "inputs", "input", "block", "custom-block", "l", "bool", "option", "comment", "header", "translations", "thumbnail", "pentrails", "pentrail")
    var xmlNodes = 0
    def checkXml(inner: String, depth: Int, parentTag: String): Unit = {
      ensure(depth <= 64, "Snap XML is too deeply nested for editing conversion.")
      val children = SnapXmlParser.children(inner)
      var consumed = 0
      val gaps = children.map { element =>
        val gap = inner.substring(consumed, element.start)
        consumed = element.end
        gap
      } :+ inner.substring(consumed)
      ensure(gaps.forall(gap => !gap.contains('<') && !gap.contains('>')), "Malformed Snap markup cannot be discarded.")
      val textTags = Set("l", "bool", "option", "input", "creator", "origCreator", "origName")
      ensure(textTags(parentTag) || gaps.forall(_.trim.isEmpty), s"Unexpected text in Snap '$parentTag' cannot be discarded.")
      children.foreach { element =>
        xmlNodes += 1
        ensure(xmlNodes <= 50000, "Snap XML has too many editing elements.")
        ensure(allowedTags(element.tag), s"Unknown Snap element '${element.tag}' cannot be discarded.")
        val managedAttributes = element.tag match {
          case "block" => Some(Set("s", "var", "id"))
          case "custom-block" => Some(Set("s", "id"))
          case "script" => Some(Set("x", "y", "id"))
          case "project" => Some(Set("name", "app", "version"))
          case "block-definition" => Some(Set("s", "type", "category", "helper", "space", "strict"))
          case "input" => Some(Set("type"))
          case _ => None
        }
        ensure(managedAttributes.forall(element.attributes.keySet.subsetOf), s"Unknown '${element.tag}' metadata cannot be discarded.")
        if Set("code", "headers", "primitives")(element.tag) then ensure(element.inner.trim.isEmpty, "Embedded Snap code is not part of the Java editing subset.")
        if Set("comment", "header", "translations")(element.tag) then ensure(element.inner.trim.isEmpty,
          s"Authored Snap ${element.tag} content cannot be discarded during Java conversion.")
        if element.tag == "notes" then ensure(element.inner.trim.isEmpty || noteContent(element.inner).startsWith(NotesPrefix),
          "Authored Snap notes cannot be discarded during Java conversion.")
        if element.tag == "notes" then ensure(Set("project", "scene")(parentTag), "Keep Java metadata notes on the project or its scene.")
        if element.tag == "input" then ensure(element.inner.trim.isEmpty ||
          element.attrOrEmpty("type") == "%n" && Set("0", "0.0")(element.inner.trim) ||
          element.attrOrEmpty("type") == "%b" && element.inner.trim == "false", "Authored custom input defaults cannot be discarded.")
        if element.tag != "notes" && element.tag != "thumbnail" && element.tag != "pentrail" then checkXml(element.inner, depth + 1, element.tag)
      }
      if inner.contains('<') then ensure(children.nonEmpty, "Malformed Snap XML cannot be converted.")
    }
    checkXml(state.snapXml, 0, "document")
    def children(element: SnapXmlParser.Element, count: Int): List[SnapXmlParser.Element] = {
      val result = SnapXmlParser.children(element.inner)
      ensure(result.size == count, s"The '${element.attrOrEmpty("s")}' block has the wrong input count.")
      result
    }
    def text(element: SnapXmlParser.Element): String = { ensure(element.tag == "l" && !element.inner.contains('<'), "Expected a literal block input."); SnapXmlParser.unescape(element.inner) }
    // Standard Snap blocks only. Java types follow from the variable metadata and literal spelling (`2` int, `2.0` double);
    // the generated Java then applies Java semantics, e.g. int division for two int operands.
    def arithmeticValue(token: String, left: Value, right: Value): Value = {
      ensure(numeric(left.kind) && numeric(right.kind), "Arithmetic blocks need numbers, not true/false.")
      Value(s"(${left.code} $token ${right.code})",
        if left.kind == R.ValueType.DoubleValue || right.kind == R.ValueType.DoubleValue then R.ValueType.DoubleValue else R.ValueType.IntValue)
    }
    def operands(element: SnapXmlParser.Element, method: Method): List[Value] = {
      val containers = children(element, 1)
      ensure(containers.head.tag == "list", "Use the operand slots of the Snap block.")
      val values = SnapXmlParser.children(containers.head.inner).map(expression(_, method))
      ensure(values.nonEmpty, "Fill in the operand slots of the Snap block.")
      values
    }
    def expression(element: SnapXmlParser.Element, method: Method): Value = {
      element.attr("var") match {
        case Some(name) =>
          ensure(element.tag == "block" && element.inner.trim.isEmpty, "A variable reporter has no inputs.")
          val variable = method.variable(name)
          Value(variable.name, variable.kind)
        case None if element.tag == "l" =>
          val literals = SnapXmlParser.children(element.inner)
          if literals.nonEmpty then {
            ensure(literals.size == 1 && literals.head.tag == "bool" && Set("true", "false")(literals.head.inner), "Use a scalar boolean literal.")
            Value(literals.head.inner, R.ValueType.BooleanValue)
          } else {
            val raw = text(element).trim
            ensure(raw.nonEmpty, "Fill in every empty number slot.")
            number(raw, snapLiteral = true)
          }
        case None if element.tag == "block" =>
          element.attrOrEmpty("s") match {
            case "reportTrue" | "reportFalse" => ensure(children(element, 0).isEmpty, "A boolean reporter has no inputs."); Value(if element.attrOrEmpty("s") == "reportTrue" then "true" else "false", R.ValueType.BooleanValue)
            case "reportBoolean" =>
              val value = expression(children(element, 1).head, method)
              ensure(value.kind == R.ValueType.BooleanValue, "Use a scalar boolean constant.")
              value
            case "reportVariadicSum" => operands(element, method).reduceLeft(arithmeticValue("+", _, _))
            case "reportVariadicProduct" => operands(element, method).reduceLeft(arithmeticValue("*", _, _))
            case selector @ ("reportDifference" | "reportQuotient" | "reportModulus") =>
              val args = children(element, 2)
              val token = SnapControlFlow.OperatorToSnapReporter.find(_._2.selector == selector).get._1
              arithmeticValue(token, expression(args.head, method), expression(args(1), method))
            case "reportMonadic" =>
              val args = children(element, 2)
              ensure(args.head.tag == "l" && SnapXmlParser.child(args.head.inner, "option").exists(_.inner == "neg"),
                "Only the 'neg' function of the Snap function block is available in Java tasks.")
              val value = expression(args(1), method)
              ensure(numeric(value.kind), "Only numbers can be negated.")
              Value(s"(- ${value.code})", value.kind)
            case "reportNot" => logical("!", expression(children(element, 1).head, method), None)
            case selector @ ("reportVariadicAnd" | "reportVariadicOr") =>
              operands(element, method).reduceLeft((left, right) => logical(if selector == "reportVariadicAnd" then "&&" else "||", left, Some(right)))
            case selector if SnapControlFlow.ConditionSelectors(selector) =>
              val args = operands(element, method); ensure(args.size == 2, "Compare exactly two values.")
              val token = SnapControlFlow.OperatorToSnapReporter.find(_._2.selector == selector).map(_._1).getOrElse(reject("Unknown condition reporter."))
              logical(token, args.head, Some(args(1)))
            case selector => reject(s"The Snap block '$selector' is not available in Java tasks.")
          }
        case _ => reject("Unsupported Snap value input.")
      }
    }
    // A repeat becomes `for (int i = 0; i < count; i++)`, which JavaToSnap shows as repeat again.
    // Counters are scoped to their loop, so only enclosing counters and method names must be avoided.
    def repeatCounter(method: Method, enclosing: Set[String]): String = {
      val taken = method.variables.map(_.name).toSet ++ meta.methods.map(_.name) ++ enclosing
      (Iterator("i", "j", "k") ++ Iterator.from(1).map(index => s"repeat_$index")).find(name => !taken(name)).get
    }
    var repeatLimits = 0
    def repeatLimit(method: Method): String = {
      repeatLimits += 1
      val name = s"repeat_limit_$repeatLimits"
      if method.variables.exists(_.name == name) || meta.methods.exists(_.name == name) then repeatLimit(method) else name
    }
    def variablesRead(element: SnapXmlParser.Element): Set[String] =
      SnapXmlParser.elements(element.outer, "block").flatMap(_.attr("var")).toSet
    def variablesWritten(element: SnapXmlParser.Element): Set[String] =
      SnapXmlParser.elements(element.outer, "block").filter(block => Set("doSetVar", "doChangeVar")(block.attrOrEmpty("s")))
        .flatMap(block => SnapXmlParser.children(block.inner).headOption.filter(_.tag == "l").map(text)).toSet
    /** Drops one pair of parentheses that encloses the whole generated expression, e.g. `(i < 2)` → `i < 2`. */
    def bare(code: String): String = {
      val inner = code.drop(1).dropRight(1)
      val balanced = inner.scanLeft(0)((depth, char) => depth + (if char == '(' then 1 else if char == ')' then -1 else 0))
      if code.startsWith("(") && code.endsWith(")") && balanced.forall(_ >= 0) && balanced.last == 0 then inner else code
    }
    def statements(script: SnapXmlParser.Element, method: Method, level: Int, counters: Set[String] = Set.empty): Vector[String] = {
      ensure(script.tag == "script", "Expected a method script.")
      commands(SnapXmlParser.children(script.inner), method, level, counters)
    }
    def commands(elements: List[SnapXmlParser.Element], method: Method, level: Int, counters: Set[String] = Set.empty): Vector[String] = {
      val prefix = "    " * level
      elements.toVector.flatMap { element =>
        if element.tag == "custom-block" then {
          val called = helpers.find(_.callSpec == element.attrOrEmpty("s")).getOrElse(reject("Unknown Java custom block identity."))
          Vector(prefix + call(meta, called.name, children(element, called.parameters.size).map(expression(_, method)).toVector))
        } else {
          ensure(element.tag == "block", "Unknown Snap command element.")
          element.attrOrEmpty("s") match {
            case "doDeclareVariables" =>
              val list = children(element, 1).head; ensure(list.tag == "list", "Use a Java local variable declaration list.")
              val names = SnapXmlParser.children(list.inner).map(text)
              ensure(names.distinct.size == names.size && names.forall(name => !method.variable(name).parameter), "Declare each Java local exactly once.")
              names.toVector.map(name => prefix + s"${kindName(method.variable(name).kind)} $name;")
            case "doSetVar" =>
              val args = children(element, 2); val variable = method.variable(text(args.head)); val value = expression(args(1), method)
              ensure(assignable(value.kind, variable.kind), "The assigned value has the wrong Java type.")
              Vector(prefix + s"${variable.name} = ${value.code};")
            case "doChangeVar" =>
              val args = children(element, 2); val variable = method.variable(text(args.head))
              val value = arithmeticValue("+", Value(variable.name, variable.kind), expression(args(1), method))
              ensure(assignable(value.kind, variable.kind), "Change an int variable only by a whole number.")
              Vector(prefix + s"${variable.name} = ${value.code};")
            case "doRepeat" =>
              val args = children(element, 2); val count = expression(args.head, method)
              ensure(count.kind == R.ValueType.IntValue, "Repeat needs a whole number.")
              val counter = repeatCounter(method, counters)
              val body = statements(args(1), method, level + 1, counters + counter) ++ Vector(prefix + "}")
              // Snap evaluates the count once. Java re-evaluates the loop condition, so a count whose variables
              // change inside the loop is first copied into a limit local.
              if (variablesRead(args.head) & variablesWritten(args(1))).isEmpty then
                Vector(prefix + s"for (int $counter = 0; $counter < ${count.code}; $counter++) {") ++ body
              else {
                val limit = repeatLimit(method)
                Vector(prefix + s"int $limit = ${count.code};", prefix + s"for (int $counter = 0; $counter < $limit; $counter++) {") ++ body
              }
            case selector if R.TurtleCommand.bySnapSelector.contains(selector) =>
              val command = R.TurtleCommand.bySnapSelector(selector)
              Vector(prefix + call(meta, R.TurtleCommand.qualified(command),
                children(element, command.arity).map(expression(_, method)).toVector))
            case "doStopThis" =>
              val input = children(element, 1).head
              ensure(input.tag == "l" && SnapXmlParser.child(input.inner, "option").exists(option => Set("this block", "this script")(option.inner)),
                "Only 'stop this block' or 'stop this script' represents Java return.")
              Vector(prefix + "return;")
            case "doIf" | "doIfElse" =>
              val args = children(element, 3)
              val isElse = element.attrOrEmpty("s") == "doIfElse"
              if !isElse then ensure(args(2).tag == "list" && args(2).inner.trim.isEmpty, "Additional Snap elseif slots cannot be discarded.")
              val condition = expression(args.head, method); ensure(condition.kind == R.ValueType.BooleanValue, "Use a boolean if condition.")
              Vector(prefix + s"if (${bare(condition.code)}) {") ++ statements(args(1), method, level + 1, counters) ++ Vector(prefix + "}") ++
                (if !isElse then Vector.empty else Vector(prefix + "else {") ++ statements(args(2), method, level + 1, counters) ++ Vector(prefix + "}"))
            case "doUntil" =>
              // `repeat until not c` is `while (c)`; anything else is `while (!(c))`.
              val args = children(element, 2)
              val negated = args.head.tag == "block" && args.head.attrOrEmpty("s") == "reportNot"
              val condition = expression(if negated then children(args.head, 1).head else args.head, method)
              ensure(condition.kind == R.ValueType.BooleanValue, "Use a boolean loop condition.")
              val test = if negated then bare(condition.code) else s"!(${bare(condition.code)})"
              Vector(prefix + s"while ($test) {") ++ statements(args(1), method, level + 1, counters) ++ Vector(prefix + "}")
            case selector => reject(s"The Snap block '$selector' is not available in Java tasks.")
          }
        }
      }
    }
    val helperBodies = definitions.map { definition =>
      val method = helpers.find(_.spec == definition.spec).getOrElse(reject("The custom block declaration no longer matches its Java identity."))
      ensure(definition.isGlobal && definition.blockType == "command" && definition.slotNames == method.parameters.map(_.name).toList &&
        method.parameters.forall(parameter => definition.typeOf(parameter.name) == (if parameter.kind == R.ValueType.BooleanValue then "%b" else "%n")),
        "Keep the typed scalar Java block parameters.")
      ensure(definition.rawCategory == "variables" || definition.rawCategory == "Variables" &&
        SnapCustomBlockRules.paletteCategories(state.snapXml).contains("Variables"),
        "A custom block category cannot be discarded during Java conversion.")
      ensure(definition.element.attributes.keySet.subsetOf(Set("s", "type", "category", "helper", "space", "strict")),
        "Unknown or executable custom-block metadata cannot be discarded.")
      val scripts = SnapXmlParser.children(definition.element.inner).filter(_.tag == "script")
      ensure(scripts.size <= 1 && SnapXmlParser.children(definition.element.inner).filter(_.tag == "scripts").forall(_.inner.trim.isEmpty),
        "Loose scripts in a custom block editor cannot be discarded.")
      method.name -> scripts.headOption.fold(Vector.empty[String])(script => statements(script, method, 0))
    }.toMap
    val sprite = SnapXmlParser.elements(state.snapXml, "sprite").head
    ensure(SnapXmlParser.children(sprite.inner).count(_.tag == "scripts") == 1,
      "Multiple sprite script containers cannot be discarded.")
    val startup = SnapXmlParser.child(sprite.inner, "scripts").toList.flatMap(container => SnapXmlParser.children(container.inner))
    ensure(startup.forall(startsWithGreenFlag),
      "Blocks outside the green-flag script are not part of main(). Attach them below the green flag or delete them.")
    ensure(startup.size <= 1, "Keep only one green-flag script; it is the Java main() method.")
    // Without a green-flag script main() is empty. Java always starts with a reset turtle.
    val mainBlocks = startup.flatMap(script => SnapXmlParser.children(script.inner)) match {
      case Nil => Nil
      case hat :: body =>
        ensure(hat.inner.trim.isEmpty, "The green-flag block has no inputs.")
        body
    }
    val stage = SnapXmlParser.elements(state.snapXml, "stage").head
    ensure(SnapXmlParser.child(stage.inner, "scripts").forall(_.inner.trim.isEmpty), "Additional stage scripts cannot be discarded.")
    ensure(SnapXmlParser.elements(state.snapXml, "scripts").count(_.inner.trim.nonEmpty) <= startup.size,
      "Additional or loose script containers cannot be discarded.")
    finish(meta, helperBodies + (meta.entry.name -> commands(mainBlocks, meta.entry, 0)))
  }
}
