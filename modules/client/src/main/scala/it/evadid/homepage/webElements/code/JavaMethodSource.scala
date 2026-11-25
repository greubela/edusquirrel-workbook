package it.evadid.homepage.webElements.code

import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.vm.conversion.dependencies.KnownDependencies
import it.evadid.vm.conversion.structured.SourceStructure
import it.evadid.vm.conversion.structured.SourceStructure.{ClassOutline, MemberOutline, Param, Visibility}

/** One immutable source analysis shared by method selection and the class diagram, in `dialect`'s language. */
private[code] final case class JavaMethodSource(code: String, dialect: StructureDialect = StructureDialect.JavaDialect) {
  private lazy val analysed = dialect.analyse(code)
  lazy val classes: List[JavaMethodSource.JavaClass] = analysed._1
  /** Runtime classes such as the generated `Turtle`, with their public members only. */
  lazy val libraries: List[JavaMethodSource.JavaClass] = analysed._2
  lazy val relations: List[JavaMethodSource.Relation] = JavaMethodSource.relations(code, classes, libraries, dialect.python)
  lazy val balanced: Boolean = dialect.balanced(code)
  def initialMethod(requirePublicMain: Boolean): Option[JavaMethodSource.JavaMethod] =
    dialect.entryMethod(code, classes, balanced, requirePublicMain)
}

object JavaMethodSource {

  /** An operation: a method or a constructor. Its range spans the whole declaration including the body. */
  private[code] final case class JavaMethod(
                                               className: String,
                                               name: String,
                                               returnType: String,
                                               parameters: String,
                                               start: Int,
                                               end: Int,
                                               visibility: Visibility = Visibility.Public,
                                               isStatic: Boolean = false,
                                               isConstructor: Boolean = false,
                                               isAbstract: Boolean = false,
                                               params: List[Param] = Nil
                                             )

  /** An attribute. `shared` marks declarations such as `int a, b;` whose range declares several fields. */
  private[code] final case class JavaField(
                                              className: String,
                                              visibility: Visibility,
                                              isStatic: Boolean,
                                              isFinal: Boolean,
                                              javaType: String,
                                              name: String,
                                              initializer: Option[String],
                                              start: Int,
                                              end: Int,
                                              shared: Boolean
                                            )

  private[code] final case class JavaClass(
                                              name: String,
                                              bodyStart: Int,
                                              bodyEnd: Int,
                                              methods: List[JavaMethod],
                                              fields: List[JavaField] = Nil,
                                              isInterface: Boolean = false,
                                              isAbstract: Boolean = false,
                                              superclasses: List[String] = Nil,
                                              interfaces: List[String] = Nil,
                                              library: Boolean = false,
                                              /** Python's module-level functions; the box has no name of its own. */
                                              isModule: Boolean = false
                                            )

  private[code] enum RelationKind {
    case Inheritance, Realization, Association, Dependency
  }

  /** A UML relationship between two diagram classes; associations carry the attribute as role. */
  private[code] final case class Relation(from: String, to: String, kind: RelationKind, role: Option[String] = None, many: Boolean = false)

  private[code] def methodKey(function: JavaMethod): String =
    s"${function.className}:${function.start}:${function.end}"

  private[code] final case class MethodRange(start: Int, end: Int) {
    def isWithin(source: String): Boolean = start >= 0 && end >= start && end <= source.length

    def replace(source: String, draft: String): Option[(String, MethodRange)] =
      Option.when(isWithin(source))(
        (source.substring(0, start) + draft + source.substring(end), MethodRange(start, start + draft.length))
      )
  }

  private[code] def firstLineNumber(source: String, range: Option[MethodRange]): Int =
    range.filter(_.isWithin(source)).fold(1) { current =>
      1 + (0 until current.start).count { index =>
        source.charAt(index) == '\n' || source.charAt(index) == '\r' &&
          (index + 1 == source.length || source.charAt(index + 1) != '\n')
      }
    }

  private[code] def parses(code: String): Boolean =
    JavaParser.parse(code).isRight && structurallyBalanced(code)

  private[code] def initialMethod(source: String, requirePublicMain: Boolean = true): Option[JavaMethod] = {
    JavaMethodSource(source).initialMethod(requirePublicMain)
  }

