package it.evadid.vm.parsing.source

import it.evadid.vm.conversion.dependencies.KnownDependencies
import it.evadid.vm.parsing.source.SourceTokens.*

/** Builds [[JavaSourceStructured]] from the lexer's tokens; it accepts any text. */
private[source] object JavaStructureStep {
  private val classModifiers = Set("public", "private", "protected", "static", "final", "abstract", "strictfp", "sealed", "non-sealed")
  private val memberModifiers = classModifiers ++ Set("synchronized", "native", "default", "transient", "volatile")
  private val statementKeywords = Set("return", "new", "throw", "else", "case", "yield", "class", "interface", "enum", "record")

  def apply(source: String, tokens: Seq[Token]): JavaSourceStructured = {
    val lines = SourceLines(source)
    def span(start: Int, end: Int) = lines.span(start, end)
    def text(start: Int, end: Int) = source.substring(start, end)

    def classesIn(values: Seq[Token], outer: Option[String]): Vector[JavaClassStructured] = {
      val code = significant(values).toVector
      val declared = code.indices.flatMap { index =>
        (code(index), code.lift(index + 1)) match {
          case (Word(classStart, _, "class"), Some(Word(_, _, name))) =>
            val modifiers = code.take(index).reverse.takeWhile {
              case Word(_, _, modifier) => classModifiers(modifier)
              case _ => false
            }.reverse
            val start = modifiers.headOption.map(_.start).getOrElse(classStart)
            val body = code.drop(index + 2).takeWhile { case Symbol(_, _, ";") => false; case _ => true }
              .collectFirst { case group: Group if group.open == '{' => group }
            val end = body.fold(source.length)(_.end)
            val classCode = text(start, end)
            Some(JavaClassStructured(name, modifiers.collect { case Word(_, _, word) => word }.toVector, span(start, end), classCode,
              text(start, body.fold(end)(_.start)), body.map(group => span(group.start + 1, if group.closed then group.end - 1 else group.end)),
              body.exists(_.closed), body.toVector.flatMap(group => members(name, group.children)),
              library = KnownDependencies.isJavaDefinition(classCode), outer) -> body)
          case _ => None
        }
      }.toVector
      val bodies = declared.flatMap(_._2)
      // A class body is searched with its class as the outer one; other groups keep the current one.
      declared.flatMap((owner, body) => owner +: body.toVector.flatMap(group => classesIn(group.children, Some(owner.name)))) ++
        values.collect { case group: Group if !bodies.exists(_ eq group) => classesIn(group.children, outer) }.flatten
    }

    /** Direct members: a member ends with `;` or with a `{…}` body that is not part of an initializer. */
    def members(className: String, tokens: Seq[Token]): Vector[JavaMemberStructured] = {
      final case class State(done: Vector[Vector[Token]] = Vector.empty, current: Vector[Token] = Vector.empty, assigned: Boolean = false) {
        def add(token: Token): State = copy(current = current :+ token)
        def finish: State = State(done :+ current)
      }
      val state = tokens.foldLeft(State()) { (state, token) => token match {
        case Opaque(_, _, true, _) => state
        case symbol: Symbol if symbol.isSpace => state
        case Symbol(_, _, ";") => state.add(token).finish
        case group: Group if group.open == '{' && !state.assigned => state.add(group).finish
        case Symbol(_, _, "=") => state.add(token).copy(assigned = true)
        case other => state.add(other)
      } }
      (state.done ++ Option.when(state.current.nonEmpty)(state.current)).map(member(className, _))
    }

    def member(className: String, tokens: Vector[Token]): JavaMemberStructured = {
      val start = tokens.head.start
      val end = tokens.last.end
      val memberSpan = span(start, end)
      val code = text(start, end)
      val (annotations, rest) = withoutAnnotations(tokens)
      val modifiers = rest.takeWhile { case Word(_, _, word) => memberModifiers(word); case _ => false }.collect { case Word(_, _, word) => word }
      val header = rest.takeWhile {
        case group: Group => group.open != '{'
        case Symbol(_, _, "=") => false
        case _ => true
      }
      def function: Option[JavaFunctionStructured] = {
        val afterModifiers = header.drop(modifiers.size)
        afterModifiers.indices.collectFirst {
          case index if (afterModifiers(index) match { case group: Group => group.open == '('; case _ => false }) => index
        }.flatMap { parametersIndex =>
          val parameters = afterModifiers(parametersIndex).asInstanceOf[Group]
          afterModifiers.lift(parametersIndex - 1).collect { case word: Word => word }.flatMap { name =>
            val typeTokens = afterModifiers.take(parametersIndex - 1)
            // A method has a type ending in a word, `>` or `[]` before its name; a constructor is named like its class.
            val typed = typeTokens.lastOption.exists {
              case _: Word => true
              case Symbol(_, _, text) => text == ">"
              case group: Group => group.open == '['
              case _ => false
            } && source.substring(typeTokens.last.end, name.start).exists(_.isWhitespace)
            Option.when(typed || (typeTokens.isEmpty && name.text == className)) {
              val bodyGroup = rest.drop(header.size).collectFirst { case group: Group if group.open == '{' => group }
              JavaFunctionStructured(className, name.text, modifiers, annotations,
                Option.when(typed)(text(typeTokens.head.start, typeTokens.last.end)), parametersOf(parameters),
                parameterText(parameters), bodyGroup.filter(_.closed).map(group => SourceText.dedent(text(group.start + 1, group.end - 1))),
                bodyGroup.map(group => span(group.start + 1, if group.closed then group.end - 1 else group.end)),
                parameters.closed && bodyGroup.exists(_.closed), memberSpan, code)
            }
          }
        }
      }
      def field: Option[JavaFieldStructured] = {
        val afterModifiers = rest.drop(modifiers.size)
        val equals = afterModifiers.indexWhere { case Symbol(_, _, "=") => true; case _ => false }
        val target = (if equals < 0 then afterModifiers else afterModifiers.take(equals)).filterNot { case Symbol(_, _, ";") => true; case _ => false }
        target.lastOption match {
          case Some(Word(nameStart, _, name)) if target.size >= 2 =>
            target.head match {
              case Word(typeStart, _, typeWord) if !statementKeywords(typeWord) &&
                  source.substring(target(target.size - 2).end, nameStart).exists(_.isWhitespace) &&
                  target.init.forall { case Word(_, _, _) | Symbol(_, _, "<" | ">" | "," | ".") => true; case group: Group => group.open == '['; case _ => false } =>
                val initializer = Option.when(equals >= 0)(text(afterModifiers(equals).end, end).trim.stripSuffix(";").trim)
                Some(JavaFieldStructured(className, name, modifiers, annotations, text(typeStart, target(target.size - 2).end), initializer,
                  memberSpan, code))
              case _ => None
            }
          case _ => None
        }
      }
      function.orElse(field).getOrElse(JavaOtherMemberStructured(memberSpan, code))
    }

    /** `@Name`, `@a.b.Name` and `@Name(…)`, but not `@interface`. */
    def withoutAnnotations(tokens: Vector[Token]): (Vector[String], Vector[Token]) = {
      def take(remaining: Vector[Token], found: Vector[String]): (Vector[String], Vector[Token]) = remaining match {
        case Symbol(at, _, "@") +: (word @ Word(_, _, name)) +: rest if name != "interface" =>
          val qualified = rest.grouped(2).takeWhile {
            case Vector(Symbol(_, _, "."), _: Word) => true
            case _ => false
          }.flatten.toVector
          val afterName = rest.drop(qualified.size)
          val arguments = afterName.headOption.collect { case group: Group if group.open == '(' => group }
          val end = arguments.map(_.end).orElse(qualified.lastOption.map(_.end)).getOrElse(word.end)
          take(afterName.drop(arguments.size), found :+ text(at, end))
        case head +: rest =>
          val (more, kept) = take(rest, found)
          (more, head +: kept)
        case _ => (found, Vector.empty)
      }
      take(tokens, Vector.empty)
    }

    def parameterText(group: Group): String = text(group.start + 1, if group.closed then group.end - 1 else group.end).trim

    /** Parameters split at commas outside generic `<…>` arguments. */
    def parametersOf(group: Group): Vector[JavaParameterStructured] = {
      val code = significant(group.children).toVector
      val parts = code.foldLeft((Vector.empty[Vector[Token]], Vector.empty[Token], 0)) { case ((done, current, depth), token) =>
        token match {
          case Symbol(_, _, ",") if depth == 0 => (done :+ current, Vector.empty, depth)
          case Symbol(_, _, "<") => (done, current :+ token, depth + 1)
          case Symbol(_, _, ">") => (done, current :+ token, (depth - 1).max(0))
          case other => (done, current :+ other, depth)
        }
      } match { case (done, current, _) => if current.nonEmpty then done :+ current else done }
      parts.flatMap { tokens =>
        val (_, rest) = withoutAnnotations(tokens)
        val modifiers = rest.takeWhile { case Word(_, _, "final") => true; case _ => false }.collect { case Word(_, _, word) => word }
        val declared = rest.drop(modifiers.size)
        declared.lastOption.collect { case Word(nameStart, _, name) if declared.size >= 2 =>
          JavaParameterStructured(name, text(declared.head.start, declared(declared.size - 2).end).trim, modifiers)
        }
      }
    }

    val classes = classesIn(tokens, None).sortBy(_.span.start)
    val topLevel = significant(tokens).toVector
    val classSpans = classes.filter(_.outer.isEmpty).map(owner => owner.span.start -> owner.span.end)
    val preamble = topLevel.indices.collect {
      case index if (topLevel(index) match { case Word(start, _, "package" | "import") => !classSpans.exists((s, e) => s <= start && start < e); case _ => false }) =>
        val keyword = topLevel(index).asInstanceOf[Word]
        val end = topLevel.drop(index).collectFirst { case Symbol(_, semicolonEnd, ";") => semicolonEnd }.getOrElse(source.length)
        JavaPreambleStructured(keyword.text, text(keyword.start, end), span(keyword.start, end))
    }.toVector
    val declarations = classSpans ++ preamble.map(declaration => declaration.span.start -> declaration.span.end)
    val loose = members("", tokens.filterNot(token => declarations.exists((start, end) => start <= token.start && token.start < end)))
    val libraries = classes.filter(_.library).map(owner => owner.span.start -> owner.span.end)
    val masked = blank(source, libraries)
    val opaque = flatten(tokens).collect { case token: Opaque => token }
    JavaSourceStructured(source, classes, preamble, loose, opaque.collect { case Opaque(start, end, true, true) => span(start, end) }.toVector,
      balanced(tokens), significant(tokens).isEmpty && balanced(tokens), masked, blank(masked, opaque.map(token => token.start -> token.end)))
  }
}
