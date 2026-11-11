package it.evadid.vm.conversion.snapjava

import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapGeneratedImages

import it.evadid.vm.conversion.snapexpression.SnapControlFlow
import it.evadid.vm.conversion.snapexpression.SnapCustomBlockRules
import it.evadid.vm.conversion.snapxml.SnapXmlParser

import it.evadid.vm.conversion.javaexpression.checked.JavaTurtleResolution as R
import it.evadid.workbook.elements.interactionElements.programming.state.*

import it.evadid.vm.conversion.javaediting.JavaEditingMetadata.*

object SnapToJava {
  def fromSnap(state: ProgrammingStateSnapXml): Either[String, ProgrammingStateJavaString] = attempt {
    bounded(state.snapXml)
    fromCleanSnap(state.copy(snapXml = SnapGeneratedImages.remove(state.snapXml)))
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
    val definitions = SnapCustomBlockRules.globalDefinitions(state.snapXml)
    ensure(definitions.size == meta.methods.size && definitions.map(_.spec).distinct.size == definitions.size &&
      SnapCustomBlockRules.localDefinitions(state.snapXml).isEmpty, "Every Java method must have one global command block.")
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
    def expression(element: SnapXmlParser.Element, method: Method): Value = {
      element.attr("var") match {
        case Some(name) =>
          val variable = method.variable(name)
          ensure(element.tag == "block" && element.inner.trim.isEmpty && variable.kind == R.ValueType.BooleanValue,
            "Use a typed Java numeric read block instead of a raw numeric variable reporter.")
          Value(name, variable.kind)
        case None if element.tag == "l" =>
          val literals = SnapXmlParser.children(element.inner)
          if literals.nonEmpty then {
            ensure(literals.size == 1 && literals.head.tag == "bool" && Set("true", "false")(literals.head.inner), "Use a scalar boolean literal.")
            Value(literals.head.inner, R.ValueType.BooleanValue)
          } else number(text(element).trim, snapLiteral = true)
        case None if element.tag == "block" =>
          element.attrOrEmpty("s") match {
            case "reportTrue" | "reportFalse" => ensure(children(element, 0).isEmpty, "A boolean reporter has no inputs."); Value(if element.attrOrEmpty("s") == "reportTrue" then "true" else "false", R.ValueType.BooleanValue)
            case "reportBoolean" =>
              val value = expression(children(element, 1).head, method)
              ensure(value.kind == R.ValueType.BooleanValue, "Use a scalar boolean constant.")
              value
            case "reportJavaRead" =>
              val variable = method.variable(text(children(element, 1).head))
              ensure(numeric(variable.kind), "Read a numeric Java cell with the typed read block.")
              Value(variable.name, variable.kind)
            case "reportJavaInt" | "reportJavaDouble" =>
              val args = children(element, 3)
              typedOperation(text(args.head), expression(args(1), method), expression(args(2), method), element.attrOrEmpty("s") == "reportJavaDouble")
            case "reportJavaCompare" =>
              val args = children(element, 4)
              val floating = expression(args(3), method)
              ensure(floating.kind == R.ValueType.BooleanValue && Set("true", "false")(floating.code), "Use a fixed boolean numeric-comparison flag.")
              typedComparison(text(args.head), expression(args(1), method), expression(args(2), method), floating.code == "true")
            case "reportJavaUpdate" =>
              val args = children(element, 4); val target = method.variable(text(args.head)); val delta = text(args(1)).trim
              ensure(Set("1", "-1")(delta) && numeric(target.kind), "Update a numeric Java variable by one.")
              val prefix = expression(args(2), method); val floating = expression(args(3), method)
              ensure(Set("true", "false")(prefix.code) && floating.code == (if target.kind == R.ValueType.DoubleValue then "true" else "false"), "The update flags do not match its type.")
              val token = if delta == "1" then "++" else "--"
              Value(if prefix.code == "true" then s"($token${target.name})" else s"(${target.name}$token)", target.kind)
            case "reportNot" => logical("!", expression(children(element, 1).head, method), None)
            case selector if SnapControlFlow.ConditionSelectors(selector) =>
              val containers = children(element, 1); ensure(containers.head.tag == "list", "Use a variadic condition input list.")
              val args = SnapXmlParser.children(containers.head.inner); ensure(args.size == 2, "Use exactly two condition operands.")
              val token = SnapControlFlow.OperatorToSnapReporter.find(_._2.selector == selector).map(_._1).getOrElse(reject("Unknown condition reporter."))
              val left = expression(args.head, method); val right = expression(args(1), method)
              ensure(left.kind == R.ValueType.BooleanValue && right.kind == R.ValueType.BooleanValue && Set("and", "or", "==", "!=")(token),
                "Use a typed Java comparison block for numeric comparisons.")
              logical(if token == "and" then "&&" else if token == "or" then "||" else token, left, Some(right))
            case selector => reject(s"Unsupported Snap value block '$selector'. Use a typed Java arithmetic block.")
          }
        case _ => reject("Unsupported Snap value input.")
      }
    }
    def stored(element: SnapXmlParser.Element, expected: R.ValueType, method: Method): Value = {
      if numeric(expected) then {
        ensure(element.tag == "block" && element.attrOrEmpty("s") == "reportJavaCell", "Store and pass numeric Java values in a typed cell block.")
        val value = expression(children(element, 1).head, method)
        ensure(numeric(value.kind), "A numeric Java cell cannot contain a boolean.")
        value
      } else expression(element, method)
    }
    def statements(script: SnapXmlParser.Element, method: Method, level: Int): Vector[String] = {
      ensure(script.tag == "script", "Expected a method script.")
      val prefix = "    " * level
      SnapXmlParser.children(script.inner).toVector.flatMap { element =>
        if element.tag == "custom-block" then {
          val called = meta.methods.find(_.callSpec == element.attrOrEmpty("s")).getOrElse(reject("Unknown Java custom block identity."))
          Vector(prefix + call(meta, called.name, children(element, called.parameters.size).zip(called.parameters)
            .map((value, parameter) => stored(value, parameter.kind, method)).toVector))
        } else {
          ensure(element.tag == "block", "Unknown Snap command element.")
          element.attrOrEmpty("s") match {
            case "doDeclareVariables" =>
              val list = children(element, 1).head; ensure(list.tag == "list", "Use a Java local variable declaration list.")
              val names = SnapXmlParser.children(list.inner).map(text)
              ensure(names.distinct.size == names.size && names.forall(name => !method.variable(name).parameter), "Declare each Java local exactly once.")
              names.toVector.map(name => prefix + s"${kindName(method.variable(name).kind)} $name;")
            case "doSetVar" =>
              val args = children(element, 2); val variable = method.variable(text(args.head)); val value = stored(args(1), variable.kind, method)
              ensure(assignable(value.kind, variable.kind), "The assigned value has the wrong Java type.")
              Vector(prefix + s"${variable.name} = ${value.code};")
            case "doJavaForward" | "doJavaTurnRight" =>
              Vector(prefix + call(meta, if element.attrOrEmpty("s") == "doJavaForward" then "Turtle.forward" else "Turtle.turnRight",
                children(element, 1).map(expression(_, method)).toVector))
            case "doStopThis" =>
              val input = children(element, 1).head
              ensure(input.tag == "l" && SnapXmlParser.child(input.inner, "option").exists(_.inner == "this block"), "Only 'stop this block' represents Java return.")
              Vector(prefix + "return;")
            case "doIf" | "doIfElse" =>
              val args = children(element, 3)
              val isElse = element.attrOrEmpty("s") == "doIfElse"
              if !isElse then ensure(args(2).tag == "list" && args(2).inner.trim.isEmpty, "Additional Snap elseif slots cannot be discarded.")
              val condition = expression(args.head, method); ensure(condition.kind == R.ValueType.BooleanValue, "Use a boolean if condition.")
              Vector(prefix + s"if (${condition.code}) {") ++ statements(args(1), method, level + 1) ++ Vector(prefix + "}") ++
                (if !isElse then Vector.empty else Vector(prefix + "else {") ++ statements(args(2), method, level + 1) ++ Vector(prefix + "}"))
            case "doUntil" =>
              val args = children(element, 2); val condition = expression(args.head, method)
              ensure(condition.kind == R.ValueType.BooleanValue, "Use a boolean loop condition.")
              Vector(prefix + s"while (!(${condition.code})) {") ++ statements(args(1), method, level + 1) ++ Vector(prefix + "}")
            case selector => reject(s"Unsupported Snap command '$selector'.")
          }
        }
      }
    }
    val bodies = definitions.map { definition =>
      val method = meta.methods.find(_.spec == definition.spec).getOrElse(reject("The custom block declaration no longer matches its Java identity."))
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
    ensure(startup.size == 1 && startup.head.tag == "script", "Keep exactly one startup script; loose scripts cannot be discarded.")
    val startBlocks = SnapXmlParser.children(startup.head.inner)
    ensure(startBlocks.size == 3 && startBlocks.head.tag == "block" && startBlocks.head.attrOrEmpty("s") == "receiveGo" && startBlocks.head.inner.trim.isEmpty &&
      startBlocks(1).tag == "block" && startBlocks(1).attrOrEmpty("s") == "doJavaReset" && startBlocks(1).inner.trim.isEmpty &&
      startBlocks(2).tag == "custom-block" && startBlocks(2).attrOrEmpty("s") == meta.entry.callSpec && startBlocks(2).inner.trim.isEmpty,
      "Keep the green-flag reset and main() startup script.")
    val stage = SnapXmlParser.elements(state.snapXml, "stage").head
    ensure(SnapXmlParser.child(stage.inner, "scripts").forall(_.inner.trim.isEmpty), "Additional stage scripts cannot be discarded.")
    ensure(SnapXmlParser.elements(state.snapXml, "scripts").count(_.inner.trim.nonEmpty) == 1,
      "Additional or loose script containers cannot be discarded.")
    finish(meta, bodies)
  }
}
