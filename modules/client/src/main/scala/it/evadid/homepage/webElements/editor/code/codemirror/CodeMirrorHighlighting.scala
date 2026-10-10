package it.evadid.homepage.webElements.editor.code.codemirror

import scala.scalajs.js
import CodeMirrorApi.*

private[code] object CodeMirrorHighlighting {
  private val reservedIdentifiers = Set(
    "if", "else", "elif", "for", "while", "do", "switch", "case", "default", "break", "continue", "return",
    "int", "void", "char", "float", "double", "long", "short", "bool", "boolean", "byte", "word", "string",
    "const", "static", "unsigned", "signed", "struct", "class", "public", "private", "protected",
    "true", "false", "True", "False", "NULL", "nullptr", "None", "sizeof", "typedef", "enum", "volatile",
    "def", "import", "from", "as", "pass", "and", "or", "not", "in", "is", "with", "try", "except",
    "finally", "raise", "yield", "lambda", "global", "nonlocal", "assert", "async", "await",
    "self", "cls", "new", "delete", "this", "using", "namespace", "template", "typename", "virtual",
    "override", "inline", "extern", "auto", "include", "define", "ifdef", "ifndef", "endif"
  )
  private val accentIdentifiers = Set("HIGH", "LOW", "INPUT", "OUTPUT", "INPUT_PULLUP", "LED_BUILTIN")

  def extension: Extension = {
    val decorations: js.Function1[IdentifierPlugin, DecorationSet] = _.decorations
    Libraries.ViewPlugin.define(
      (view: View) => new IdentifierPlugin(view),
      js.Dynamic.literal(decorations = decorations)
    )
  }

  private class IdentifierPlugin(view: View) extends js.Object {
    var decorations: DecorationSet = buildDecorations(view)

    def update(update: ViewUpdate): Unit = {
      var reconfigured = false
      var index = 0
      while (index < update.transactions.length && !reconfigured) {
        reconfigured = update.transactions(index).reconfigured
        index += 1
      }
      if (update.docChanged || update.viewportChanged || reconfigured)
        decorations = buildDecorations(update.view)
    }
  }

  private def isInCommentOrString(state: State, position: Int): Boolean = {
    var node: SyntaxNode | Null = Libraries.syntaxTree(state).resolveInner(position, -1)
    while (node != null) {
      val current = node.asInstanceOf[SyntaxNode]
      val name = current.name
      if (name == "CharLiteral" || name.contains("Comment") || name.contains("String")) return true
      node = current.parent
    }
    false
  }

  private def nextNonSpaceChar(doc: Text, position: Int): String = {
    val text = doc.sliceString(position, math.min(position + 32, doc.length))
    val matched = new js.RegExp("^\\s*(.)").exec(text)
    if (matched == null) "" else matched(1).getOrElse("")
  }

  private def buildDecorations(view: View): DecorationSet = {
    val ranges = new js.Array[js.Object]()
    val doc = view.state.doc
    val identifiers = new js.RegExp("\\b[A-Za-z_][A-Za-z0-9_]*\\b", "g")
    val brackets = new js.RegExp("[{}[\\]()]", "g")
    var rangeIndex = 0
    while (rangeIndex < view.visibleRanges.length) {
      val visible = view.visibleRanges(rangeIndex)
      val text = doc.sliceString(visible.from, visible.to)
      identifiers.lastIndex = 0
      var matched = identifiers.exec(text)
      while (matched != null) {
        val word = matched(0).get
        val start = visible.from + matched.index
        val end = start + word.length
        if (!reservedIdentifiers(word) && !isInCommentOrString(view.state, start)) {
          val nextChar = nextNonSpaceChar(doc, end)
          val cssClass =
            if (word.contains("TODO")) "cm-todo-token"
            else if (word == "receive_go" && nextChar == "(") "cm-receive-go"
            else if (nextChar == "(" || nextChar == "." || accentIdentifiers(word)) "cm-accent-name"
            else "cm-plain-name"
          ranges.push(Libraries.Decoration.mark(js.Dynamic.literal(`class` = cssClass)).range(start, end))
        }
        matched = identifiers.exec(text)
      }

      brackets.lastIndex = 0
      matched = brackets.exec(text)
      while (matched != null) {
        val start = visible.from + matched.index
        if (!isInCommentOrString(view.state, start))
          ranges.push(Libraries.Decoration.mark(js.Dynamic.literal(`class` = "cm-plain-name")).range(start, start + 1))
        matched = brackets.exec(text)
      }
      rangeIndex += 1
    }
    Libraries.Decoration.set(ranges, true)
  }
}
