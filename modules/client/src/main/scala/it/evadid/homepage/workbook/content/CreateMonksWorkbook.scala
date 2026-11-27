package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.file.CopyrightInfo
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.user.User
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.abstractions.{TypeOfTextDisplay, WorkbookDisplayElement, WorkbookElement}
import it.evadid.workbook.elements.displayElements.WorkbookImageElement.LanguageMapBasedWorkbookImageElement
import it.evadid.workbook.elements.displayElements.WorkbookSubmissionElement
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.choice.WorkbookContentChoiceInteraction
import it.evadid.workbook.elements.interactionElements.matching.{LikertScaleInteraction, MatchingInteraction}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{RecreateTurtleDidacticConfig, TurtleGraphic, TurtleRecreateShapeInteraction}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaStructured
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction.ReorderMapIdInteraction
import it.evadid.workbook.elements.interactionElements.slideshow.*
import it.evadid.workbook.elements.interactionElements.table.{AnswerTableInteraction, EditableTableCell}
import it.evadid.workbook.elements.structureElements.{ExerciseGroup, Workbook}


/** Recursion workbook "Die Mönche von Mons Komputarius".
 *
 * Sections: overview, 1 (the monks' story), 2 (the tower story), 3 (editor and recursive drawing),
 * 4 (Koch curve and snowflake) and the submission with the optional survey. Each container groups the
 * questions of one exercise; its title key names it in the language maps.
 *
 * The programming tasks open the Eva editor with Java starting code; their targets are recorded from the
 * sample solutions in [[CreateMonksWorkbook.editorTarget]]. The lead text before an editor is also its
 * instruction inside the editor (see [[editorTask]]). Element IDs are stable per task (`monks-a2`,
 * `monks-e5`, ...) and independent of the displayed text.
 *
 * Assignment tasks use [[MatchingInteraction]] (connection lines), the survey uses [[LikertScaleInteraction]].
 * The language maps list their keys in order of first use (checked by `MonksWorkbookSpec`).
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
        Some(scene.copyright)
      ) :: (scene.notice.toList ++ scene.lines).map(line => instructionMarkdown(key(line))))

  /** Picture followed by its caption, which says what the picture shows. The image renderer does not display the
   * description itself, so the caption is added as text below the picture. */
  private def figure(id: String, fileKey: String, captionKey: String, copyright: CopyrightInfo): List[WorkbookElement] =
    List(LanguageMapBasedWorkbookImageElement(id, lm(fileKey), TypeOfTextDisplay.URL_RELATIVE_TO_WORKBOOK_RESOURCES,
      Some(lm(captionKey)), Some(copyright)), text(captionKey))

  /** Explanation of the Koch curve of the given depth, followed by its static picture and caption. */
  private def kochFigure(depth: Int): List[WorkbookElement] =
    text("kochexplain" + depth) ::
      figure("monks-image-koch-" + depth, "filekoch" + depth, "kochcaption" + depth, CopyrightInfo.plattformCopyrightInfo())

  // ---- task builders: the task `id` determines its language-map keys

  private def text(name: String): WorkbookElement = instructionMarkdown(key(name))

  private def prompt(id: String): WorkbookElement = text(id + "prompt")

  /** Options are `<id>-opt0..n-1` unless explicit option keys are given. */
  private def choice(id: String, expected: List[Int], optionCount: Int, multiple: Boolean = false,
                     optionKeys: Option[List[String]] = None): WorkbookContentChoiceInteraction =
    contentChoice("monks-" + id, lm(id + "-question"),
      optionKeys.getOrElse(List.tabulate(optionCount)(i => s"$id-opt$i")).map(lm), multiple, Some(expected))

  /** Matching task with connection lines: the statements `<id>-row<i>` on the left, the categories `cat-<c>` on
   * the right; `answers` name the correct category for each statement. Categories may be used several times or
   * not at all, so unused categories act as distractors.
   */
  private def matching(id: String, categories: List[String], answers: List[String]): MatchingInteraction = {
    require(answers.forall(categories.contains), s"Unknown category in $id")
    MatchingInteraction(s"monks-$id-match", None, answers.indices.map(i => lm(s"$id-row$i")).toList,
      categories.map(c => lm("cat-" + c)), Some(answers.map(categories.indexOf)))
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

  /** Editor task: the `lead` text followed by a Java turtle editor whose drawing is compared with the sample
   * solution. The lead is also the instruction in the editor's side panel, so both always show the same task. */
  private def editorTask(id: String, lead: String): List[WorkbookElement] =
    List(text(lead), TurtleRecreateShapeInteraction("monks-" + id, ProgrammingStateJavaStructured(EditorCode(id)),
      editorTarget(id), customExerciseConfig = Some(EditorConfig), instructionText = Some(lm(lead))))

  // Building once makes IDs and state references stable across createWorkbook/createEverything calls.
  override lazy val createWorkbook: Workbook = workbook(
    key("workbooktitle"),
    List(
      section("welcomeTitle", key("welcomeTitle"), List(
        container(key("welcomeTitle"), List(text("welcome"))))),
      section("monks-story", key("sec1title"), List(
        container(key("cont-1-1"), List(
          text("introduction"),
          Slideshow("monks-theater", theaterScenes.map(panel)))),
        container(key("cont-1-2"), List(
          prompt("a1"),
          reorder("a1", 8),
          choice("a3", List(1), 4),
          text("schemaintro"),
          text("schemacode"),
          text("a4pre"),
          // "none" is a distractor: no scene belongs to it.
          matching("a4",
            List("abort", "simplify", "call", "combine", "first", "none"),
            List("simplify", "call", "abort", "combine", "first")))),
        container(key("cont-1-3"), List(
          singleColumn("a2", List("3", "5", "5 4 2|5,4,2|5, 4, 2|5 , 4 , 2", "5")),
          text("javaintro"),
          text("javacode"),
          choice("an", List(1), 4),
          text("stackintro")) ++
          figure("monks-image-callstack", "filecallstack", "stack-caption", FigureCopyright) ++ List(
          singleColumn("a8b", List("6")),
          choice("a8c", List(2), 4))),
        container(key("cont-1-4"), List(
          choice("a7", List(1, 3, 5), 6, multiple = true),
          choice("a6", List(1, 3, 5), 6, multiple = true),
          text("functionaloutro"))))),

      section("monks-tower", key("partc"), List(
        container(key("partc"), List(
          prompt("c0"),
          Slideshow("monks-counting-story", countingScenes.map(panel)))),
        container(key("cont-2-1"), List(
          prompt("c1"),
          matching("c1",
            List("abort", "simplify", "call", "combine"),
            List("simplify", "call", "abort", "combine")),
          table("c2", List("c2-col0", "c2-col1", "c2-col2"), List(List("2", "1", "1"), List("1", "2", "2"), List("0", "3", "3")),
            "c2-rowheader"),
          singleColumn("c2b", List("4")),
          choice("towerCode", List(0), 3))),
        container(key("cont-2-2"), List(
          choice("strichliste", List(1, 2, 3, 5), 8, multiple = true),
          text("sec2outro"))))),

      section("monks-editor", key("sec3Title"), List(
        container(key("sec3Ex1Title"),
          editorTask("e1", "sec3Ex1Instr1") ++ editorTask("e3", "sec3Ex1Instr2")),
        container(key("sec3Ex2Title"),
          editorTask("e4", "sec3Ex2Instr1") ++ editorTask("e5", "sec3Ex2Instr2") ++ editorTask("e6b", "sec3Ex2Instr3") ++
            List(text("sec3Ex2Instr4"))))),

      section("monks-koch", key("sec4Title"), List(
        container(key("sec4Ex1Title"),
          (0 to 2).toList.flatMap(kochFigure) ++ List(
            table("k1", List("k1-col0", "k1-col1"),
              List(List("1", "270"), List("4", "90"), List("16", "30"), List("64", "10")), "k1-rowheader"))),
        container(key("sec4Ex2Title"),
          editorTask("k4", "sec4Ex2Instr1") ++ List(text("sec4Ex2Instr2"))))),

      section("finishTitle", key("finishTitle"), List[WorkbookElement](
        container(key("abschluss"), List(text("finish"))),
        // Optional survey. Consent is opt-in: the checkbox starts unchecked.
        container(key("survey"), List(
          text("survey-intro"),
          checklist(key("survey-consent"), "monks-survey-consent"),
          LikertScaleInteraction("monks-survey", None, List.tabulate(8)(i => lm(s"survey-item$i")),
            List.tabulate(5)(i => lm(s"survey-scale$i"))),
          text("survey-additional"),
          TextInteraction("free-feedback"))),
        container(key("submit"), List(text("submitInstruction"), WorkbookSubmissionElement("monks-submission")))))
    ),
    User.AndreGreubel
  )
}

