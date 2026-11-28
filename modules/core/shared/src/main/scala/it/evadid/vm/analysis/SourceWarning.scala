package it.evadid.vm.analysis

import upickle.default.ReadWriter

/** A located problem in a program's source: `subject` (e.g. the dotted path `System.out2.println`) from line/column
  * `startLine`:`startColumn` to `endLine`:`endColumn` (1-based, end exclusive), as the parser located it.
  */
final case class SourceWarning(kind: SourceWarning.Kind, subject: String, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int)
    derives ReadWriter

object SourceWarning {
  enum Kind derives ReadWriter {
    /** A call or attribute that neither the program nor a known dependency defines. */
    @upickle.implicits.key("UnknownDependency")
    case UnknownDependency
    /** A statement the parser keeps as text because it cannot read it; `subject` is its first line. */
    @upickle.implicits.key("Unreadable")
    case Unreadable
    /** Syntax was understood, but the adapter cannot represent its behavior in the shared model. */
    @upickle.implicits.key("Unsupported")
    case Unsupported
  }
}
