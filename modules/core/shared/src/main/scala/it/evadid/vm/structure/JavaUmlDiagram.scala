package it.evadid.vm.structure

import it.evadid.vm.structure.UmlClassDiagram.*
import it.evadid.vm.conversion.structured.SourceStructure
import it.evadid.vm.conversion.structured.SourceStructure.{Group, Symbol, Token, Word}
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaStructured

/** Parses Java declarations into the language-independent UML model. */
object JavaUmlDiagram {
  def apply(state: ProgrammingStateJavaStructured): UmlClassDiagram =
    UmlClassDiagram(state.definedClasses.toSeq
      .map(umlClass))

  /** The box of one class source; its `fullCode` is the declaration without the preamble of imports. */
  private def umlClass(sourceKey: BeEntityName, source: String): UmlClass = {
    val block = SourceStructure.javaClasses(source).headOption
    val code = block.fold(source)(_.source).trim
    val name = block.fold(sourceKey.universalInterpretation())(_.name)
    val parsed = SourceStructure.javaMembers(code).flatMap(parseMember)
    def keyed(kind: MemberKind): Seq[UmlMember] = parsed.collect { case (`kind`, member) => member }
      .foldLeft(Vector.empty[UmlMember]) { (done, member) =>
        val occurrence = done.count(_.name == member.name)
        done :+ member.copy(key = EntityKey(name, Some(MemberKey(kind, member.name, occurrence))))
      }
    UmlClass(sourceKey, name, code, words(SourceStructure.header(code)).contains("abstract"), keyed(MemberKind.Attribute), keyed(MemberKind.Function))
  }

  /** A member with a provisional key holding only its name; initializer blocks and nested types yield nothing. */
  private def parseMember(code: String): Option[(MemberKind, UmlMember)] = {
    val declaration = withoutAnnotations(code)
    val header = SourceStructure.header(declaration).toVector
    val modifiers = words(header)
    def member(kind: MemberKind, name: String, label: String) = kind -> UmlMember(
      EntityKey("", Some(MemberKey(kind, name, 0))), s"${visibility(modifiers).symbol}$label",
      visibility(modifiers), modifiers.contains("static"), modifiers.contains("abstract"), code)
    header.indexWhere { case group: Group => group.open == '('; case _ => false } match {
      case parameters if parameters >= 1 => (header(parameters - 1), header(parameters)) match {
        case (Word(nameStart, _, name), group: Group) =>
          val typeStart = header.find {
            case Word(_, _, word) => !modifierWords(word)
            case _ => true
          }.map(_.start).getOrElse(nameStart)
          val returnType = declaration.substring(typeStart, nameStart).trim
          val parameterList = umlParameters(declaration.substring(group.start + 1, if group.closed then group.end - 1 else group.end))
          Some(member(MemberKind.Function, name, s"$name($parameterList)${typeSuffix(returnType)}"))
        case _ => None
      }
      case _ => SourceStructure.declaration(declaration).map(field =>
        member(MemberKind.Attribute, field.name, s"${field.name}${typeSuffix(field.javaType.trim)}"))
    }
  }

  private val modifierWords =
    Set("public", "private", "protected", "static", "final", "abstract", "strictfp", "synchronized", "native", "default", "transient", "volatile")

  private def words(tokens: Seq[Token]): Seq[String] = tokens.collect { case Word(_, _, text) => text }

  private def visibility(modifiers: Seq[String]): Visibility =
    if modifiers.contains("public") then Visibility.Public
    else if modifiers.contains("private") then Visibility.Private
    else if modifiers.contains("protected") then Visibility.Protected
    else Visibility.Package

  private def typeSuffix(javaType: String): String = if javaType.isEmpty then "" else s": $javaType"

  /** `int x, final List<String> names` becomes `x: int, names: List<String>`. */
  private def umlParameters(parameters: String): String =
    splitTopLevel(withoutAnnotations(parameters).trim).map { parameter =>
      val declared = parameter.trim.stripPrefix("final ").trim
      declared.lastIndexWhere(_.isWhitespace) match {
        case split if split > 0 => s"${declared.substring(split + 1)}: ${declared.substring(0, split).trim}"
        case _ => declared
      }
    }.mkString(", ")

  /** Splits at commas outside generic, array and parenthesised groups. */
  private def splitTopLevel(text: String): Seq[String] =
    if text.isEmpty then Seq.empty
    else {
      val (parts, current, _) = text.foldLeft((Vector.empty[String], "", 0)) { case ((parts, current, depth), char) =>
        char match {
          case ',' if depth == 0 => (parts :+ current, "", depth)
          case '<' | '(' | '[' | '{' => (parts, current + char, depth + 1)
          case '>' | ')' | ']' | '}' => (parts, current + char, (depth - 1).max(0))
          case _ => (parts, current + char, depth)
        }
      }
      parts :+ current
    }

  /** Removes `@Name`, `@a.b.Name` and `@Name(…)` annotations from the top level of a declaration. */
  private def withoutAnnotations(code: String): String = {
    val tokens = SourceStructure.significant(SourceStructure.tokens(code, python = false)).toVector
    val ranges = tokens.indices.collect {
      case index if (tokens(index) match { case Symbol(_, _, "@") => true; case _ => false }) &&
          tokens.lift(index + 1).exists { case Word(_, _, "interface") => false; case _: Word => true; case _ => false } =>
        val name = tokens.drop(index + 1).zipWithIndex.takeWhile { (token, offset) =>
          if offset % 2 == 0 then token.isInstanceOf[Word] else token match { case Symbol(_, _, ".") => true; case _ => false }
        }.map(_._1)
        val arguments = tokens.lift(index + 1 + name.size).collect { case group: Group if group.open == '(' => group }
        tokens(index).start -> arguments.fold(name.last.end)(_.end)
    }
    SourceStructure.remove(code, ranges)
  }
}
