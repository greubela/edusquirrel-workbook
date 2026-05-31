package it.evadid.homepage.workbook.legacy.plantworkshop.helpers

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js.timers.*

/** Drag-and-drop blocks UI. */
object DragAndDropHelper {
  private def snippetExplanation(snippetText: String): String = {
    val normalized = snippetText.trim

    if (normalized.contains("digitalWrite(SENSOR_POWER_PIN, HIGH)")) "Setze SENSOR_POWER_PIN auf HIGH (Sensor an)."
    else if (normalized.contains("digitalWrite(SENSOR_POWER_PIN, LOW)")) "Setze SENSOR_POWER_PIN auf LOW (Sensor aus)."
    else if (normalized.contains("analogRead(SENSOR_PIN)")) "Lies den analogen Wert von SENSOR_PIN und speichere ihn als messwert."
    else if (normalized.contains("messwert < feuchtigkeitsGrenze")) "Prüfe: Ist messwert kleiner als feuchtigkeitsGrenze?"
    else if (normalized.contains("digitalWrite(PUMP_PIN, HIGH)")) "Setze PUMP_PIN auf HIGH (Pumpe an)."
    else if (normalized.contains("digitalWrite(PUMP_PIN, LOW)")) "Setze PUMP_PIN auf LOW (Pumpe aus)."
    else if (normalized.contains("Serial.print")) "Gib Text im Serial Monitor ohne Zeilenumbruch aus."
    else if (normalized.contains("Serial.println")) "Gib Text oder Wert im Serial Monitor mit Zeilenumbruch aus."
    else if (normalized.contains("delay(2000)")) "Warte 2000 ms."
    else if (normalized.contains("int feuchtigkeitsGrenze")) "Erstelle eine Ganzzahlvariable für die Feuchtigkeitsgrenze."
    else if (normalized.contains("delay(10000)")) "Warte 10000 ms."
    else if (normalized.startsWith("if")) "Starte einen if-Block."
    else if (normalized.startsWith("} else {")) "Beende den if-Block und starte den else-Zweig."
    else if (normalized.contains("delay(10)")) "Warte 10 ms."
    else if (normalized == "}") "Schließe den Block."
    else "Erklärung für diese Zeile ist nicht hinterlegt."
  }

  def createDndArea(
    allSnippets: List[CodeSnippet],
    sourceSnippets: Var[List[CodeSnippet]],
    targetSnippets: Var[List[CodeSnippet]],
    draggingSnippet: Var[Option[CodeSnippet]],
    targetHoverIndex: Var[Option[Int]],
    title: String,
    orderConstraints: List[(Int, Int)] = Nil
  ): HtmlElement = {
    div(
      h4(title),
      div(
        className := "dnd-area",

        div(
          className := "snippet-container source",
          h5("Verfügbare Bausteine"),

          onDragOver.preventDefault --> { _ => targetHoverIndex.set(None) },

          onDrop.preventDefault --> { _ =>
            draggingSnippet.now().foreach { snippet =>
              targetSnippets.update(_.filterNot(_.id == snippet.id))
              sourceSnippets.update { list =>
                val clean = list.filterNot(_.id == snippet.id)
                (clean :+ snippet).sortBy(_.id)
              }
            }
            draggingSnippet.set(None)
            targetHoverIndex.set(None)
          },

          children <-- sourceSnippets.signal.combineWith(draggingSnippet.signal).map {
            case (list, dragging) =>
              list.filterNot(s => dragging.exists(_.id == s.id))
                .map(s => renderDraggableItem(s, draggingSnippet))
          }
        ),

        div(
          className <-- targetHoverIndex.signal.map(opt =>
            if (opt.isDefined) "snippet-container target drag-over"
            else "snippet-container target"
          ),
          h5("Dein Programmablauf"),

          onDragOver.preventDefault --> { e =>
            val container = e.currentTarget.asInstanceOf[dom.html.Div]
            val items = container.querySelectorAll(".sortable-item")
            val mouseY = e.clientY

            var newIndex = items.length
            var found = false
            var i = 0
            while (i < items.length && !found) {
              val rect = items.item(i).asInstanceOf[dom.html.Div].getBoundingClientRect()
              val middleY = rect.top + (rect.height / 2)
              if (mouseY < middleY) { newIndex = i; found = true }
              i += 1
            }
            targetHoverIndex.set(Some(newIndex))
          },

          onDrop.preventDefault --> { _ =>
            val snippetOpt = draggingSnippet.now()
            val indexOpt = targetHoverIndex.now()

            (snippetOpt, indexOpt) match {
              case (Some(snippet), Some(idx)) =>
                sourceSnippets.update(_.filterNot(_.id == snippet.id))
                targetSnippets.update { list =>
                  val clean = list.filterNot(_.id == snippet.id)
                  val safeIdx = Math.min(idx, clean.length)
                  val (front, back) = clean.splitAt(safeIdx)
                  front ++ List(snippet) ++ back
                }
              case _ =>
            }
            draggingSnippet.set(None)
            targetHoverIndex.set(None)
          },

          children <-- targetSnippets.signal
            .combineWith(draggingSnippet.signal, targetHoverIndex.signal)
            .map { case (snippets, dragging, hoverIdx) =>
              val visible = snippets.filterNot(s => dragging.exists(_.id == s.id))
              val elements = visible.map(s => renderDraggableItem(s, draggingSnippet))
              hoverIdx match {
                case Some(idx) =>
                  val safe = Math.min(idx, elements.length)
                  val (f, b) = elements.splitAt(safe)
                  f ++ List(div(className := "drop-placeholder")) ++ b
                case None => elements
              }
            }
        )
      ),

      button(
        "Lösung prüfen",
        className := "btn-check",
        onClick --> { _ =>
          val current = targetSnippets.now().map(_.id)
          val required = allSnippets.map(_.id)

          val allBlocksUsed = current.length == required.length && current.toSet == required.toSet
          val constraints =
            if (orderConstraints.nonEmpty) orderConstraints
            else required.sliding(2).collect { case List(a, b) => (a, b) }.toList

          val positions = current.zipWithIndex.toMap
          val orderIsValid = constraints.forall { case (first, second) =>
            positions.get(first).exists(firstIdx => positions.get(second).exists(secondIdx => firstIdx < secondIdx))
          }

          if (!allBlocksUsed) {
            dom.window.alert("⚠️ Es fehlen noch Bausteine oder ein Baustein wurde doppelt verwendet.")
          } else if (orderIsValid) {
            dom.window.alert("✅ Sehr gut! Dein Ablauf funktioniert.")
          } else {
            dom.window.alert("⚠️ Fast! Die Logik passt noch nicht ganz. Prüfe, welche Schritte zwingend vor anderen kommen müssen.")
          }
        }
      )
    )
  }

  def renderDraggableItem(snippet: CodeSnippet, draggingSnippet: Var[Option[CodeSnippet]]): HtmlElement = {
    div(
      className := "code-block sortable-item",
      draggable := true,
      snippet.text,
      span(
        className := "code-tooltip",
        snippetExplanation(snippet.text)
      ),
      onDragStart --> { _ => setTimeout(0) { draggingSnippet.set(Some(snippet)) } },
      onDragEnd --> { _ =>
        draggingSnippet.set(None)
      }
    )
  }
}
