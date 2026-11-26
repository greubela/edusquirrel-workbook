package it.evadid.vm.parsing.source

/** Step 1 for Python ([[SourceParsing]]): logical lines, classes and functions of any text, unfinished or invalid
  * code included. Verbatim library definitions (`import turtle`, the `fetch_text` helper) are blanked in `masked`,
  * keeping offsets and lines; later steps read `masked`.
  */
final case class PythonSourceStructured(
    source: String,
    lines: Vector[PythonLineStructured],
    classes: Vector[PythonClassStructured],
    functions: Vector[PythonFunctionStructured],
    balanced: Boolean,
    masked: String,
    codeMasked: String
) {
  /** Logical lines that hold code. */
  def codeLines: Vector[PythonLineStructured] = lines.filter(_.code.nonEmpty)
}

/** A logical line (brackets and strings may span physical lines) starting on 1-based `row`: its indentation, its code
  * without comments (empty for blank and comment lines) and whether it opens a block (ends with `:`).
  */
final case class PythonLineStructured(row: Int, indent: Int, code: String, opensBlock: Boolean, span: SourceSpan)

/** A class block from its header line to the last line indented deeper than the header. */
final case class PythonClassStructured(name: String, bases: String, header: String, body: String, code: String, indent: Int,
    span: SourceSpan)

/** A function block from its header (`def`, `async def`) to its last line. `decorators` precede the header on lines of
  * the same indentation; `owner` is the class whose body directly or indirectly contains it.
  */
final case class PythonFunctionStructured(
    name: String,
    owner: Option[String],
    decorators: Vector[String],
    parameters: Vector[PythonParameterStructured],
    parameterList: String,
    returnType: Option[String],
    header: String,
    body: String,
    code: String,
    indent: Int,
    headerLine: Int,
    span: SourceSpan
)

/** A parameter as written: `*args`, `count: int = 3`. */
final case class PythonParameterStructured(name: String, annotation: Option[String], default: Option[String], text: String)
