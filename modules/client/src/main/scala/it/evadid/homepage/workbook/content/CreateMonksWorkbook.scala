package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.file.CopyrightInfo
import it.evadid.core.datastructures.geometry.{Line, Point}
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.user.User
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.abstractions.{TypeOfTextDisplay, WorkbookDisplayElement, WorkbookElement}
import it.evadid.workbook.elements.displayElements.WorkbookImageElement.LanguageMapBasedWorkbookImageElement
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement.HintLabel
import it.evadid.workbook.elements.interactionElements.choice.ChoiceInteraction
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGraphic.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleGraphic, TurtleRecreateShapeInteraction}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStateJavaString, ProgrammingStatePythonString}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction.ReorderMapIdInteraction
import it.evadid.workbook.elements.interactionElements.slideshow.*
import it.evadid.workbook.elements.interactionElements.table.{AnswerTableInteraction, EditableTableCell}
import it.evadid.workbook.elements.structureElements.{ExerciseGroup, Workbook}

import scala.collection.mutable.ListBuffer

/** Recursion workbook "Die Mönche von Mons Komputarius".
 *
 * Parts: 0 (editor basics), A (monks), B (outward/return path), C (tower), D (bridge to the final task)
 * and the Koch curve practice (section 3).
 *
 * The programming tasks E1–E8 open the Eva editor with Java starting code; their targets are recorded
 * from the sample solutions in [[CreateMonksWorkbook.editorTarget]]. Element IDs are stable per task
 * (`monks-a2`, `monks-e5`, ...).
 */
