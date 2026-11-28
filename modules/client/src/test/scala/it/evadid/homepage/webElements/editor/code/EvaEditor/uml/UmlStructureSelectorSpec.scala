package it.evadid.homepage.webElements.editor.code.EvaEditor.uml

import it.evadid.vm.structure.JavaUmlDiagram
import com.raquo.airstream.ownership.ManualOwner
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.core.datastructures.uml.*
import it.evadid.workbook.interactions.programming.state.ProgrammingStateJavaStructured
import org.scalajs.dom

class UmlStructureSelectorSpec extends EditorDomSuite {
  private val labels = UmlStructureSelector.OriginLabels(key => Val(Map("umlLibraryClass" -> "library", "umlLibraryClassHint" -> "view only",
    "umlUnknownClass" -> "unknown", "umlImpliedMemberHint" -> "implied").getOrElse(key, key)))
  private val coach =
    """public class Coach {
      |    public String name;
      |    public String role;
      |    public int experience = 3;
      |    public void trainTeam() { }
      |    public void makeStrategy() { }
      |}""".stripMargin
  private val player =
    """public class Player {
      |    private static int count;
      |    protected String position;
      |    int jerseyNumber;
      |    public Player(final String position, int jerseyNumber) { this.position = position; }
      |    @Override public String toString() { return position; }
      |    public void train(java.util.Map<String, Integer> plan) { }
      |    public void train() { }
      |    static { count = 0; }
      |}""".stripMargin
  private val state = ProgrammingStateJavaStructured("import java.util.*;\n\n" + coach + "\n\n" + player)

  test("classes become UML boxes with attribute and function lines in source order") {
    val diagram = JavaUmlDiagram(state)
    assertEquals(diagram.classes.map(_.name), Seq("Coach", "Player"))
    val Seq(coachBox, playerBox) = diagram.classes: @unchecked
    assertEquals(coachBox.code, coach)
    assertEquals(coachBox.attributes.map(_.label), Seq("+name: String", "+role: String", "+experience: int"))
    assertEquals(coachBox.functions.map(_.label), Seq("+trainTeam(): void", "+makeStrategy(): void"))
    assertEquals(playerBox.attributes.map(_.label), Seq("-count: int", "#position: String", "~jerseyNumber: int"))
    assertEquals(playerBox.functions.map(_.label), Seq("+Player(position: String, jerseyNumber: int)",
      "+toString(): String", "+train(plan: java.util.Map<String, Integer>): void", "+train(): void"))
    assertEquals(playerBox.attributes.map(_.isStatic), Seq(true, false, false))
  }

  test("interfaces, inheritance and attribute associations use the shared UML model") {
    val diagram = JavaUmlDiagram(ProgrammingStateJavaStructured(
      "interface Named { String name(); } class Base {} " +
        "class Team extends Base implements Named { java.util.List<Player> players; void act() { Coach.train(); } } " +
        "class Player {} class Coach {}"))
    val named = diagram.classes.find(_.name == "Named").get
    assert(named.isInterface)
    assert(named.functions.head.isAbstract)
    assert(diagram.relations.contains(UmlRelation("Team", "Base", UmlRelation.Kind.Inheritance)))
    assert(diagram.relations.contains(UmlRelation("Team", "Named", UmlRelation.Kind.Realization)))
    assert(diagram.relations.contains(UmlRelation("Team", "Player", UmlRelation.Kind.Association, Some("players"), many = true)))
    assert(!diagram.relations.exists(_.to == "Coach"), "using a class draws no arrow")
    val selection = Var(Option.empty[UmlEntityKey])
    val unmount = mount(UmlStructureSelector(Val(diagram), selection.signal, selection.writer.contramap(Some(_)), labels = labels).getDomElement())
    try assertEquals(dom.document.querySelector(".uml-class__stereotype").textContent, "«interface»")
    finally unmount()
  }

  test("relationship routes meet the boxes and do not draw self or overlapping arrows") {
    import UmlRelationLayout.*
    val relation = UmlRelation("Child", "Parent", UmlRelation.Kind.Inheritance)
    val upper = Box(0, 0, 100, 50)
    val lower = Box(150, 100, 100, 50)
    assertEquals(route(relation, lower, upper).map(_.path), Some("M 200 100 V 75 H 50 V 50"))
    assertEquals(route(relation, upper, Box(150, 0, 100, 50)).map(_.path), Some("M 100 25 H 125 V 25 H 150"))
    assertEquals(route(relation.copy(to = "Child"), lower, upper), None)
    assertEquals(route(relation, upper, upper), None)
  }

  test("entities carry the full declarations; overloads are told apart") {
    val diagram = JavaUmlDiagram(state)
    val playerBox = diagram.classes(1)
    assertEquals(diagram.entity(playerBox.key), Some(UmlSelectedEntity(UmlEntityKind.Class, "Player", player)))
    assertEquals(playerBox.attributes.head.entity, UmlSelectedEntity(UmlEntityKind.Attribute, "count", "private static int count;"))
    val Seq(_, toString, first, second) = playerBox.functions.map(_.entity): @unchecked
    assertEquals(toString, UmlSelectedEntity(UmlEntityKind.Function, "toString", "@Override public String toString() { return position; }"))
    assertEquals(first.fullCode, "public void train(java.util.Map<String, Integer> plan) { }")
    assertEquals(second.fullCode, "public void train() { }")
    assertEquals(playerBox.functions.map(_.key.member.map(_.occurrence)), Seq(Some(0), Some(0), Some(0), Some(1)))
  }

