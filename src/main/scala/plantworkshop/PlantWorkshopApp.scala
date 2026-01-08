package plantworkshop

import com.raquo.laminar.api.L._
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.scalajs.js.timers._

// JAVASCRIPT FACADES
@js.native
@JSGlobal("CodeJar")
class CodeJar(element: dom.Element, highlight: js.Function1[dom.Element, Unit]) extends js.Object {
  def updateCode(code: String): Unit = js.native
  def onUpdate(callback: js.Function1[String, Unit]): Unit = js.native
  def destroy(): Unit = js.native
}

@js.native
@JSGlobal("Prism")
object Prism extends js.Object {
  def highlightElement(element: dom.Element): Unit = js.native
}

case class CodeSnippet(id: Int, text: String)

object PlantWorkshopApp {

  // --- STATE ---
  val isAdvancedMode: Var[Boolean] = Var(false)

  // Standard-Text für den Editor
  val defaultEditorCode = 
      """void loop() {
        |   // 1. Pumpe einschalten
        |   
        |   
        |   // 2. 1 Sekunde warten
        |   
        |   
        |   // 3. Pumpe ausschalten
        |   
        |   
        |   // 4. Lange Pause machen
        |   
        |}""".stripMargin

  // HIER speichern wir den Text dauerhaft, auch beim Wechseln der Ansicht
  val advancedCodeState: Var[String] = Var(defaultEditorCode)

  // Start-Daten: Die Bausteine für die Pumpe
  val allSnippets = List(
    CodeSnippet(1, "digitalWrite(PUMP_PIN, HIGH);"),
    CodeSnippet(2, "delay(1000);"),
    CodeSnippet(3, "digitalWrite(PUMP_PIN, LOW);"),
    CodeSnippet(4, "delay(60000);") 
  )

  val sourceSnippets: Var[List[CodeSnippet]] = Var(allSnippets)
  val targetSnippets: Var[List[CodeSnippet]] = Var(List.empty)

  val draggingSnippet: Var[Option[CodeSnippet]] = Var(None)
  val targetHoverIndex: Var[Option[Int]] = Var(None)

  /** Entry point to render the workshop into a given container */
  def render(container: dom.Element): Unit = {
    com.raquo.laminar.api.L.render(container, appElement)
  }

  // --- UI STRUKTUR ---
  def appElement: HtmlElement = {
    div(
      className := "container",
      
      // 1. Header & Aufgabenstellung (Das schöne Design vom Anfang)
      h1("Pumpensteuerung"),
      div(
        className := "task-box",
        h3("Aufgabe"),
        p("Wir wollen, dass die Pflanze gegossen wird. Schreibe ein Programm, das die Pumpe für genau 1 Sekunde einschaltet."),
        p("Danach soll die Pumpe wieder ausgehen und das Programm soll 60 Sekunden warten, bevor es von vorne beginnt."),
        div(
            className := "info-box",
            strong("Hinweis: "), 
            "Das Arduino 'loop' wiederholt sich unendlich oft. Ziel ist also nur einmal den richtigen Ablauf zu programmieren, welcher sich dann immer wiederholt."
        )
      ),

      // 2. Toggle Switch
      div(
        className := "controls",
        label(
          className := "switch",
          input(typ := "checkbox", onInput.mapToChecked --> isAdvancedMode),
          span(className := "slider")
        ),
        span(
            className := "mode-text", 
            child.text <-- isAdvancedMode.signal.map(if (_) "Modus: Fortgeschritten (Code Editor)" else "Modus: Anfänger (Puzzle)")
        )
      ),

      // 3. Arbeitsbereich (Wechselt je nach Modus)
      div(
        className := "workspace",
        child <-- isAdvancedMode.signal.map {
          case true  => advancedView()
          case false => beginnerView()
        }
      )
    )
  }

