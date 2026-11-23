package it.evadid.workbook.elements.interactionElements.programming.state.snap

import it.evadid.core.datastructures.language.AppLanguage.{Python, SnapLanguage}
import it.evadid.vm.conversion.snapexpression.{SnapControlFlow, SnapCustomBlockRules, SnapExpressionSupport}
import it.evadid.vm.modules.BeModules
import it.evadid.vm.simulation.ProgramEntry
import it.evadid.vm.simulation.data.{DataWorld, ListCommand}
import it.evadid.vm.simulation.turtle.TurtleExecutionStatus
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw
import munit.FunSuite

class SnapModuleBlocksSpec extends FunSuite {
  private val sorting =
    """def sortiere():
      |    for runde in range(NumberList.length()):
      |        for i in range(NumberList.length() - 1):
      |            if NumberList.get(i) > NumberList.get(i + 1):
      |                NumberList.swap(i, i + 1)
      |    NumberList.set(0, NumberList.get(0))
      |""".stripMargin

  private def snapXml(python: String): String = ProgrammingStateRaw(Python, python).toSnapXml.code

  test("each list operation has a block whose call spec matches its definition, as Snap requires") {
    assertEquals(SnapModuleBlocks.numberList.map(_.id), List("NumberList.get", "NumberList.set", "NumberList.swap", "NumberList.length"))
    assertEquals(SnapModuleBlocks.numberList.map(_.blockSpec),
      List("item %n of number list", "set item %n of number list to %n", "swap items %n and %n of number list", "length of number list"))
    val library = SnapModuleBlocks.libraryXml(SnapModuleBlocks.numberList, DataWorld(list = Vector(4, 2)))
    val loaded = SnapCustomBlockRules.allDefinitions(library)
    assertEquals(loaded.map(_.blockSpec), SnapModuleBlocks.numberList.map(_.blockSpec))
    assert(loaded.forall(definition => definition.isGlobal && definition.isPythonCompatible), loaded.toString)
    assertEquals(loaded.map(_.arity), SnapModuleBlocks.numberList.map(_.definition.inputs.size), "one slot per function input")
    assert(library.contains("""<variable name="number list"><list struct="atomic">4,2</list></variable>"""), library)
    SnapModuleBlocks.numberList.foreach(block => assertEquals(SnapModuleBlocks.of(block.definition), Some(block)))
  }

  test("list calls become the predefined blocks in Snap, with their definitions and the list variable") {
    val xml = snapXml(sorting)
    assert(xml.contains("""<custom-block s="swap items %n and %n of number list">"""), xml)
    assert(xml.contains("""<custom-block s="item %n of number list">"""), xml)
    assertEquals(SnapCustomBlockRules.obsoleteCalls(xml), Nil)
    assertEquals(SnapExpressionSupport.unsupportedSnapSelectors(xml), Nil)
    assertEquals(SnapCustomBlockRules.globalDefinitions(xml).count(definition => SnapModuleBlocks.bySpec(definition.spec).nonEmpty), 4,
      "every block of the module, so all of them are in the palette")
    assert(xml.contains("""<variable name="number list">"""), xml)
    assert(!snapXml("forward(10)\n").contains("number list"), "programs without list calls stay unchanged")
  }

  test("reading the blocks back binds them to the module, so the program runs and prints as before") {
    val back = ProgrammingStateRaw(SnapLanguage, snapXml(sorting)).toBeExpression
    val python = back.toPython.code
    assert(python.contains("NumberList.swap(i, i + 1)"), python)
    assert(python.contains("NumberList.set(0, NumberList.get(0))"), python)
    assert(!python.contains("number_list") && !python.contains("def item"), "definitions and the list variable are not program code:\n" + python)
    val run = back.execute(DataWorld(list = Vector(5, 1, 4)), ProgramEntry.Call("sortiere"))
    assertEquals((run.status, run.world.list), (TurtleExecutionStatus.Completed, Vector(1, 4, 5)))
    assertEquals(run.moduleCommands(BeModules.NumberList).head, ListCommand.Length(3))
  }

  test("an imported library without calls adds nothing to the program") {
    val program = snapXml("forward(10)\n")
    val withLibrary = program.replace("<blocks></blocks><primitives>",
      s"<blocks>${SnapModuleBlocks.numberList.map(_.definitionXml).mkString}</blocks><primitives>")
      .replace("</variables></scene>", s"${SnapModuleBlocks.variableXml(SnapModuleBlocks.numberList)}</variables></scene>")
    assertNotEquals(withLibrary, program)
    assertEquals(ProgrammingStateRaw(SnapLanguage, withLibrary).toBeExpression.toPython.code,
      ProgrammingStateRaw(SnapLanguage, program).toBeExpression.toPython.code)
  }

  test("a reporter block cannot stand alone as a statement") {
    val statements = SnapControlFlow.topLevelStatements(ProgrammingStateRaw(Python, "NumberList.length()\nNumberList.swap(0, 1)\n").toBeExpression.expression)
    assertEquals(statements.map(SnapControlFlow.describeUnsupportedStatement).map(_.exists(_.contains("reports a value"))), List(true, false))
  }
}