case class CreateMonksWorkbook(override val fullInfo: FullInfo) extends WorkbookFactory {

  import CreateMonksWorkbook.*

  override val workbookId: String = "MonksWorkbook"

  override def availableLanguages: List[AppLanguage.HumanLanguage] = List(AppLanguage.German, AppLanguage.English)

  private def key(name: String): String = "monksworkbook/" + name

  private def lm(name: String): LanguageMapContentId = LanguageMapContentId(key(name))

  private def panel(scene: Scene): WorkbookDisplayElement =
    ExerciseGroup("monks-panel-" + scene.id,
      LanguageMapBasedWorkbookImageElement(
        "monks-image-" + scene.id,
        LanguageMapContentId(key(scene.imageKey)),
        TypeOfTextDisplay.URL_RELATIVE_TO_WORKBOOK_RESOURCES,
        Some(LanguageMapContentId(key(scene.lines.head))),
        Some(CopyrightInfo.fromAi)
      ) :: scene.lines.map(line => instructionMarkdown(key(line))))

  // ---- task builders: the task `id` determines the container title and all language-map keys

  private def task(id: String, elements: List[WorkbookElement]): WorkbookElement =
    container(key(id + "title"), elements)

  private def text(name: String): WorkbookElement = instructionMarkdown(key(name))

  private def prompt(id: String): WorkbookElement = text(id + "prompt")

  /** Options are `<id>-opt0..n-1` unless explicit option keys are given. */
  private def choice(id: String, expected: List[Int], optionCount: Int, multiple: Boolean = false,
                     optionKeys: Option[List[String]] = None): ChoiceInteraction =
    ChoiceInteraction("monks-" + id, lm(id + "-question"),
      optionKeys.getOrElse(List.tabulate(optionCount)(i => s"$id-opt$i")).map(lm), multiple, Some(expected))

  /** One single-choice row per statement; `answers` name the correct category for each `<id>-row<i>`. */
  private def assign(id: String, categories: List[String], answers: List[String]): List[WorkbookElement] =
    answers.zipWithIndex.map { (answer, i) =>
      ChoiceInteraction(s"monks-$id-$i", lm(s"$id-row$i"), categories.map(c => lm("cat-" + c)),
        expected = Some(List(categories.indexOf(answer))))
    }

  /** Free-input table. A cell lists accepted spellings separated by `|`; `columns` are full language-map keys. */
  private def table(id: String, columns: List[String], answers: List[List[String]]): AnswerTableInteraction =
    AnswerTableInteraction("monks-" + id, lm(id + "-caption"),
      answers.indices.map(i => lm(s"$id-row$i")).toList, columns.map(lm),
      answers.map(_.map(cell => EditableTableCell(expected = Some(cell.split('|').toList)))))

  private def singleColumn(id: String, answers: List[String]): AnswerTableInteraction =
    table(id, List("answercolumn"), answers.map(List(_)))

  /** `items` are listed in their correct order; the interaction shuffles them. */
  private def reorder(id: String, itemCount: Int): ReorderMapIdInteraction =
    ReorderMapIdInteraction("monks-" + id, List.tabulate(itemCount)(i => lm(s"$id-item$i")), seed = id.hashCode.toLong)

  /** Editor task: prompt and a Java turtle editor whose drawing is compared with the sample solution. */
  private def editorTask(id: String, extra: List[WorkbookElement] = Nil): WorkbookElement =
    task(id, List(prompt(id),
      TurtleRecreateShapeInteraction("monks-" + id, ProgrammingStateJavaString(EditorCode(id)), editorTarget(id),
        ProgrammingEditorPalette.PythonCompatibleSnap, Map.empty[String, Integer])) ++ extra)

  // Building once makes IDs and state references stable across createWorkbook/createEverything calls.
  override lazy val createWorkbook: Workbook = workbook(
    key("workbooktitle"),
    List(
      section("monks-editor", key("partzero"), List(
        container(key("partzero"), List(text("zerointro"))),
        editorTask("e1"), editorTask("e2"), editorTask("e3"))),

      section("monks-story", key("parta"), List(
        container(key("parta"), List(
          text("introduction"),
          Slideshow("monks-theater", theaterScenes.map(panel)),
          text("schemaintro"), text("schemacode"))),
        task("a1", List(prompt("a1"), reorder("a1", 8))),
        task("a2", List(singleColumn("a2", List("3", "5", "5 4 2|5,4,2|5, 4, 2|5 , 4 , 2", "5")))),
        task("a3", List(choice("a3", List(1), 4))),
        task("a4", List(prompt("a4")) ++ assign("a4",
          List("abort", "simplify", "call", "combine", "first", "none"),
          List("simplify", "call", "abort", "combine", "first", "none"))),
        task("a5", List(prompt("a5"), text("a5code")) ++ assign("a5",
          List("abort", "simplify", "call", "combine"),
          List("abort", "simplify", "simplify", "call", "combine"))),
        task("a6", List(choice("a6", List(1, 3, 5), 6, multiple = true))),
        task("a7", List(choice("a7", List(1, 3, 5), 6, multiple = true), text("a7note"))),
        task("a8", List(text("a8intro"),
          choice("a8a", List(1), 4),
          singleColumn("a8b", List("6")),
          choice("a8c", List(2), 4))))),

      section("monks-phases", key("partb"), List(
        task("b1", List(prompt("b1"), text("b1code"), reorder("b1", 8))),
        task("b2", List(choice("b2", List(1, 3, 4, 5), 6, multiple = true))),
        task("b3", List(prompt("b3"), text("b3code")) ++ assign("b3",
          List("exception", "overflow", "ascending", "correct"),
          List("exception", "overflow", "ascending", "correct"))))),

      section("monks-tower", key("partc"), List(
        container(key("partc"), List(
          prompt("c0"),
          Slideshow("monks-counting-story", countingScenes.map(panel)),
          text("countingend"))),
        task("c1", List(prompt("c1")) ++ assign("c1",
          List("abort", "simplify", "call", "combine", "state"),
          List("simplify", "state", "call", "abort", "combine"))),
        task("c2", List(
          table("c2", List("c2-col0", "c2-col1", "c2-col2"), List(List("2", "1", "1"), List("1", "2", "2"), List("0", "3", "3"))),
          singleColumn("c2b", List("4")),
          text("c2c-prompt"), reorder("c2c", 3))),
        task("c3", List(prompt("c3"), text("c3code")) ++ assign("c3",
          List("variant-a", "variant-b", "variant-c"), List("variant-a", "variant-b", "variant-c"))),
        task("c4", List(singleColumn("c4", List("6", "3", "5", "3")))),
        task("c5", List(text("functionalexplanation"), choice("c5", List(1, 3, 4, 5), 6, multiple = true))),
        task("c6", List(choice("c6", List(2), 3, multiple = true,
          optionKeys = Some(List("cat-variant-a", "cat-variant-b", "cat-variant-c"))))),
        container(key("helperslabel"), List(instructionCollapsibleHint(key("helperslabel"), key("helperscode")))),
        editorTask("e4"), editorTask("e5"), editorTask("e6a"), editorTask("e6b"))),

      section("monks-bridge", key("partd"), List(
        task("d1", List(choice("d1a", List(1), 4), choice("d1b", List(1, 3), 5, multiple = true), text("d1note"))),
        task("d2", List(text("d2code"), table("d2", List("answercolumn"), List(List("15"), List("8"), List("9"))))),
        editorTask("e7", List(singleColumn("e7b", List("15")))),
        editorTask("e8", List(singleColumn("e8b", List("7")))))),

      // Section 3 (Koch curve) is authored separately and intentionally unchanged.
      section("monks-koch", key("kochtitle"),
        List(container(key("kochintroductioncont"), List(
          instructionMarkdown(key("kochintroduction"))
        ))) ++ (0 to 2).toList.map { depth =>
          container(key("koch" + depth + "title"), List(
            instructionMarkdown(key("koch" + depth)),
            TurtleRecreateShapeInteraction(
              "monks-koch-" + depth,
              ProgrammingStatePythonString(""),
              kochTarget(depth),
              ProgrammingEditorPalette.PythonCompatibleSnap,
              Map.empty[String, Integer])))
        })
    ),
    User.AndreGreubel
  )
}

