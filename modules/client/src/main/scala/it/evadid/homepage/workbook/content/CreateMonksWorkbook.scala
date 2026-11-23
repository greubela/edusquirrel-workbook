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
import it.evadid.workbook.elements.interactionElements.choice.WorkbookContentChoiceInteraction
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGraphic.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{RecreateTurtleDidacticConfig, TurtleGraphic, TurtleRecreateShapeInteraction}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStateJavaStructured, ProgrammingStateSnapScalaModel}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction.ReorderMapIdInteraction
import it.evadid.workbook.elements.interactionElements.slideshow.*
import it.evadid.workbook.elements.interactionElements.table.{AnswerTableInteraction, EditableTableCell}
import it.evadid.workbook.elements.structureElements.{ExerciseGroup, Workbook}


/** Recursion workbook "Die Mönche von Mons Komputarius".
 *
 * Sections 0 (monks), 1 (tower), 2 (editor basics and the building-brick methods),
 * 3 (drawing recursively with bricks) and 4 (Koch curve). Task titles are numbered per section
 * ("Aufgabe 2.3") in the language maps.
 *
 * The programming tasks E1–E8 open the Eva editor with Java starting code; their targets are recorded
 * from the sample solutions in [[CreateMonksWorkbook.editorTarget]]. Element IDs are stable per task
 * (`monks-a2`, `monks-e5`, ...) and independent of the displayed numbers.
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
                     optionKeys: Option[List[String]] = None): WorkbookContentChoiceInteraction =
    contentChoice("monks-" + id, lm(id + "-question"),
      optionKeys.getOrElse(List.tabulate(optionCount)(i => s"$id-opt$i")).map(lm), multiple, Some(expected))

  /** One single-choice row per statement; `answers` name the correct category for each `<id>-row<i>`. */
  private def assign(id: String, categories: List[String], answers: List[String]): List[WorkbookElement] =
    answers.zipWithIndex.map { (answer, i) =>
      contentChoice(s"monks-$id-$i", lm(s"$id-row$i"), categories.map(c => lm("cat-" + c)),
        expected = Some(List(categories.indexOf(answer))))
    }

  /** Free-input table. A cell lists accepted spellings separated by `|`; `columns` and `rowHeader` are full
   * language-map keys. */
  private def table(id: String, columns: List[String], answers: List[List[String]], rowHeader: String): AnswerTableInteraction =
    AnswerTableInteraction("monks-" + id, lm(id + "-caption"),
      answers.indices.map(i => lm(s"$id-row$i")).toList, columns.map(lm),
      answers.map(_.map(cell => EditableTableCell(expected = Some(cell.split('|').toList)))), Some(lm(rowHeader)))

  /** One question per row, answered in a single column. */
  private def singleColumn(id: String, answers: List[String]): AnswerTableInteraction =
    table(id, List("answercolumn"), answers.map(List(_)), "questioncolumn")

  /** `items` are listed in their correct order; the interaction shuffles them. */
  private def reorder(id: String, itemCount: Int): ReorderMapIdInteraction =
    ReorderMapIdInteraction("monks-" + id, List.tabulate(itemCount)(i => lm(s"$id-item$i")), seed = id.hashCode.toLong)

  /** Editor task: prompt and a Java turtle editor whose drawing is compared with the sample solution. */
  private def editorTask(id: String, extra: List[WorkbookElement] = Nil): WorkbookElement =
    task(id, List(prompt(id),
      TurtleRecreateShapeInteraction("monks-" + id, ProgrammingStateJavaStructured(EditorCode(id)), editorTarget(id),
        customExerciseConfig = Some(EditorConfig))) ++ extra)

  // Building once makes IDs and state references stable across createWorkbook/createEverything calls.
  override lazy val createWorkbook: Workbook = workbook(
    key("workbooktitle"),
    List(
      section("welcomeTitle", key("part0"), List[WorkbookElement](
        text("welcome"),
      )),
      section("monks-story", key("parta"), List(
        container(key("parta"), List(
          text("introduction"),
          Slideshow("monks-theater", theaterScenes.map(panel)),
          text("schemaintro"), text("schemacode"))),
        task("a1", List(prompt("a1"), reorder("a1", 8))),
        task("a2", List(singleColumn("a2", List("3", "5", "5 4 2|5,4,2|5, 4, 2|5 , 4 , 2", "5")))),
        task("a3", List(choice("a3", List(1), 4))),
        // "none" stays an option (distractor) so stored selections of the remaining rows stay valid.
        task("a4", List(prompt("a4")) ++ assign("a4",
          List("abort", "simplify", "call", "combine", "first", "none"),
          List("simplify", "call", "abort", "combine", "first"))),
        // The story as code: one method with four gaps, one question per gap.
        task("ac", List(text("ac-intro"), text("ac-code"),
          choice("ac-1", List(0), 3), choice("ac-2", List(1), 3), choice("ac-3", List(2), 3), choice("ac-4", List(1), 3))),
        // Refers to the gaps of task "ac" instead of showing the finished method, which would spoil "ac".
        task("a5", List(prompt("a5")) ++ assign("a5",
          List("abort", "simplify", "call", "combine"),
          List("abort", "simplify", "simplify", "call", "combine"))),
        task("a6", List(choice("a6", List(1, 3, 5), 6, multiple = true))),
        task("a7", List(choice("a7", List(1, 3, 5), 6, multiple = true))),
        task("a8", List(text("a8intro"),
          choice("a8a", List(1), 4),
          singleColumn("a8b", List("6")),
          choice("a8c", List(2), 4))))),

      section("monks-tower", key("partc"), List(
        container(key("partc"), List(
          prompt("c0"),
          Slideshow("monks-counting-story", countingScenes.map(panel)))),
        task("c1", List(prompt("c1")) ++ assign("c1",
          List("abort", "simplify", "call", "combine", "state"),
          List("simplify", "state", "call", "abort", "combine"))),
        task("c2", List(
          table("c2", List("c2-col0", "c2-col1", "c2-col2"), List(List("2", "1", "1"), List("1", "2", "2"), List("0", "3", "3")),
            "c2-rowheader"),
          singleColumn("c2b", List("4")))),
        task("c3", List(prompt("c3"), text("c3code")) ++ assign("c3",
          List("variant-a", "variant-b", "variant-c"), List("variant-a", "variant-b", "variant-c"))),
        task("c4", List(singleColumn("c4", List("6", "3", "5", "3")))),
        task("c5", List(text("functionalexplanation"), choice("c5", List(1, 3, 4, 5), 6, multiple = true))))),

      // Placed right before the drawing section, so learners use the editor directly after getting to know it.
      section("monks-editor", key("partzero"), List(
        container(key("partzero"), List(text("zerointro"))),
        editorTask("e1"), editorTask("e2"), editorTask("e3"))),

      // Drawing with the building-brick methods from the editor section (tower, pyramids, contract).
      section("monks-bridge", key("partd"), List(
        container(key("partd"), List(text("partdintro"))),
        editorTask("e4"), editorTask("e5"), editorTask("e6a"), editorTask("e6b"),
        task("d1", List(choice("d1a", List(1), 4), choice("d1b", List(1, 3), 5, multiple = true), text("d1note"))),
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
              ProgrammingStateSnapScalaModel.empty,
              kochTarget(depth),
              customExerciseConfig = Some(EditorConfig))))
        }),
      section("finishTitle", key("partX"), List[WorkbookElement](
        text("finish"),
        container(key("survey"), List(
          text("survey-todo"))
        ),
        container(key("submit"), List(
          text("submit-todo"))
        ),
      )),
    ),
    User.AndreGreubel
  )
}

