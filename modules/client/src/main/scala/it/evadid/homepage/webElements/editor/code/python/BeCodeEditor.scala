package it.evadid.homepage.webElements.editor.code.python

/*

todo: actually implement this..

case class BeCodeEditor(
                         program: Var[BeProgram],
                         programmingLanguage: ProgrammingLanguage = Python,
                         humanLanguage: HumanLanguage = English,
                         onParseError: Option[String] => Unit = _ => (),
                         onUserInput: String => Unit = _ => (),
                         editorFont: Signal[AppFont] = Val(AppFont("JetBrains Mono", 14))
                       ) extends HtmlAppElement {

  import BeCodeEditor.{facade, waitForFacade, CodeMirrorFacade, CodeMirrorHandle, EditorConfig}

  private val strVar: Var[String] = Var(initProgramString())
  private var textDirty: Boolean = false
  private var editorHandle: Option[CodeMirrorHandle] = None
  private var updatingFromVar: Boolean = false

  private def initProgramString(): String =
    program.now().fullProgram.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable = false)

  def focus(): Unit = editorHandle.foreach(_.focus())

  def setDiagnostics(diagnostics: Seq[CodeMirrorEditor.Diagnostic]): Unit =
    editorHandle.foreach(_.setDiagnostics(js.Array(diagnostics.map(_.toJs)*)))

  def clearDiagnostics(): Unit = setDiagnostics(Nil)

  def syncTextToProgram(): Unit = {
    val raw = strVar.now()
    textDirty = false
    try {
      val parsedSequence = PythonParser.parsePython(raw)
      program.set(BeProgram(BeStartProgram(parsedSequence)))
      onParseError(None)
    } catch {
      case e: Exception =>
        onParseError(Some(e.getMessage))
    }
  }

  override def getDomElement(): L.Element = {
    div(
      cls := "code-mirror-editor",
      styleAttr <-- editorFont.map(font =>
        s"--code-font-family: '${font.name}', 'Fira Code', 'JetBrains Mono', monospace; --code-font-size: ${font.sizeInPx}px;"
      ),
      onMountCallback { ctx =>
        waitForFacade {
          case Some(cmFacade) =>
            val container = ctx.thisNode.ref
            val initialValue = strVar.now()

            var updatingFromEditor: Boolean = false

            val handle = cmFacade.createEditor(
              EditorConfig(
                parent = container,
                doc = initialValue,
                onDocChange = value =>
                  if (!updatingFromVar) {
                    updatingFromEditor = true
                    textDirty = true
                    strVar.set(value)
                    onUserInput(value)
                    updatingFromEditor = false
                  }
              )
            )

            editorHandle = Some(handle)

            // BeProgram -> String: when program Var changes externally, update editor
            program.signal.foreach { newProg =>
              if (!textDirty) {
                val newText = newProg.fullProgram.expressionIO.toStringInLanguage(programmingLanguage, humanLanguage, skipUnparsable = false)
                if (handle.getDoc() != newText) {
                  updatingFromVar = true
                  handle.setDoc(newText)
                  strVar.set(newText)
                  updatingFromVar = false
                }
              }
            }(using ctx.owner)

          case None =>
            dom.console.error("CodeMirror facade is not available on window.EduSquirrelCodeMirror")
        }
      },
      onUnmountCallback { _ =>
        editorHandle.foreach(_.destroy())
        editorHandle = None
      }
    )
  }
}

object BeCodeEditor {

  @js.native
  private trait CodeMirrorFacade extends js.Object {
    def createEditor(config: EditorConfig): CodeMirrorHandle = js.native
  }

  @js.native
  private trait CodeMirrorHandle extends js.Object {
    def setDoc(value: String): Unit = js.native
    def getDoc(): String = js.native
    def setDiagnostics(diagnostics: js.Array[js.Object]): Unit = js.native
    def focus(): Unit = js.native
    def destroy(): Unit = js.native
  }

  private trait EditorConfig extends js.Object {
    var parent: dom.Element
    var doc: String
    var onDocChange: js.Function1[String, Unit]
  }

  private object EditorConfig {
    def apply(parent: dom.Element, doc: String, onDocChange: String => Unit): EditorConfig = {
      js.Dynamic.literal(
        parent = parent,
        doc = doc,
        onDocChange = (value: String) => onDocChange(value)
      ).asInstanceOf[EditorConfig]
    }
  }

  private def facade: Option[CodeMirrorFacade] = {
    val maybeFacade = js.Dynamic.global.selectDynamic("EduSquirrelCodeMirror")
    if (js.isUndefined(maybeFacade) || maybeFacade == null) None
    else Some(maybeFacade.asInstanceOf[CodeMirrorFacade])
  }

  private def waitForFacade(callback: Option[CodeMirrorFacade] => Unit): Unit = {
    facade match {
      case some@Some(_) =>
        callback(some)
      case None =>
        val readyPromise = js.Dynamic.global.selectDynamic("EduSquirrelCodeMirrorReady")
        if (js.isUndefined(readyPromise) || readyPromise == null) {
          callback(None)
        } else {
          readyPromise
            .asInstanceOf[js.Promise[js.Any]]
            .`then`[Unit]((_: js.Any) => callback(facade))
            .`catch`(((error: scala.Any) => {
              dom.console.error("Failed to initialize CodeMirror facade", error.asInstanceOf[js.Any])
              callback(None)
              (): Unit
            }): js.Function1[scala.Any, Unit | js.Thenable[Unit]])
        }
    }
  }
}*/