object CreateMonksWorkbook {
  /** One slide: a key image plus the lines shown below it. A line is the key of a spoken dialogue
   * (`...dialogue`, prefixed with the speaker in the text) or of a short narration (`narr-...`).
   * Several beats of the story are aggregated into one slide so the stories stay short.
   */
  case class Scene(id: String, imageKey: String, lines: List[String]) derives upickle.default.ReadWriter {
    require(lines.nonEmpty, "A slide needs at least one line")
  }

  val theaterScenes: List[Scene] = List(
    Scene("arrival", "fileimage2", List("narr-arrival", "entrancedialogue")),
    Scene("warning", "filebeattravelershout", List("warningdialogue", "shoutdialogue")),
    Scene("help", "filebeathelpquestion", List("narr-help", "cards-demanddialogue", "in-deed-onedialogue",
      "help-uncertaindialogue", "in-deed-onedialogue", "help-questiondialogue", "in-deed-onedialogue")),
    Scene("journey", "filebeatreturnanswer", List("help-insistentdialogue", "journey-questiondialogue",
      "journey-answerdialogue", "return-questiondialogue", "return-answerdialogue")),
    Scene("patience", "filebeatpatiencespeaking", List("patiencedialogue")),
    Scene("tray", "filebeattraytomaster", List("tray-instructiondialogue", "narr-tray", "watchdialogue")),
    Scene("select-a", "filebeatselect7held", List("narr-select-a")),
    Scene("select-b", "filebeatselect2held", List("narr-select-b")),
    Scene("empty", "filebeatemptyspeaking", List("narr-empty", "emptydialogue", "givebackdialogue")),
    Scene("insert-a", "filebeatinsert4left", List("narr-insert-a")),
    Scene("insert-b", "filebeatinsert9left", List("narr-insert-b")),
    Scene("sorted", "filebeatunderstoodspeaking", List("narr-sorted", "sorteddialogue", "understooddialogue")),
    Scene("farewell", "fileimage13", List("farewelldialogue", "narr-farewell")))

  val countingScenes: List[Scene] = List(
    Scene("family", "filebeatfamilyfatherhurry", List("familydialogue", "family-father-replydialogue",
      "family-child-questiondialogue", "family-father-hurrydialogue")),
    Scene("count-red", "filebeatcounttally", List("count-explanationdialogue", "narr-count-red")),
    Scene("count-yellow", "filebeatcounttallytwo", List("narr-count-yellow")),
    Scene("count-blue", "filebeatcountempty", List("narr-count-blue")))

