package it.evadid.homepage.webElements.editor.code.EvaEditor.uml

import com.raquo.airstream.ownership.ManualOwner
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.webElements.editor.code.EvaEditor.uml.UmlStructureSelector.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaStructured
import org.scalajs.dom

class UmlStructureSelectorSpec extends EditorDomSuite {
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
    val diagram = UmlClassDiagram(state)
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

  test("entities carry the full declarations; overloads are told apart") {
    val diagram = UmlClassDiagram(state)
    val playerBox = diagram.classes(1)
    assertEquals(diagram.entity(playerBox.key), Some(SelectedClass("Player", player)))
    assertEquals(playerBox.attributes.head.entity, SelectedAttribute("count", "private static int count;"))
    val Seq(_, toString, first, second) = playerBox.functions.map(_.entity): @unchecked
    assertEquals(toString, SelectedFunction("toString", "@Override public String toString() { return position; }"))
    assertEquals(first.fullCode, "public void train(java.util.Map<String, Integer> plan) { }")
    assertEquals(second.fullCode, "public void train() { }")
    assertEquals(playerBox.functions.map(_.key.member.map(_.occurrence)), Seq(Some(0), Some(0), Some(0), Some(1)))
  }

  test("the generated Turtle library is not drawn, while an edited copy is") {
    val source = it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw(
      it.evadid.core.datastructures.language.AppLanguage.Python, "forward(10)").toJava.code
    assert(source.contains("class Turtle {"), source)
    assertEquals(UmlClassDiagram(ProgrammingStateJavaStructured(source)).classes.map(_.name), Seq("Main"))
    val edited = source.replace("move(distance);", "move(2 * distance);")
    assertEquals(UmlClassDiagram(ProgrammingStateJavaStructured(edited)).classes.map(_.name), Seq("Main", "Turtle"))
  }

  test("clicking names, attributes and functions selects them; the selection follows source edits") {
    val source = Var(state)
    val selector = UmlStructureSelector(source.signal)
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
      assertEquals(selected.now(), Some(SelectedClass("Coach", coach)))
      click("+experience: int")
      assertEquals(selected.now(), Some(SelectedAttribute("experience", "public int experience = 3;")))
      assertEquals(pressed, Seq("+experience: int"))
      click("+train(): void")
      assertEquals(selected.now(), Some(SelectedFunction("train", "public void train() { }")))

      source.set(ProgrammingStateJavaStructured(coach + "\n\n" + player.replace("public void train() { }", "public void train() { run(); }")))
      assertEquals(selected.now(), Some(SelectedFunction("train", "public void train() { run(); }")))
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