  private[code] def javaEntryMethod(source: String, currentClasses: List[JavaClass], balanced: Boolean,
      requirePublicMain: Boolean): Option[JavaMethod] = {
    if !balanced then None
    else {
      val entryClasses = if !requirePublicMain && currentClasses.exists(_.name == "Main") then
        currentClasses.filter(_.name == "Main") else currentClasses
      entryClasses.flatMap(_.methods).filter(method => method.name == "main" && !method.isConstructor) match
        case List(function) if function.returnType == "void" &&
          SourceStructure.declaration(function.parameters).exists(parameter =>
            Set("String[]", "java.lang.String[]")(parameter.javaType.filterNot(_.isWhitespace))) &&
          findSelection(currentClasses, function).isDefined =>
          val modifiers = SourceStructure.header(source.substring(function.start, function.end)).collect {
            case SourceStructure.Word(_, _, word) => word
          }.toSet
          Option.when((!requirePublicMain || modifiers.contains("public")) && modifiers.contains("static"))(function)
        case _ => None
    }
  }

  private[code] def structurallyBalanced(source: String): Boolean = SourceStructure.balanced(source)

  private[code] def insideAny(ranges: Seq[(Int, Int)], outline: ClassOutline): Boolean =
    ranges.exists((start, end) => start <= outline.bodyOpen && outline.bodyClose < end)

  private[code] def javaClass(outline: ClassOutline, library: Boolean): JavaClass = {
    def shown(visibility: Visibility) = !library || visibility == Visibility.Public
    val declarations = outline.fields.groupBy(field => (field.start, field.end))
    JavaClass(
      name = outline.name,
      bodyStart = outline.bodyOpen,
      bodyEnd = outline.bodyClose,
      methods = outline.members.toList.collect {
        case constructor: MemberOutline.Constructor if shown(constructor.visibility) =>
          JavaMethod(outline.name, constructor.className, "", constructor.parameters, constructor.start, constructor.end,
            constructor.visibility, isConstructor = true, params = constructor.params.toList)
        case method: MemberOutline.Method if shown(method.visibility) =>
          JavaMethod(outline.name, method.name, method.returnType, method.parameters, method.start, method.end,
            method.visibility, method.isStatic, isAbstract = method.isAbstract, params = method.params.toList)
      },
      fields = outline.fields.toList.filter(field => shown(field.visibility)).map { field =>
        JavaField(outline.name, field.visibility, field.isStatic, field.isFinal, field.javaType, field.name, field.initializer,
          field.start, field.end, shared = declarations((field.start, field.end)).size > 1)
      },
      isInterface = outline.isInterface,
      isAbstract = outline.isAbstract,
      superclasses = outline.extendsNames.toList,
      interfaces = outline.implementsNames.toList,
      library = library
    )
  }

  /** Classes with closed bodies and their operations, from the shared fastparse source lexer. Known
    * library classes, such as the generated `Turtle`, belong to the runtime and are not listed.
    */
  private[code] def discover(source: String): List[JavaClass] = JavaMethodSource(source).classes

  /** Identifiers in code, without comments and string literals. */
  private def words(text: String, python: Boolean = false): Set[String] = {
    def walk(tokens: Seq[SourceStructure.Token]): Seq[String] = tokens.flatMap {
      case SourceStructure.Word(_, _, word) => Seq(word)
      case group: SourceStructure.Group => walk(group.children)
      case _ => Nil
    }
    walk(SourceStructure.tokens(text, python)).toSet
  }

  private val collections = Set("List", "ArrayList", "LinkedList", "Set", "HashSet", "TreeSet", "Collection", "Queue", "Deque",
    "ArrayDeque", "list", "set", "tuple", "dict")

  private def relations(source: String, classes: List[JavaClass], libraries: List[JavaClass], python: Boolean): List[Relation] = {
    val names = (classes ++ libraries).filterNot(_.isModule).map(_.name).toSet
    classes.flatMap { clazz =>
      val generalizations = clazz.superclasses.filter(names).map(Relation(clazz.name, _, RelationKind.Inheritance)) ++
        clazz.interfaces.filter(names).map(Relation(clazz.name, _, RelationKind.Realization))
      val associations = clazz.fields.flatMap { field =>
        val typeWords = words(field.javaType, python)
        typeWords.filter(name => names(name) && name != clazz.name).toList.sorted.map { target =>
          Relation(clazz.name, target, RelationKind.Association, Some(field.name),
            many = field.javaType.contains("[") || typeWords.exists(collections))
        }
      }
      val related = (generalizations ++ associations).map(_.to).toSet + clazz.name
      // The module box has no body of its own: its functions are what it uses.
      val body = if clazz.isModule then clazz.methods.map(method => source.substring(method.start, method.end)).mkString("\n")
        else source.substring(clazz.bodyStart, math.min(clazz.bodyEnd + 1, source.length))
      val dependencies = words(body, python).filter(name => names(name) && !related(name))
        .toList.sorted.map(Relation(clazz.name, _, RelationKind.Dependency))
      generalizations ++ associations ++ dependencies
    }.distinct
  }

