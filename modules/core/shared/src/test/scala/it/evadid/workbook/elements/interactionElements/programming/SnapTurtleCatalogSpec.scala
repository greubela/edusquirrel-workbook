package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, KnownBeDefineStructures}
import it.evadid.vm.naming.NamingStyle
import it.evadid.workbook.elements.interactionElements.programming.SnapTurtleCatalog.SnapInputKind
import munit.FunSuite

class SnapTurtleCatalogSpec extends FunSuite {

  test("selectors, canonical python names, and aliases are unique") {
    val selectors = SnapTurtleCatalog.Primitives.map(_.snapSelector)
    assertEquals(selectors.distinct, selectors)
    val canonical = SnapTurtleCatalog.Primitives.map(_.pythonName)
    assertEquals(canonical.distinct, canonical)
    val allNames = SnapTurtleCatalog.Primitives.flatMap(_.allPythonNames)
    assertEquals(allNames.distinct, allNames)
  }

  test("canonical snap and python mappings are reversible") {
    SnapTurtleCatalog.Primitives.foreach { primitive =>
      assertEquals(SnapTurtleCatalog.pythonNameBySnapSelector(primitive.snapSelector), primitive.pythonName)
      assertEquals(SnapTurtleCatalog.snapSelectorByPythonName(primitive.pythonName), primitive.snapSelector)
      assertEquals(SnapTurtleCatalog.canonicalPythonName(primitive.snapSelector), primitive.pythonName)
      primitive.aliases.foreach { alias =>
        assertEquals(SnapTurtleCatalog.snapSelectorByPythonName(alias), primitive.snapSelector)
        assertEquals(SnapTurtleCatalog.primitiveByPythonName(alias), primitive)
      }
    }
  }

  test("extra-primitive metadata is present exactly when extraPrimitive is set") {
    SnapTurtleCatalog.Primitives.foreach { primitive =>
      assertEquals(primitive.extraSpec.isDefined, primitive.extraPrimitive, clue = primitive.snapSelector)
      primitive.extraSpec.foreach { spec =>
        assertEquals(spec.defaults.size, primitive.arity, clue = primitive.snapSelector)
        assert(spec.spec.nonEmpty, clue = primitive.snapSelector)
      }
    }
    assertEquals(
      SnapTurtleCatalog.ExtraPrimitives.map(_.snapSelector).toSet,
      SnapTurtleCatalog.Primitives.filter(_.extraPrimitive).map(_.snapSelector).toSet
    )
  }

  test("input kinds are complete and distinguish color from strings") {
    assertEquals(SnapTurtleCatalog.primitiveBySnapSelector("setColor").inputKinds, List(SnapInputKind.Color))
    assertEquals(SnapTurtleCatalog.primitiveBySnapSelector("gotoXY").arity, 2)
    assertEquals(SnapTurtleCatalog.primitiveBySnapSelector("forward").arity, 1)
    assertEquals(SnapTurtleCatalog.primitiveBySnapSelector("receiveGo").arity, 0)
    assertEquals(SnapTurtleCatalog.primitiveBySnapSelector("jumpStitch").inputKinds, List(SnapInputKind.Bool))
    SnapTurtleCatalog.Primitives.foreach { primitive =>
      assertEquals(primitive.inputKinds.size, primitive.arity, clue = primitive.snapSelector)
    }
  }

  test("catalog turtle arities match KnownBeDefineStructures methods") {
    val turtleClass = KnownBeDefineStructures.byName("turtle").collectFirst { case cls: BeDefineClass => cls }.get
    turtleClass.methods.foreach { method =>
      val name = method.functionTypeInfo.displayName.getNameIn(English, NamingStyle.SnakeCase)
      SnapTurtleCatalog.primitiveByPythonName.get(name).foreach { primitive =>
        assertEquals(primitive.arity, method.inputs.size, clue = name)
      }
    }
  }

  test("KnownBeDefineStructures does not need a workbook catalog dependency for overlapping names") {
    val catalogTurtleNames = SnapTurtleCatalog.Primitives.flatMap(_.allPythonNames).toSet
    val knownNames = KnownBeDefineStructures.byName("turtle").collectFirst { case cls: BeDefineClass => cls }.get
      .methods.map(_.functionTypeInfo.displayName.getNameIn(English, NamingStyle.SnakeCase)).toSet
    assert(catalogTurtleNames.contains("color"))
    assert(knownNames.contains("color"))
    assert(catalogTurtleNames.contains("pensize"))
    assert(knownNames.contains("pensize"))
    assert(catalogTurtleNames.contains("set_x"))
    assert(!knownNames.contains("set_x"))
  }
}
