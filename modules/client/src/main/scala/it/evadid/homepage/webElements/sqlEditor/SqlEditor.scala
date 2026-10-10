package it.evadid.homepage.webElements.sqlEditor

import com.raquo.laminar.api.L.*
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.commandTypes.SqlEditorCommands
import it.evadid.distribution.commandTypes.SqlEditorCommands.*
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.homepage.webElements.editor.code.CodeMirrorEditor
import it.evadid.core.datastructures.language.AppLanguage.SQL
import it.evadid.workbook.elements.interactionElements.sql.SqlDatabaseConfig
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success}

final case class SqlEditor(state: Var[String], databaseConfig: SqlDatabaseConfig, backend: ExecutionClient) extends HtmlAppElement with FullscreenLifecycle {
  private case class Block(id: Int, text: String) derives upickle.default.ReadWriter
  private val blockMode = Var(false)
  private var nextId = 0
  private def block(text: String): Block = { nextId += 1; Block(nextId, text) }
  private val blocks = Var(SqlBlocks.split(state.now()).map(block))
  private val busy = Var(false)
  private val connection = Var("Connection not checked")
  private val connectionKind = Var("unknown")
  private val response = Var(Option.empty[Response])
  private val lastRun = Var(Option.empty[Response])
  private val executionError = Var(Option.empty[String])
  private var mounted = false
  private var generation = 0
  private var publishingBlocks = false
  private var draggedBlock = Option.empty[Int]
  override def onFullscreenClose(): Unit = ()
  override def dismissOnOutsideClick: Boolean = false
  private val textEditor = CodeMirrorEditor(state, language = SQL)

  private def publish(next: List[Block]): Unit = {
    blocks.set(next)
    publishingBlocks = true
    state.set(SqlBlocks.join(next.map(_.text)))
    publishingBlocks = false
  }
  private def update(id: Int, text: String): Unit = publish(blocks.now().map(b => if b.id == id then b.copy(text = text) else b))
  private def move(id: Int, offset: Int): Unit = {
    val from = blocks.now().indexWhere(_.id == id)
    publish(SqlBlocks.move(blocks.now(), from, from + offset))
  }
  private def add(text: String): Unit = {
    publish(SqlBlocks.split(SqlBlocks.appendClause(state.now(), text)).map(block))
  }

  private def request(run: Boolean): Unit = {
    if busy.now() then return
    busy.set(true)
    executionError.set(None)
    connection.set("Connecting…")
    connectionKind.set("checking")
    generation += 1
    val current = generation
    SqlEditorCommands.execute.sendCommandTo(backend, Request(databaseConfig, if run then Some(state.now()) else None))
      .map(info => info.resultTyped.result)
      .onComplete { result =>
        if mounted && current == generation then {
          busy.set(false)
          result match {
            case Success(value) =>
              response.set(Some(value))
              if run then lastRun.set(Some(value))
              connectionKind.set(value.status match {
                case Status.Ready | Status.QueryError => "connected"
                case Status.Unreachable => "unreachable"
                case Status.ConfigurationError => "configuration-error"
              })
              connection.set(value.status match {
                case Status.Ready | Status.QueryError => s"Connected to ${databaseConfig.databaseName}"
                case _ => value.message
              })
            case Failure(_) =>
              connectionKind.set("service-error")
              connection.set("SQL service unavailable")
              executionError.set(Some("Could not reach the SQL service. Check backend access and sign in, then retry."))
          }
        }
      }
  }

  private def renderBlocks(): Element = div(
    cls := "sql-editor__blocks",
    div(cls := "sql-editor__palette", aria.label := "SQL block palette",
      SqlBlocks.palette.map { case (name, text) => button(typ := "button", name, onClick --> (_ => add(text))) }),
    div(cls := "sql-editor__palette", aria.label := "SQL comparison blocks",
      span("Conditions: "),
      SqlBlocks.comparisonOperators.map(operator => button(typ := "button", s"WHERE $operator",
        onClick --> (_ => add(s"WHERE column_name $operator 1"))))),
    p("Add clauses from the palette. Drag blocks or use the arrow buttons to reorder them."),
    div(
      children <-- blocks.signal.split(_.id) { (id, initial, signal) =>
        div(
          cls := "sql-editor__block", draggable := true,
          onDragStart --> { event =>
            draggedBlock = Some(id)
            event.dataTransfer.setData("text/plain", id.toString)
          },
          onDragEnd --> (_ => draggedBlock = None),
          onDragOver --> { event => if draggedBlock.nonEmpty then event.preventDefault() },
          onDrop --> { event =>
            event.preventDefault()
            draggedBlock.foreach { source =>
              val current = blocks.now()
              publish(SqlBlocks.move(current, current.indexWhere(_.id == source), current.indexWhere(_.id == id)))
            }
            draggedBlock = None
          },
          div(cls := "sql-editor__block-heading",
            strong(child.text <-- signal.map(b => SqlBlocks.label(b.text))),
            button(typ := "button", aria.label := "Move block up", "↑",
              disabled <-- blocks.signal.map(_.headOption.exists(_.id == id)), onClick --> (_ => move(id, -1))),
            button(typ := "button", aria.label := "Move block down", "↓",
              disabled <-- blocks.signal.map(_.lastOption.exists(_.id == id)), onClick --> (_ => move(id, 1))),
            button(typ := "button", aria.label := "Remove block", "Remove", onClick --> (_ => publish(blocks.now().filterNot(_.id == id))))
          ),
          textArea(aria.label := "SQL clause", value <-- signal.map(_.text),
            onInput.mapToValue --> (text => update(id, text)))
        )
      }
    ),
    child <-- blocks.signal.map(bs => if bs.isEmpty then p("No blocks yet. Add a SQL clause above.") else emptyNode)
  )

  private def renderResults(value: Response): Element = div(
    p(role := (if value.status == Status.QueryError then "alert" else "status"), value.message),
    value.results.map { result =>
      div(
        result.affectedRows.fold[Element](div())(count => p(s"$count row(s) affected.")),
        if result.columns.isEmpty then emptyNode
        else div(cls := "sql-editor__result-table",
          table(
            thead(tr(result.columns.map(name => th(name)))),
            tbody(result.rows.map(row => tr(row.map(cell => td(cell.getOrElse("NULL")))))),
          ),
          p(s"${result.rows.size} row(s)${if result.truncated then " shown (limited to 500 rows)" else ""}.")
        )
      )
    }
  )

  override def getDomElement(): Element = div(
    cls := "sql-editor",
    styleTag(SqlEditor.styles),
    onMountCallback { ctx =>
      mounted = true
      blocks.set(SqlBlocks.split(state.now()).map(block))
      state.signal.changes.foreach { source =>
        if !publishingBlocks && SqlBlocks.join(blocks.now().map(_.text)) != source then
          blocks.set(SqlBlocks.split(source).map(block))
      }(using ctx.owner)
      request(false)
    },
    onUnmountCallback { _ => mounted = false; generation += 1; busy.set(false); draggedBlock = None },
    h2("SQL editor"),
    div(cls := "sql-editor__toolbar",
      div(role := "tablist", aria.label := "SQL editor modes",
        button(typ := "button", role := "tab", aria.selected <-- blockMode.signal.map(!_), "SQL text",
          onClick --> (_ => blockMode.set(false))),
        button(typ := "button", role := "tab", aria.selected <-- blockMode.signal, "SQL blocks",
          onClick --> (_ => blockMode.set(true)))
      ),
      button(typ := "button", "Run", disabled <-- busy.signal.combineWith(state.signal).map { case (running, sql) => running || sql.trim.isEmpty },
        onClick --> (_ => request(true))),
      button(typ := "button", "Check connection", disabled <-- busy.signal, onClick --> (_ => request(false)))
    ),
    p(role := "status", aria.live := "polite",
      cls <-- connectionKind.signal.map(kind => s"sql-editor__connection sql-editor__connection--$kind"),
      child.text <-- connection.signal),
    div(role := "tabpanel", child <-- blockMode.signal.map { useBlocks =>
      if useBlocks then renderBlocks() else textEditor.getDomElement()
    }),
    sectionTag(cls := "sql-editor__section", h3("Results"), aria.live := "polite",
      child <-- executionError.signal.map(_.fold(emptyNode)(message => p(role := "alert", message))),
      child <-- lastRun.signal.map(_.fold[Element](p("Run a SQL command to see its result here."))(renderResults))
    ),
    sectionTag(cls := "sql-editor__section", h3("Database diagram"),
      child <-- response.signal.map {
        case None => p("Check the connection to load database tables and relationships.")
        case Some(value) if value.schemaError.nonEmpty => p(role := "alert", value.schemaError.get)
        case Some(value) if value.status == Status.Unreachable || value.status == Status.ConfigurationError => p("Database schema unavailable.")
        case Some(value) => SqlDatabaseDiagram.render(value.schema)
      }
    )
  )
}