  // UML notation, shown in the class diagram and above the method editor.

  private[code] def umlParameters(params: List[Param]): String =
    params.map(param =>
      if param.name.isEmpty then param.javaType else if param.javaType.isEmpty then param.name else s"${param.name}: ${param.javaType}"
    ).mkString(", ")

  /** `+ name(p: T): R`; constructors and operations without a result (`void`, Python's `None`) have no result type,
    * as usual in school UML. */
  private[code] def umlOperation(method: JavaMethod): String = {
    val result = if method.isConstructor || Set("void", "None", "")(method.returnType) then "" else s": ${method.returnType}"
    s"${method.visibility.symbol} ${method.name}(${umlParameters(method.params)})$result"
  }

  /** `- name: T`; Python attributes without annotation or recognisable value have no type. */
  private[code] def umlAttribute(field: JavaField): String =
    s"${field.visibility.symbol} ${field.name}" + (if field.javaType.isEmpty then "" else s": ${field.javaType}")

  /** `Class.method`, or the bare function name for Python's module functions. */
  private[code] def qualifiedName(method: JavaMethod): String =
    if method.className.isEmpty then method.name else s"${method.className}.${method.name}"

  // Declarations and edits. Every edit returns new source text; callers validate it before committing.

  private[code] def javaModifier(visibility: Visibility): Option[String] = visibility match {
    case Visibility.Public => Some("public")
    case Visibility.Private => Some("private")
    case Visibility.Protected => Some("protected")
    case Visibility.Package => None
  }

  private def withModifiers(visibility: Visibility, isStatic: Boolean, rest: String): String =
    (javaModifier(visibility).toList ++ Option.when(isStatic)("static") :+ rest).mkString(" ")

  private[code] def javaParameters(params: List[Param]): String = params.map(param => s"${param.javaType} ${param.name}").mkString(", ")

  private[code] def methodDeclaration(returnType: String, name: String, parameters: String, isStatic: Boolean,
      visibility: Visibility = Visibility.Public): String =
    withModifiers(visibility, isStatic, s"$returnType $name($parameters)") + " {\n    \n  }"

  private[code] def fieldDeclaration(visibility: Visibility, isStatic: Boolean, javaType: String, name: String, initializer: String): String =
    withModifiers(visibility, isStatic, s"$javaType $name") + (if initializer.trim.isEmpty then "" else s" = ${initializer.trim}") + ";"

  /** A constructor; `assigned` parameters are stored in attributes of the same name. */
  private[code] def constructorDeclaration(className: String, visibility: Visibility, params: List[Param], assigned: List[String]): String = {
    val body = if assigned.isEmpty then "    \n" else assigned.map(name => s"    this.$name = $name;\n").mkString
    withModifiers(visibility, isStatic = false, s"$className(${javaParameters(params)})") + s" {\n$body  }"
  }

  private[code] def lineSeparator(source: String): String =
    "\r\n|\r|\n".r.findFirstIn(source).getOrElse("\n")

  private def withSeparator(declaration: String, newline: String): String = declaration.replaceAll("\r\n|\r|\n", newline)

  private def lineStart(source: String, position: Int): Int =
    source.lastIndexWhere(c => c == '\n' || c == '\r', position - 1) + 1

  /** The indentation of the line a member starts on, or two spaces when code precedes it. */
  private def indentation(source: String, position: Int): String = {
    val prefix = source.substring(lineStart(source, position), position)
    if prefix.forall(c => c == ' ' || c == '\t') then prefix else "  "
  }

  private def insertAfter(source: String, member: (Int, Int), declaration: String, blankLine: Boolean): String = {
    val newline = lineSeparator(source)
    source.substring(0, member._2) + newline + (if blankLine then newline else "") + indentation(source, member._1) +
      withSeparator(declaration, newline) + source.substring(member._2)
  }

  private def insertBefore(source: String, start: Int, declaration: String): String = {
    val newline = lineSeparator(source)
    source.substring(0, start) + withSeparator(declaration, newline) + newline + newline + indentation(source, start) + source.substring(start)
  }