object CreateMonksWorkbook {
  /** Learners work in Snap!, Python or Java. */
  val EditorLanguages: List[AppLanguage.ProgrammingLanguage] = List(AppLanguage.SnapLanguage, AppLanguage.Python, AppLanguage.Java)

  val EditorConfig: RecreateTurtleDidacticConfig = RecreateTurtleDidacticConfig.defaultForPalette(ProgrammingEditorPalette.PythonCompatibleSnap)
    .copy(restrictEditorToLanguages = EditorLanguages)

  /** One slide: a key image plus the lines shown below it (dialogue prefixed with the speaker in the text, or a
   * short narration). Several beats of the story are aggregated into one slide so the stories stay short.
   */
  case class Scene(id: String, imageKey: String, lines: List[String], copyright: CopyrightInfo = CopyrightInfo.fromAi,
                   notice: Option[String] = None) {
    require(lines.nonEmpty, "A slide needs at least one line")
  }

  /** Figures drawn for this workbook, such as the call stack in section 1 (`tools/dev/monks-callstack-figure.py`). */
  val FigureCopyright: CopyrightInfo = CopyrightInfo(CopyrightInfo.CC_LICENCE, CopyrightInfo.AuthorNameInfo("André Greubel"))

  /** The theater and tower images are Minecraft screenshots whose author is unknown. */
  val ScreenshotCopyright: CopyrightInfo = CopyrightInfo(CopyrightInfo.UnknownLicence, CopyrightInfo.UnknownAuthorInfo())

