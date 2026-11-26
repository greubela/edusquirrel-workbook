package it.evadid.vm.parsing.source

import upickle.default.ReadWriter

/** Step 1 for Java ([[SourceParsing]]): the declarations of any text, unfinished or invalid code included.
  *
  * `classes` lists every class declaration (nested ones too) in source order. Verbatim library definitions such
  * as the generated `Turtle` are `library` classes; `masked` is the source with them blanked, keeping offsets and
  * lines, and is what later steps read. `codeMasked` also blanks comments and string literals. `looseMembers` are the
  * declarations and statements outside classes and the preamble, split like class members (`className` is empty).
  */
final case class JavaSourceStructured(
    source: String,
    classes: Vector[JavaClassStructured],
    preamble: Vector[JavaPreambleStructured],
    looseMembers: Vector[JavaMemberStructured],
    comments: Vector[SourceSpan],
    balanced: Boolean,
    trivia: Boolean,
    masked: String,
    codeMasked: String
) derives ReadWriter {
  /** The classes the program defines itself. */
  def programClasses: Vector[JavaClassStructured] = classes.filterNot(_.library)

  /** Top-level classes, outside any other class. */
  def topLevelClasses: Vector[JavaClassStructured] = classes.filter(_.outer.isEmpty)

  def functions: Vector[JavaFunctionStructured] = classes.flatMap(_.functions)

  /** Whether `[start, end)` holds only whitespace and closed comments. */
  def onlyTrivia(start: Int, end: Int): Boolean = {
    val inside = comments.filter(comment => comment.start < end && comment.end > start)
    inside.forall(comment => start <= comment.start && comment.end <= end) &&
      (start until end).forall(offset => source.charAt(offset).isWhitespace || inside.exists(_.contains(offset)))
  }
}

/** A `package` or `import` declaration outside classes; `text` runs to its `;`. */
final case class JavaPreambleStructured(keyword: String, text: String, span: SourceSpan) derives ReadWriter

/** A class declaration: from its first modifier to its closing `}` (or the end of an unfinished text). `bodySpan` is
  * the text inside the braces; `outer` names the class it is nested in.
  */
final case class JavaClassStructured(
    name: String,
    modifiers: Vector[String],
    span: SourceSpan,
    code: String,
    header: String,
    bodySpan: Option[SourceSpan],
    closed: Boolean,
    members: Vector[JavaMemberStructured],
    library: Boolean,
    outer: Option[String],
    isInterface: Boolean = false,
    extendsNames: Vector[String] = Vector.empty,
    implementsNames: Vector[String] = Vector.empty
) derives ReadWriter {
  def isAbstract: Boolean = isInterface || modifiers.contains("abstract")
  def functions: Vector[JavaFunctionStructured] = members.collect { case function: JavaFunctionStructured => function }
  def fields: Vector[JavaFieldStructured] = members.collect { case field: JavaFieldStructured => field }
}

/** A direct member of a class body, from its first code token (annotations included) to its `;` or `}`. */
sealed trait JavaMemberStructured derives ReadWriter {
  def span: SourceSpan
  def code: String
}

/** A method or constructor (`returnType` is `None`). `body` is the dedented text inside its braces once they close. */
final case class JavaFunctionStructured(
    className: String,
    name: String,
    modifiers: Vector[String],
    annotations: Vector[String],
    returnType: Option[String],
    parameters: Vector[JavaParameterStructured],
    parameterList: String,
    body: Option[String],
    bodySpan: Option[SourceSpan],
    closed: Boolean,
    span: SourceSpan,
    code: String
) extends JavaMemberStructured derives ReadWriter {
  def isStatic: Boolean = modifiers.contains("static")
  def isAbstract: Boolean = modifiers.contains("abstract")
  def isConstructor: Boolean = returnType.isEmpty
}

/** A parameter: `final int x` has the modifier `final`, the type `int` and the name `x`; varargs keep `...`. */
final case class JavaParameterStructured(name: String, javaType: String, modifiers: Vector[String]) derives ReadWriter

/** A field (or, in loose code, a local) declaration with its type and optional initializer. */
final case class JavaFieldStructured(
    className: String,
    name: String,
    modifiers: Vector[String],
    annotations: Vector[String],
    javaType: String,
    initializer: Option[String],
    span: SourceSpan,
    code: String,
    typeNames: Vector[String] = Vector.empty
) extends JavaMemberStructured derives ReadWriter {
  def isStatic: Boolean = modifiers.contains("static")
}

/** Any other member: a statement of loose code, an initializer block, a nested type or unreadable text. */
final case class JavaOtherMemberStructured(span: SourceSpan, code: String) extends JavaMemberStructured derives ReadWriter