object CreateMonksWorkbook {
  /** Python is hidden in this workbook for now; learners work in Snap! or Java. */
  val EditorLanguages: List[AppLanguage.ProgrammingLanguage] = List(AppLanguage.SnapLanguage, AppLanguage.Java)

  val EditorConfig: RecreateTurtleDidacticConfig = RecreateTurtleDidacticConfig.defaultForPalette(ProgrammingEditorPalette.PythonCompatibleSnap)
    .copy(restrictEditorToLanguages = EditorLanguages)

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
    Scene("family", "filebeatfamilychildquestion", List("familydialogue", "family-father-replydialogue",
      "family-child-questiondialogue")),
    Scene("count-red", "filebeatcounttally", List("count-explanationdialogue", "narr-count-red")),
    Scene("count-yellow", "filebeatcounttallytwo", List("narr-count-yellow")),
    Scene("count-blue", "filebeatcountempty", List("narr-count-blue", "count-resultdialogue", "count-brokendialogue",
      "count-rememberdialogue", "narr-count-rebuild", "count-grandmadialogue")))

  /** Java starting code of the editor tasks E1–E8. Section 0 lets the learner write the building-brick methods
   * `stein`, `hoch`, `runter` and `springe`; from E4 on they are prefilled ([[EditorCode.helpers]]).
   * Identifiers follow the German task texts; the class name is free, `Turtle` is reserved by the runner.
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
        |    static void hoch() {
        |        Turtle.penUp();
        |        Turtle.turnLeft(90);
        |        Turtle.forward(20);
        |        Turtle.turnRight(90);
        |        Turtle.penDown();
        |    }
        |
        |    static void runter() {
        |        Turtle.penUp();
        |        Turtle.turnRight(90);
        |        Turtle.forward(20);
        |        Turtle.turnLeft(90);
        |        Turtle.penDown();
        |    }
        |
        |    static void springe(double weite) {
        |        Turtle.penUp();
        |        Turtle.forward(weite);
        |        Turtle.penDown();
        |    }
        |""".stripMargin

    private def program(body: String, withHelpers: Boolean = false): String =
      "public class Zeichnung {\n" + (if withHelpers then helpers + "\n" else "") + body.stripMargin + "}\n"

    private val pyramidMain: String =
      """    public static void main(String[] args) {
        |        zeichnePyramide(4);
        |        springe(100);
        |        zeichnePyramide(3);
        |    }
        |"""

    private val byTask: Map[String, String] = Map(
      "e1" -> program(
        """    public static void main(String[] args) {
          |        Turtle.forward(60);
          |        Turtle.turnLeft(90);
          |        // TODO
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
          |    static void runter() {
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
          |    static void springe(double weite) {
          |        // TODO
          |    }
          |
          |    public static void main(String[] args) {
          |        vieleck(4, 60);
          |        // TODO
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
          |        hoch();
          |        springe(10);
          |        zeichnePyramide(n - 1);
          |        springe(-10);
          |        runter();
          |    }
          |
          |""" + pyramidMain, withHelpers = true),
      "e6b" -> program(
        """    static void zeichnePyramide(int n) {
          |        if (n == 0) {
          |            return;
          |        }
          |        stein(n * 20);
          |        hoch();
          |        springe(10);
          |        zeichnePyramide(n - 1);
          |    }
          |
          |""" + pyramidMain, withHelpers = true),
      "e7" -> program(
        """    static void cantor(double breite, int n) {
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
  private object Sketch {
    import TurtleCommand.*

    def stein(breite: Double): List[TurtleCommand[Double]] =
      List.fill(2)(List(Forward(breite), Left(90.0), Forward(20.0), Left(90.0))).flatten
    def hoch(): List[TurtleCommand[Double]] = List(PenUp(), Left(90.0), Forward(20.0), Right(90.0), PenDown())
    def runter(): List[TurtleCommand[Double]] = List(PenUp(), Right(90.0), Forward(20.0), Left(90.0), PenDown())
    def springe(weite: Double): List[TurtleCommand[Double]] = List(PenUp(), Forward(weite), PenDown())

    def vieleck(ecken: Int, laenge: Double): List[TurtleCommand[Double]] =
      List.fill(ecken)(List(Forward(laenge), Left(360.0 / ecken))).flatten

    def turm(n: Int): List[TurtleCommand[Double]] =
      if n <= 0 then Nil else stein(40) ++ hoch() ++ turm(n - 1)

    def pyramide(n: Int): List[TurtleCommand[Double]] =
      if n <= 0 then Nil
      else stein(n * 20) ++ hoch() ++ springe(10) ++ pyramide(n - 1) ++ springe(-10) ++ runter()

    /** One brick per level; the next level starts two brick heights lower. */
    def cantor(breite: Double, n: Int): List[TurtleCommand[Double]] =
      stein(breite) ++ (if n <= 0 then Nil else
        runter() ++ runter() ++ cantor(breite / 3, n - 1) ++ springe(2 * breite / 3) ++
          cantor(breite / 3, n - 1) ++ springe(-2 * breite / 3) ++ hoch() ++ hoch())

    def baum(laenge: Double, n: Int): List[TurtleCommand[Double]] =
      if n <= 0 then Nil
      else List(Forward(laenge), Left(30.0)) ++ baum(laenge * 0.6, n - 1) ++
        List(Right(60.0)) ++ baum(laenge * 0.6, n - 1) ++ List(Left(30.0)) ++ springe(-laenge)
  }

  /** Target drawing of an editor task, recorded from its sample solution. */
  def editorTarget(id: String): TurtleGraphic = {
    import Sketch.*
    val commands: List[TurtleCommand[Double]] = id match {
      case "e1" => List(TurtleCommand.Forward(60.0), TurtleCommand.Left(90.0), TurtleCommand.Forward(20.0),
        TurtleCommand.Left(90.0), TurtleCommand.Forward(60.0), TurtleCommand.Left(90.0), TurtleCommand.Forward(20.0))
      case "e2" => stein(80) ++ hoch() ++ stein(60) ++ hoch() ++ stein(40)
      case "e3" => vieleck(4, 60) ++ springe(100) ++ vieleck(6, 40)
      case "e4" => turm(5)
      case "e5" | "e6a" | "e6b" => pyramide(4) ++ springe(100) ++ pyramide(3)
      case "e7" => cantor(270, 3)
      case "e8" => List(TurtleCommand.Left(90.0)) ++ baum(80, 5)
      case other => throw IllegalArgumentException(s"No editor target for $other")
    }
    TurtleGraphic(commands)
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