  /** Slide `number` of a story: image `<imagePrefix><number>` (two digits) and the lines `<linePrefix>-<number>-1..lineCount`,
   * optionally preceded by the notice `<linePrefix>-notice-<number>`, which is shown above the lines without
   * becoming the image caption. */
  private def storyScene(idPrefix: String, imagePrefix: String, linePrefix: String)(number: Int, lineCount: Int,
                         notice: Boolean = false): Scene =
    Scene(idPrefix + number, f"$imagePrefix$number%02d", List.tabulate(lineCount)(line => s"$linePrefix-$number-${line + 1}"),
      ScreenshotCopyright, Option.when(notice)(s"$linePrefix-notice-$number"))

  /** The first two slides of the monks' story carry a notice. */
  val theaterScenes: List[Scene] =
    List(1 -> 3, 2 -> 2, 3 -> 15, 4 -> 8, 5 -> 3, 6 -> 4, 7 -> 3, 8 -> 3, 9 -> 5, 10 -> 2, 11 -> 1, 12 -> 9, 13 -> 1)
      .map((number, lines) => storyScene("comp-scene", "filetheater", "computarius")(number, lines, notice = number <= 2))

  val countingScenes: List[Scene] =
    List(1 -> 8, 2 -> 3, 3 -> 1, 4 -> 5).map((number, lines) => storyScene("count-scene", "filetower", "counting")(number, lines))

