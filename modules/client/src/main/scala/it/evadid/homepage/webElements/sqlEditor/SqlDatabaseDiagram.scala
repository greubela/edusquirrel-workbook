package it.evadid.homepage.webElements.sqlEditor

import com.raquo.laminar.api.L.*
import it.evadid.distribution.commandTypes.SqlEditorCommands.Table

object SqlDatabaseDiagram {
  def render(tables: List[Table]): Element = {
    val cardWidth = 300
    val gap = 100
    val top = 30
    val heights = tables.map(table => 42 + table.columns.size * 24)
    val canvasHeight = heights.maxOption.getOrElse(80) + 80
    val canvasWidth = math.max(400, tables.size * (cardWidth + gap))
    val positions = tables.zipWithIndex.map { case (table, index) => table.name -> index }.toMap
    div(
      cls := "sql-editor__diagram", role := "img", aria.label := "Database UML diagram: tables, columns, primary keys and foreign keys",
      if tables.isEmpty then p("No tables available in this database.")
      else svg.svg(
        svg.viewBox := s"0 0 $canvasWidth $canvasHeight",
        svg.width := canvasWidth.toString, svg.height := canvasHeight.toString,
        tables.zipWithIndex.flatMap { case (table, index) =>
          table.foreignKeys.flatMap { key => positions.get(key.targetTable).map { target =>
            val startX = index * (cardWidth + gap) + cardWidth + 10
            val endX = target * (cardWidth + gap) + 10
            val startY = top + 54 + math.max(0, table.columns.indexWhere(_.name == key.column)) * 24
            val endY = top + 54 + math.max(0, tables(target).columns.indexWhere(_.name == key.targetColumn)) * 24
            svg.path(svg.d := s"M $startX $startY H ${startX + 25} V ${canvasHeight - 25 - index * 4} H ${endX - 25} V $endY H $endX l -8 -5 m 8 5 l -8 5",
              svg.fill := "none", svg.stroke := "#64748b", svg.strokeWidth := "2")
          }}
        },
        tables.zipWithIndex.map { case (table, index) =>
          val x = index * (cardWidth + gap) + 10
          svg.g(
            svg.rect(svg.x := x.toString, svg.y := top.toString, svg.width := cardWidth.toString,
              svg.height := heights(index).toString, svg.rx := "6", svg.fill := "#fff", svg.stroke := "#64748b"),
            svg.line(svg.x1 := x.toString, svg.x2 := (x + cardWidth).toString,
              svg.y1 := (top + 34).toString, svg.y2 := (top + 34).toString, svg.stroke := "#64748b"),
            svg.text(svg.x := (x + 12).toString, svg.y := (top + 23).toString, svg.fill := "#0f172a", table.name),
            table.columns.zipWithIndex.map { case (column, row) =>
              val key = if column.primaryKey then "PK " else if table.foreignKeys.exists(_.column == column.name) then "FK " else ""
              svg.text(svg.x := (x + 12).toString, svg.y := (top + 54 + row * 24).toString,
                svg.fill := "#334155", s"$key${column.name}: ${column.dataType}${if column.nullable then " ?" else ""}")
            }
          )
        }
      ),
      ul(tables.flatMap(table => table.foreignKeys.map(key =>
        li(s"${table.name}.${key.column} → ${key.targetTable}.${key.targetColumn}"))))
    )
  }
}