  test("the generated Turtle library is a read-only box; an edited copy is clickable") {
    val source = it.evadid.workbook.interactions.programming.state.ProgrammingStateRaw(
      it.evadid.core.datastructures.language.AppLanguage.Python, "forward(10)").toJava.code
    assert(source.contains("class Turtle {"), source)
    val program = Var(ProgrammingStateJavaStructured(source))
    val selection = Var(Option.empty[UmlEntityKey])
    val selector = UmlStructureSelector(program.signal.map(JavaUmlDiagram(_)), selection.signal, selection.writer.contramap(Some(_)),
      classFooter = _ => span(cls := "spec-footer"), labels = labels)
    val unmount = mount(selector.getDomElement())
    try {
      def boxes = (0 until dom.document.querySelectorAll(".uml-class").length).map(dom.document.querySelectorAll(".uml-class")(_))
      def turtle = boxes.find(_.querySelector(".uml-class__header").textContent.contains("Turtle")).getOrElse(fail("No Turtle box"))
      assertEquals(boxes.size, 2)
      assert(turtle.classList.contains("uml-class--library"))
      assertEquals(turtle.querySelector(".uml-class__stereotype").textContent, "«library»")
      assertEquals(turtle.getAttribute("title"), "view only")
      assertEquals(turtle.querySelectorAll("button").length, 0)
      assertEquals(turtle.querySelectorAll(".spec-footer").length, 0)
      assert(turtle.querySelectorAll(".uml-entry--readonly").length > 1)
      assertEquals(dom.document.querySelectorAll(".spec-footer").length, 1)

      program.set(ProgrammingStateJavaStructured(source.replace("move(distance);", "move(2 * distance);")))
      assert(!turtle.classList.contains("uml-class--library"))
      assert(turtle.querySelectorAll("button").length > 1)
      assertEquals(dom.document.querySelectorAll(".spec-footer").length, 2)
    } finally unmount()
  }

  test("recognized library members, implied members and unknown classes are told apart") {
    val program = ProgrammingStateJavaStructured("class Main { static void main(String[] a) { System.out.println(1); System.out2.println(2); Helper.run(4); } }")
    val analysis = it.evadid.vm.analysis.ProgramAnalysis.of(program)
    val diagram = JavaUmlDiagram(program).including(JavaUmlDiagram.external(analysis.libraryDependencies, analysis.unknownDependency))
    val selection = Var(Option.empty[UmlEntityKey])
    val unmount = mount(UmlStructureSelector(Val(diagram), selection.signal, selection.writer.contramap(Some(_)), labels = labels).getDomElement())
    try {
      def box(name: String) = dom.document.querySelector(s".uml-class[data-uml-class='$name']")
      def implied(name: String) = (0 until box(name).querySelectorAll(".uml-entry--implied").length)
        .map(box(name).querySelectorAll(".uml-entry--implied")(_).textContent)
      assert(box("System").classList.contains("uml-class--library"))
      assertEquals(implied("System"), Seq("+out2: SystemOut2"))
      assertEquals(box("System").querySelector(".uml-entry--implied").getAttribute("title"), "implied")
      assert(box("System").textContent.contains("+out: PrintStream"))
      assert(box("Helper").classList.contains("uml-class--unknown"))
      assertEquals(box("Helper").querySelector(".uml-class__stereotype").textContent, "«unknown»")
      assertEquals(implied("Helper"), Seq("+run(arg0)"))
    } finally unmount()
  }

  test("clicking names, attributes and functions selects them; the selection follows source edits") {
    val source = Var(state)
    val selection = Var(Option.empty[UmlEntityKey])
    val selector = UmlStructureSelector(source.signal.map(JavaUmlDiagram(_)), selection.signal, selection.writer.contramap(Some(_)), labels = labels)
    val owner = ManualOwner()
    val selected = selector.selectedEntitySignal.observe(using owner)
    val unmount = mount(selector.getDomElement())
    try {
      def entries = (0 until dom.document.querySelectorAll(".uml-entry").length)
        .map(index => dom.document.querySelectorAll(".uml-entry")(index).asInstanceOf[dom.HTMLButtonElement])
      def click(label: String): Unit = entries.find(_.textContent == label).getOrElse(fail(s"No entry '$label'")).click()
      def pressed = entries.filter(_.getAttribute("aria-pressed") == "true").map(_.textContent)

      assertEquals(dom.document.querySelectorAll(".uml-class").length, 2)
      assertEquals(selected.now(), None)
      click("Coach")
      assertEquals(selected.now(), Some(UmlSelectedEntity(UmlEntityKind.Class, "Coach", coach)))
      click("+experience: int")
      assertEquals(selected.now(), Some(UmlSelectedEntity(UmlEntityKind.Attribute, "experience", "public int experience = 3;")))
      assertEquals(pressed, Seq("+experience: int"))
      click("+train(): void")
      assertEquals(selected.now(), Some(UmlSelectedEntity(UmlEntityKind.Function, "train", "public void train() { }")))

      source.set(ProgrammingStateJavaStructured(coach + "\n\n" + player.replace("public void train() { }", "public void train() { run(); }")))
      assertEquals(selected.now(), Some(UmlSelectedEntity(UmlEntityKind.Function, "train", "public void train() { run(); }")))
      assertEquals(pressed, Seq("+train(): void"))

      source.set(ProgrammingStateJavaStructured(coach))
      assertEquals(selected.now(), None)
      assertEquals(dom.document.querySelectorAll(".uml-class").length, 1)
    } finally {
      unmount()
      owner.killSubscriptions()
    }
  }
}