  private[code] def insertMethod(source: String, clazz: JavaClass, declaration: String): String = {
    val beforeClosingBrace = source.substring(0, clazz.bodyEnd)
    val afterClosingBrace = source.substring(clazz.bodyEnd)
    val newline = lineSeparator(source)
    beforeClosingBrace + s"$newline  ${withSeparator(declaration, newline)}$newline" + afterClosingBrace
  }

  /** After the last attribute, else before the first operation, else at the end of the class. */
  private[code] def insertField(source: String, clazz: JavaClass, declaration: String): String =
    clazz.fields.lastOption match {
      case Some(field) => insertAfter(source, field.start -> field.end, declaration, blankLine = false)
      case None => clazz.methods.headOption.fold(insertMethod(source, clazz, declaration))(first => insertBefore(source, first.start, declaration))
    }

  /** After the last constructor, else before the first method, else at the end of the class. */
  private[code] def insertConstructor(source: String, clazz: JavaClass, declaration: String): String =
    clazz.methods.filter(_.isConstructor).lastOption match {
      case Some(constructor) => insertAfter(source, constructor.start -> constructor.end, declaration, blankLine = true)
      case None => clazz.methods.headOption.fold(insertMethod(source, clazz, declaration))(first => insertBefore(source, first.start, declaration))
    }

  private[code] def replaceRange(source: String, start: Int, end: Int, replacement: String): String =
    source.substring(0, start) + replacement + source.substring(end)

  /** Removes a member; a member on its own line takes its line with it. */
  private[code] def removeMember(source: String, start: Int, end: Int): String = {
    val from = lineStart(source, start)
    val ownLine = source.substring(from, start).forall(c => c == ' ' || c == '\t')
    val afterSpaces = source.indexWhere(c => c != ' ' && c != '\t', end) match {
      case -1 => source.length
      case index => index
    }
    val to = if source.startsWith("\r\n", afterSpaces) then afterSpaces + 2
      else if afterSpaces < source.length && (source.charAt(afterSpaces) == '\n' || source.charAt(afterSpaces) == '\r') then afterSpaces + 1
      else afterSpaces
    source.substring(0, if ownLine then from else start) + source.substring(if ownLine then to else afterSpaces)
  }

  private val reserved = Set("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
    "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
    "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected",
    "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "transient", "try", "void", "volatile", "while", "true", "false", "null")

  private[code] def validName(name: String): Boolean =
    name.matches("[A-Za-z_$][A-Za-z0-9_$]*") && !reserved(name)

  /** A type as written in a declaration: a name with optional type arguments, arrays or varargs. */
  private[code] def validType(javaType: String): Boolean = {
    val value = javaType.trim
    value.matches("[A-Za-z_$][A-Za-z0-9_$<>,.?\\[\\] ]*") &&
      value.count(_ == '<') == value.count(_ == '>') && value.count(_ == '[') == value.count(_ == ']') &&
      !Set("void", "class", "new", "return", "static", "public", "private", "protected")(value)
  }

  // Selection follows the source: a method is found again by its class and signature.

  private[code] def replaceMethod(source: String, function: JavaMethod, draft: String): String =
    source.substring(0, function.start) + draft + source.substring(function.end)

  private[code] def findEditedMethod(
                                        source: String,
                                        currentClasses: List[JavaClass],
                                        previous: JavaMethod,
                                        range: MethodRange,
                                        python: Boolean = false
                                      ): Option[JavaMethod] = {
    if !range.isWithin(source) then None
    else currentClasses.filter(_.name == previous.className) match
      case List(clazz) if range.start > clazz.bodyStart && range.end <= clazz.bodyEnd =>
        clazz.methods.filter(function => function.start >= range.start && function.end <= range.end) match
          case List(function) if onlyTrivia(source.substring(range.start, function.start), python) &&
            onlyTrivia(source.substring(function.end, range.end), python) => Some(function)
          case _ => None
      case _ => None
  }

  private[code] def sameMethod(first: JavaMethod, second: JavaMethod): Boolean =
    first.className == second.className && first.name == second.name &&
      first.returnType == second.returnType && first.parameters == second.parameters

  private[code] def findSelection(classes: List[JavaClass], previous: JavaMethod): Option[JavaMethod] =
    classes.filter(_.name == previous.className) match
      case List(clazz) => clazz.methods.filter(sameMethod(_, previous)) match
        case List(function) => Some(function)
        case _ => None
      case _ => None

  /** Only whitespace and closed comments. */
  private def onlyTrivia(source: String, python: Boolean): Boolean =
    SourceStructure.significant(SourceStructure.tokens(source, python)).isEmpty && SourceStructure.balanced(source, python)
}