  // --- VIEW: ANFÄNGER (Puzzle) ---
  def beginnerView(): HtmlElement = {
    div(
      h4("Setze die Bausteine in die richtige Reihenfolge:"),
      div(
        className := "dnd-area",

        // >>> LINKER CONTAINER (QUELLE) <<<
        div(
          className := "snippet-container source",
          h5("Verfügbare Bausteine"),

          // Drop erlauben (Zurücklegen)
          onDragOver.preventDefault --> { _ => targetHoverIndex.set(None) },
          
          onDrop.preventDefault --> { _ =>
            draggingSnippet.now().foreach { snippet =>
              targetSnippets.update(_.filterNot(_.id == snippet.id))
              sourceSnippets.update { list => 
                 val clean = list.filterNot(_.id == snippet.id)
                 (clean :+ snippet).sortBy(_.id) // Sortiert einfügen
              }
            }
            draggingSnippet.set(None)
            targetHoverIndex.set(None)
          },

          // Liste rendern (gefiltert: gezogenes Item ist unsichtbar)
          children <-- sourceSnippets.signal.combineWith(draggingSnippet.signal).map { 
            case (list, dragging) =>
              list.filterNot(s => dragging.exists(_.id == s.id))
                  .map(s => renderDraggableItem(s, isTarget = false))
          }
        ),

        // >>> RECHTER CONTAINER (LÖSUNG) <<<
        div(
          className <-- targetHoverIndex.signal.map(opt => if(opt.isDefined) "snippet-container target drag-over" else "snippet-container target"),
          h5("Dein Programmablauf (Loop)"),

          // Geometrie-Logik: Wo soll der Platzhalter hin?
          onDragOver.preventDefault --> { e =>
             val container = e.currentTarget.asInstanceOf[dom.html.Div]
             val items = container.querySelectorAll(".sortable-item")
             val mouseY = e.clientY
             
             var newIndex = items.length // Default: Ende
             var found = false
             var i = 0
             while(i < items.length && !found) {
               val rect = items.item(i).asInstanceOf[dom.html.Div].getBoundingClientRect()
               val middleY = rect.top + (rect.height / 2)
               if (mouseY < middleY) { newIndex = i; found = true }
               i += 1
             }
             targetHoverIndex.set(Some(newIndex))
          },

          // Drop Logik
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
              case _ => // Fallback
            }
            draggingSnippet.set(None)
            targetHoverIndex.set(None)
          },

          // Rendern mit Placeholder
          children <-- targetSnippets.signal
            .combineWith(draggingSnippet.signal, targetHoverIndex.signal)
            .map { case (snippets, dragging, hoverIdx) =>
               // 1. Filtern
               val visible = snippets.filterNot(s => dragging.exists(_.id == s.id))
               // 2. Items bauen
               val elements = visible.zipWithIndex.map { case (s, i) => renderDraggableItem(s, true, i) }
               // 3. Placeholder einfügen
               hoverIdx match {
                 case Some(idx) =>
                   val safe = Math.min(idx, elements.length)
                   val (f, b) = elements.splitAt(safe)
                   f ++ List(div(className := "drop-placeholder")) ++ b
                 case None => elements
               }
            }
        )
      )
    )
  }

  // --- HELPER: DRAGGABLE ITEM ---
  def renderDraggableItem(snippet: CodeSnippet, isTarget: Boolean, myIndex: Int = -1): HtmlElement = {
    div(
      className := "code-block sortable-item", // sortable-item für Selektor wichtig
      draggable := true,
      snippet.text,
      
      onDragStart --> { _ => setTimeout(0) { draggingSnippet.set(Some(snippet)) } },
      
      onDragEnd --> { _ => 
        draggingSnippet.set(None)
        targetHoverIndex.set(None)
      },
      
      // Item selbst muss keine Events mehr behandeln (macht der Container)
      emptyMod
    )
  }

  // --- VIEW: FORTGESCHRITTEN ---
  def advancedView(): HtmlElement = {

    var editorInstance: Option[CodeJar] = None

    div(
      h4("Schreibe den Code für die Pumpensteuerung (Arduino C++):"),
      
      div(
        className := "editor-container language-cpp", 
        
        onMountUnmountCallback(
          mount = { nodeCtx =>
            val element = nodeCtx.thisNode.ref.asInstanceOf[dom.Element]
            
            val highlightFn: js.Function1[dom.Element, Unit] = { el =>
              Prism.highlightElement(el)
            }

            val jar = new CodeJar(element, highlightFn)
            
            // Text aus dem global State
            jar.updateCode(advancedCodeState.now())
            
            // Beim Tippen speichern
            jar.onUpdate((code: String) => advancedCodeState.set(code))
            
            editorInstance = Some(jar)
          },
          unmount = { _ =>
            editorInstance.foreach(_.destroy())
            editorInstance = None
          }
        )
      ),
      
      // Button Logik
      button(
        "Code Simulieren & Prüfen", 
        className := "btn-check", 
        onClick --> { _ => 
           // Code aus der Variable lesen
           val code = advancedCodeState.now()
           if(code.contains("digitalWrite(PUMP_PIN") && code.contains("delay(1000)") && code.contains("delay(60000)")) {
             dom.window.alert("Code sieht gut aus!\n")
           } else {
             dom.window.alert("Hmm, da fehlen noch Befehle.")
           }
        }
      )
    )
  }
}