  /** Java starting code of the editor tasks. The recursive drawing tasks from E4 on prefill the building-brick
   * methods `stein`, `hoch`, `runter` and `springe` ([[EditorCode.helpers]]).
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
      // The faulty program draws stairs, so the pyramid from e5 does not already fix it.
      "e6b" -> program(
        """    static void zeichneTreppe(int n) {
          |        if (n == 0) {
          |            return;
          |        }
          |        stein(n * 20);
          |        hoch();
          |        zeichneTreppe(n - 1);
          |    }
          |
          |    public static void main(String[] args) {
          |        zeichneTreppe(4);
          |        springe(120);
          |        zeichneTreppe(3);
          |    }
          |""", withHelpers = true),
      "k4" -> program(
        """    static void koch(double laenge, int tiefe) {
          |        // TODO
          |    }
          |
          |    public static void main(String[] args) {
          |        // TODO
          |    }
          |"""))

    def apply(id: String): String = byTask.getOrElse(id, throw IllegalArgumentException(s"No starting code for $id"))
  }

  /** Records a sample solution as turtle commands. The helper methods mirror [[EditorCode.helpers]],
   * so the recorded strokes are exactly what the Java runner emits for the same solution.
   */
  private object Sketch {

    import TurtleCommand.*

    def stein(breite: Double): List[TurtleCommand[Double]] =
      List.fill(2)(List(Forward(breite), Left(90.0), Forward(20.0), Left(90.0))).flatten

    def hoch(): List[TurtleCommand[Double]] = List(PenUp, Left(90.0), Forward(20.0), Right(90.0), PenDown)

    def runter(): List[TurtleCommand[Double]] = List(PenUp, Right(90.0), Forward(20.0), Left(90.0), PenDown)

    def springe(weite: Double): List[TurtleCommand[Double]] = List(PenUp, Forward(weite), PenDown)

    def vieleck(ecken: Int, laenge: Double): List[TurtleCommand[Double]] =
      List.fill(ecken)(List(Forward(laenge), Left(360.0 / ecken))).flatten

    def turm(n: Int): List[TurtleCommand[Double]] =
      if n <= 0 then Nil else stein(40) ++ hoch() ++ turm(n - 1)

    def pyramide(n: Int): List[TurtleCommand[Double]] =
      if n <= 0 then Nil
      else stein(n * 20) ++ hoch() ++ springe(10) ++ pyramide(n - 1) ++ springe(-10) ++ runter()

    /** Stairs aligned on the left; the turtle returns to where the call began. */
    def treppe(n: Int): List[TurtleCommand[Double]] =
      if n <= 0 then Nil else stein(n * 20) ++ hoch() ++ treppe(n - 1) ++ runter()

    /** Koch curve from the current position along the current heading; ends at its end point with the same heading. */
    def koch(laenge: Double, tiefe: Int): List[TurtleCommand[Double]] =
      if tiefe <= 0 then List(Forward(laenge))
      else {
        val part = koch(laenge / 3, tiefe - 1)
        part ++ List(Left(60.0)) ++ part ++ List(Right(120.0)) ++ part ++ List(Left(60.0)) ++ part
      }
  }

  /** Target drawing of an editor task, recorded from its sample solution. */
  def editorTarget(id: String): TurtleGraphic = {
    import Sketch.*
    val commands: List[TurtleCommand[Double]] = id match {
      case "e1" => List(TurtleCommand.Forward(60.0), TurtleCommand.Left(90.0), TurtleCommand.Forward(20.0),
        TurtleCommand.Left(90.0), TurtleCommand.Forward(60.0), TurtleCommand.Left(90.0), TurtleCommand.Forward(20.0))
      case "e3" => vieleck(4, 60) ++ springe(100) ++ vieleck(6, 40)
      case "e4" => turm(5)
      case "e5" => pyramide(4) ++ springe(100) ++ pyramide(3)
      case "e6b" => treppe(4) ++ springe(120) ++ treppe(3)
      // Turning right keeps the peaks, which point to the left of each side, on the outside.
      case "k4" => List.fill(3)(koch(270, 2) ++ List(TurtleCommand.Right(120.0))).flatten
      case other => throw IllegalArgumentException(s"No editor target for $other")
    }
    TurtleGraphic(commands)
  }
}