  /** Java starting code of the editor tasks E1–E8. From E4 on, the helper methods are prefilled
   * (the same code is shown in the collapsible "helpers" block of part C). Identifiers follow the
   * German task texts; the class name is free, `Turtle` is reserved by the runner.
   */
  object EditorCode {
    val helpers: String =
      """    static void stein(double breite) {
        |        Turtle.forward(breite);
        |        Turtle.turnLeft(90);
        |        Turtle.forward(20);
        |        Turtle.turnLeft(90);
        |        Turtle.forward(breite);
        |        Turtle.turnLeft(90);
        |        Turtle.forward(20);
        |        Turtle.turnLeft(90);
        |    }
        |
        |    static void linie(double laenge) {
        |        Turtle.forward(laenge);
        |        Turtle.penUp();
        |        Turtle.forward(-laenge);
        |        Turtle.penDown();
        |    }
        |
        |    static void nachRechts(double a) {
        |        Turtle.penUp();
        |        Turtle.forward(a);
        |        Turtle.penDown();
        |    }
        |
        |    static void nachLinks(double a) {
        |        Turtle.penUp();
        |        Turtle.forward(-a);
        |        Turtle.penDown();
        |    }
        |
        |    static void nachOben(double a) {
        |        Turtle.penUp();
        |        Turtle.turnLeft(90);
        |        Turtle.forward(a);
        |        Turtle.turnRight(90);
        |        Turtle.penDown();
        |    }
        |
        |    static void nachUnten(double a) {
        |        Turtle.penUp();
        |        Turtle.turnRight(90);
        |        Turtle.forward(a);
        |        Turtle.turnLeft(90);
        |        Turtle.penDown();
        |    }
        |""".stripMargin

    private def program(body: String, withHelpers: Boolean = false): String =
      "public class Zeichnung {\n" + (if withHelpers then helpers + "\n" else "") + body.stripMargin + "}\n"

    private val pyramidMain: String =
      """    public static void main(String[] args) {
        |        zeichnePyramide(4);
        |        nachRechts(100);
        |        zeichnePyramide(3);
        |    }
        |"""

    private val byTask: Map[String, String] = Map(
      "e1" -> program(
        """    public static void main(String[] args) {
          |        Turtle.forward(60);
          |        Turtle.turnLeft(90);
          |        // TODO: zweite Haelfte des Rechtecks
          |    }
          |"""),
      "e2" -> program(
        """    static void stein(double breite) {
          |        // TODO
          |    }
          |
          |    static void hoch() {
          |        // TODO
          |    }
          |
          |    public static void main(String[] args) {
          |        stein(80);
          |        hoch();
          |        // TODO
          |    }
          |"""),
      "e3" -> program(
        """    static void vieleck(int ecken, double laenge) {
          |        // TODO
          |    }
          |
          |    public static void main(String[] args) {
          |        vieleck(4, 60);
          |        // TODO: 100 nach rechts (ohne zu zeichnen), dann das Sechseck
          |    }
          |"""),
      "e4" -> program(
        """    static void zeichneTurm(int n) {
          |        // TODO
          |    }
          |
          |    public static void main(String[] args) {
          |        zeichneTurm(5);
          |    }
          |""", withHelpers = true),
      "e5" -> program(
        """    static void zeichnePyramide(int n) {
          |        // TODO
          |    }
          |
          |""" + pyramidMain, withHelpers = true),
      "e6a" -> program(
        """    static void zeichnePyramide(int n) {
          |        stein(n * 20);
          |        nachOben(20);
          |        nachRechts(10);
          |        zeichnePyramide(n - 1);
          |        nachLinks(10);
          |        nachUnten(20);
          |    }
          |
          |""" + pyramidMain, withHelpers = true),
      "e6b" -> program(
        """    static void zeichnePyramide(int n) {
          |        if (n == 0) {
          |            return;
          |        }
          |        stein(n * 20);
          |        nachOben(20);
          |        nachRechts(10);
          |        zeichnePyramide(n - 1);
          |    }
          |
          |""" + pyramidMain, withHelpers = true),
      "e7" -> program(
        """    static void cantor(double laenge, int n) {
          |        // TODO
          |    }
          |
          |    public static void main(String[] args) {
          |        cantor(270, 3);
          |    }
          |""", withHelpers = true),
      "e8" -> program(
        """    static void baum(double laenge, int n) {
          |        // TODO
          |    }
          |
          |    public static void main(String[] args) {
          |        Turtle.turnLeft(90); // die Turtle schaut nach oben
          |        baum(80, 5);
          |    }
          |""", withHelpers = true))

    def apply(id: String): String = byTask.getOrElse(id, throw IllegalArgumentException(s"No starting code for $id"))
  }

  /** Records a sample solution as turtle commands. The helper methods mirror [[EditorCode.helpers]],
   * so the recorded strokes are exactly what the Java runner emits for the same solution.
   */
  private final class Sketch {
    private val commands = ListBuffer.empty[TurtleCommand[Double]]