object SqlEditor {
  private val styles = """
    .sql-editor {padding:20px; color:#17243a; background:#f8fafc; border:1px solid #cbd5e1; border-radius:12px;}
    .sql-editor button {padding:6px 12px; margin:3px; border:1px solid #94a3b8; border-radius:6px; background:white; color:#17243a; cursor:pointer;}
    .sql-editor button:disabled {opacity:.5; cursor:default;}
    .sql-editor button[aria-selected=true] {background:#dbeafe; border-color:#2563eb;}
    .sql-editor__toolbar, .sql-editor__block-heading {display:flex; gap:8px; align-items:center; flex-wrap:wrap;}
    .sql-editor__connection {padding:8px; border-left:4px solid #64748b;}
    .sql-editor__connection--connected {border-color:#15803d; background:#dcfce7;}
    .sql-editor__connection--unreachable, .sql-editor__connection--configuration-error, .sql-editor__connection--service-error {border-color:#b91c1c; background:#fee2e2;}
    .sql-editor__palette {padding:8px; background:#e2e8f0; border-radius:8px;}
    .sql-editor__block {margin:8px 0; padding:10px; border:2px solid #3b82f6; border-radius:10px 10px 10px 3px; background:#dbeafe;}
    .sql-editor__block strong {flex:1;}
    .sql-editor__block textarea {width:100%; min-height:55px; box-sizing:border-box; font-family:monospace;}
    .sql-editor__result-table, .sql-editor__diagram {overflow:auto; max-width:100%;}
    .sql-editor table {border-collapse:collapse; background:white;}
    .sql-editor td, .sql-editor th {border:1px solid #cbd5e1; padding:6px 12px; white-space:pre-wrap;}
    .sql-editor section {margin-top:20px; border-top:1px solid #cbd5e1;}
    .sql-editor__diagram svg {font:13px monospace;}
  """
}