    def forward(distance: Double): Unit = commands += TurtleCommand.Forward(distance)
    def turnLeft(degrees: Double): Unit = commands += TurtleCommand.Left(degrees)
    def turnRight(degrees: Double): Unit = commands += TurtleCommand.Right(degrees)
    def penUp(): Unit = commands += TurtleCommand.PenUp()
    def penDown(): Unit = commands += TurtleCommand.PenDown()

    def stein(breite: Double): Unit = (0 until 2).foreach { _ =>
      forward(breite); turnLeft(90); forward(20); turnLeft(90)
    }
    def linie(laenge: Double): Unit = { forward(laenge); penUp(); forward(-laenge); penDown() }
    def nachRechts(a: Double): Unit = { penUp(); forward(a); penDown() }
    def nachLinks(a: Double): Unit = { penUp(); forward(-a); penDown() }
    def nachOben(a: Double): Unit = { penUp(); turnLeft(90); forward(a); turnRight(90); penDown() }
    def nachUnten(a: Double): Unit = { penUp(); turnRight(90); forward(a); turnLeft(90); penDown() }

    def vieleck(ecken: Int, laenge: Double): Unit = (0 until ecken).foreach { _ => forward(laenge); turnLeft(360.0 / ecken) }

    def turm(n: Int): Unit = if n > 0 then { stein(40); nachOben(20); turm(n - 1) }

    def pyramide(n: Int): Unit = if n > 0 then {
      stein(n * 20); nachOben(20); nachRechts(10)
      pyramide(n - 1)
      nachLinks(10); nachUnten(20)
    }

    def cantor(laenge: Double, n: Int): Unit = {
      linie(laenge)
      if n > 0 then {
        nachUnten(20)
        cantor(laenge / 3, n - 1)
        nachRechts(2 * laenge / 3)
        cantor(laenge / 3, n - 1)
        nachLinks(2 * laenge / 3); nachOben(20)
      }
    }

    def baum(laenge: Double, n: Int): Unit = if n > 0 then {
      forward(laenge)
      turnLeft(30); baum(laenge * 0.6, n - 1)
      turnRight(60); baum(laenge * 0.6, n - 1)
      turnLeft(30)
      penUp(); forward(-laenge); penDown()
    }

    def graphic: TurtleGraphic = TurtleGraphic(commands.toList)
  }

  /** Target drawing of an editor task, recorded from its sample solution. */
  def editorTarget(id: String): TurtleGraphic = {
    val sketch = new Sketch
    id match {
      case "e1" =>
        sketch.forward(60); sketch.turnLeft(90); sketch.forward(20); sketch.turnLeft(90)
        sketch.forward(60); sketch.turnLeft(90); sketch.forward(20)
      case "e2" => sketch.stein(80); sketch.nachOben(20); sketch.stein(60); sketch.nachOben(20); sketch.stein(40)
      case "e3" => sketch.vieleck(4, 60); sketch.nachRechts(100); sketch.vieleck(6, 40)
      case "e4" => sketch.turm(5)
      case "e5" | "e6a" | "e6b" => sketch.pyramide(4); sketch.nachRechts(100); sketch.pyramide(3)
      case "e7" => sketch.cantor(270, 3)
      case "e8" => sketch.turnLeft(90); sketch.baum(80, 5)
      case other => throw IllegalArgumentException(s"No editor target for $other")
    }
    sketch.graphic
  }

  /** Exact vector targets for the line and the first two Koch refinements.
   * Screen coordinates have y pointing down, so the triangular peak is above
   * the original horizontal line. Each subdivision preserves its endpoints.
   */
  def kochTarget(depth: Int): TurtleLineBasedProgram = {
    require(depth >= 0 && depth <= 2, "This practice uses Koch depths 0, 1 and 2")

    def segments(start: Point[Double], end: Point[Double], remaining: Int): List[Line[Double]] = {
      if (remaining == 0) List(Line(start, end))
      else {
        val dx = (end.x - start.x) / 3
        val dy = (end.y - start.y) / 3
        val first = Point(start.x + dx, start.y + dy)
        val last = Point(start.x + 2 * dx, start.y + 2 * dy)
        val peak = Point(first.x + dx / 2 + dy * math.sqrt(3) / 2,
          first.y + dy / 2 - dx * math.sqrt(3) / 2)
        List(start -> first, first -> peak, peak -> last, last -> end)
          .flatMap { case (from, to) => segments(from, to, remaining - 1) }
      }
    }

    TurtleLineBasedProgram(segments(Point(0.0, 0.0), Point(270.0, 0.0), depth))
  }
}
