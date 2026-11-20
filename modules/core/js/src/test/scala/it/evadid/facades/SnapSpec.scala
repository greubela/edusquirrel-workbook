package it.evadid.facades

import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.facades.BrowserScripts.*
import it.evadid.util.JsDomSuite
import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

/** Records constructor arguments of the Morphic classes the facade instantiates. */
private class FakeMorph(val args: js.Any*) extends js.Object

/** The project loader of Snap's serializer, recording the application name it receives. */
private class FakeSnapSerializer extends js.Object {
  val app: String = "Snap! 10, https://snap.berkeley.edu"
  def loadProjectModel(xml: js.Dynamic, ide: js.Any, remixId: js.Any): js.Any = {
    val app: js.Any = if xml == null then "no project"
      else if js.isUndefined(xml.attributes) then "no attributes" else xml.attributes.app
    SnapSpec.loadedProjects.push(js.Array(this, app, ide, remixId))
    "model"
  }
}

/** An IDE whose serializer reports the translator language active while serializing. */
private class FakeIde(val config: js.Any) extends js.Object {
  var loadNewProject: Boolean = true
  val reflected: js.Array[js.Any] = js.Array()
  val serializer: js.Dynamic = js.Dynamic.literal(serialize = ((value: js.Any, library: js.UndefOr[Boolean]) =>
    s"$value/$library/${js.Dynamic.global.SnapTranslator.language}"): js.Function2[js.Any, js.UndefOr[Boolean], String])
  def reflectLanguage(language: String, callback: js.Function0[Unit], noSave: Boolean): Unit = {
    reflected.push(js.Array(language, noSave)); callback()
  }
}

/** Snap's sprite class; rebuilding the block palette is observable through the counter. */
private class FakeSprite extends js.Object {
  def initBlocks(): Unit = SnapSpec.initializedBlocks.update(_ + 1)
}

private object SnapSpec {
  val loadedProjects: js.Array[js.Array[js.Any]] = js.Array()
  val initializedBlocks: Var[Int] = Var(0)
}

class SnapSpec extends JsDomSuite {
  private val runtimeGlobals = List("SnapSerializer", "SnapTranslator", "SpriteMorph", "base_prog_dir", "WorldMorph",
    "IDE_Morph", "PushButtonMorph", "DialogBoxMorph", "MenuMorph")
  private val previousRuntime = Var(List.empty[(String, js.Any)])
  private val globals = js.Dynamic.global.globalThis
  private val base = "http://localhost/resources/programs/20260212TurtleStitch/"
  private def initializedBlocks = SnapSpec.initializedBlocks

  override def beforeAll(): Unit = {
    super.beforeAll()
    previousRuntime.set(runtimeGlobals.map(name => name -> js.Dynamic.global.Object.getOwnPropertyDescriptor(globals, name)))
  }
  override def afterAll(): Unit = {
    previousRuntime.now().foreach { (name, descriptor) =>
      if js.isUndefined(descriptor) then js.special.delete(globals, name)
      else js.Dynamic.global.Object.defineProperty(globals, name, descriptor)
    }
    super.afterAll()
  }

  /** The bundled runtime: each script defines the globals it provides; the language files add dictionaries. */
  private def runtime(failing: String => Boolean)(src: String): Outcome =
    if failing(src) then Fail
    else Load { () =>
      val path = src.stripPrefix(base)
      path match {
        case "adjusted/adjustedMorphic.js" =>
          List("WorldMorph", "PushButtonMorph", "DialogBoxMorph", "MenuMorph").foreach(name =>
            globals.updateDynamic(name)(js.constructorOf[FakeMorph]))
        case "adjusted/adjustedObjects.js" => globals.SpriteMorph = js.constructorOf[FakeSprite]
        case "turtlestitchsrc/gui.js" => globals.IDE_Morph = js.constructorOf[FakeIde]
        case "turtlestitchsrc/store.js" => globals.SnapSerializer = js.constructorOf[FakeSnapSerializer]
        case "turtlestitchsrc/locale.js" =>
          globals.SnapTranslator = js.Dynamic.literal(language = "en", dict = js.Dynamic.literal(en = js.Dynamic.literal()))
        case language if language.startsWith("adjusted/lang-") =>
          globals.SnapTranslator.dict.updateDynamic(language.stripPrefix("adjusted/lang-").stripSuffix(".js"))(js.Dynamic.literal())
        case _ => ()
      }
    }

  test("a failed runtime script stops loading, and a retry continues with the remaining scripts in order") {
    val failedOnce = Var(false)
    val unreachableOnce: String => Boolean = src =>
      src.endsWith("/xml.js") && !failedOnce.now() && { failedOnce.set(true); true }
    BrowserScripts.using(runtime(unreachableOnce)) { scripts =>
      Snap.load().failed.flatMap { error =>
        assert(error.getMessage.contains("turtlestitchsrc/xml.js"))
        assertEquals(scripts.scripts.last, base + "turtlestitchsrc/xml.js")
        assertEquals(scripts.scripts.head, base + "adjusted/adjustedMorphic.js")
        assertEquals(globals.base_prog_dir.asInstanceOf[String], base)
        assert(intercept[IllegalArgumentException](Snap.world(dom.document.createElement("canvas")
          .asInstanceOf[dom.HTMLCanvasElement]).createLoadedInstance()).getMessage.contains("not been preloaded"))
        tick().flatMap(_ => Snap.preload()).map(_ => scripts.scripts)
      }.map { inserted =>
        assertEquals(inserted.count(_ == base + "adjusted/adjustedMorphic.js"), 1, "loaded scripts are reused")
        assertEquals(inserted.count(_ == base + "turtlestitchsrc/xml.js"), 2)
        assertEquals(inserted.last, base + "turtlestitchsrc/embroider.js")
        assertEquals(inserted.size, 23)
        assert(Snap.load() eq Snap.load(), "a successful runtime load is shared")
      }
    }
  }

  test("project loading adopts the current application name only for projects from other applications") {
    val serializer = js.Dynamic.newInstance(globals.SnapSerializer)()
    def load(xml: js.Any): js.Any = {
      serializer.loadProjectModel(xml, "ide", "remix")
      SnapSpec.loadedProjects.pop()(1)
    }
    def project(app: js.Any) = js.Dynamic.literal(attributes = js.Dynamic.literal(app = app))
    assertEquals(load(project("TurtleStitch 2.7")), serializer.app: js.Any)
    assertEquals(load(project("Snap! 9.0")), "Snap! 9.0": js.Any)
    assertEquals(load(project(" anonymous")), " anonymous": js.Any)
    assertEquals(load(project(7)), 7: js.Any)
    assert(js.isUndefined(load(js.Dynamic.literal(attributes = js.Dynamic.literal()))))
    serializer.loadProjectModel(js.Dynamic.literal(), "ide", "remix")
    val recorded = SnapSpec.loadedProjects.pop()
    assert(recorded(0) eq serializer)
    assertEquals(recorded.toList.drop(2), List[js.Any]("ide", "remix"))
    assertEquals(load(null), "no project": js.Any)
    val patched = globals.SnapSerializer.prototype.loadProjectModel
    Snap.patchSerializer(globals.SnapSerializer.prototype)
    assert(globals.SnapSerializer.prototype.loadProjectModel eq patched, "patching is idempotent")
    assert(intercept[IllegalArgumentException](Snap.patchSerializer(js.Dynamic.literal()))
      .getMessage.contains("loadProjectModel is unavailable"))
  }

  test("Morphic widgets are created through the loaded runtime") {
    val canvas = dom.document.createElement("canvas").asInstanceOf[dom.HTMLCanvasElement]
    val action: js.Function0[Unit] = () => ()
    val owner = js.Dynamic.literal(name = "owner")
    for {
      world <- Snap.world(canvas).createInstance()
      button <- Snap.button(action, "OK").createInstance()
      dialog <- Snap.dialog("accept").createInstance()
      menu <- Snap.menu("pick", owner).createInstance()
    } yield {
      def args(morph: js.Any) = morph.asInstanceOf[FakeMorph].args.toList
      assertEquals(args(world), List[js.Any](canvas, false))
      assertEquals(args(button), List[js.Any](null, action, "OK"))
      assertEquals(args(dialog), List[js.Any](null, "accept", null))
      assertEquals(args(menu), List[js.Any]("pick", null, owner))
    }
  }

  test("editor serialization always uses English block names and restores the active language") {
    Snap.editor(js.Dynamic.literal(noSprites = true)).createInstance().map { ide =>
      val serializer = ide.serializer
      val fake = ide.asInstanceOf[FakeIde]
      assertEquals(fake.config.asInstanceOf[js.Dynamic].noSprites.asInstanceOf[Boolean], true)
      globals.SnapTranslator.language = "de"
      assertEquals(serializer.serialize("project", true).asInstanceOf[String], "project/true/en")
      assertEquals(globals.SnapTranslator.language.asInstanceOf[String], "de")
      val guarded = serializer.serialize
      Snap.protectSerialization(serializer)
      assert(serializer.serialize eq guarded, "the guard is installed once")
      val failing = js.Dynamic.literal(serialize = ((_: js.Any) => throw js.JavaScriptException("broken")): js.Function1[js.Any, String])
      Snap.protectSerialization(failing)
      intercept[js.JavaScriptException](failing.serialize("x"))
      assertEquals(globals.SnapTranslator.language.asInstanceOf[String], "de", "the language is restored after failures")
      globals.SnapTranslator.language = "en"
    }
  }

  test("language dictionaries are preloaded once and switched without rebuilding unchanged languages") {
    BrowserScripts.using(runtime(_ => false)) { scripts =>
      Snap.preloadLanguage(English).flatMap { _ =>
        assertEquals(scripts.scripts, Vector.empty)
        Snap.preloadLanguage(German)
      }.map { _ =>
        assertEquals(scripts.scripts, Vector(base + "adjusted/lang-de.js"))
        val before = initializedBlocks.now()
        Snap.useLanguage(German)
        Snap.useLanguage(German)
        assertEquals(globals.SnapTranslator.language.asInstanceOf[String], "de")
        assertEquals(initializedBlocks.now(), before + 1)
        assert(intercept[IllegalArgumentException](Snap.useLanguage(French)).getMessage.contains("'fr' has not been loaded"))
        Snap.useLanguage(English)
        assertEquals(initializedBlocks.now(), before + 2)
      }
    }
  }

  test("IDE language changes reflect the bundled locale without reopening the project") {
    val ide = new FakeIde(js.undefined)
    val done = Var(false)
    Snap.changeLanguage(ide.asInstanceOf[Snap.IDEMorph], Ukrainian, () => done.set(true))
    assertEquals(ide.loadNewProject, false)
    assertEquals(ide.reflected.toList.map(_.asInstanceOf[js.Array[js.Any]].toList), List(List[js.Any]("ua", true)))
    assert(done.now())
    assertEquals(List(English, German, French, Ukrainian, Russian, Turkish, Danish, Spanish).map(Snap.languageCode),
      List("en", "de", "fr", "ua", "ru", "tr", "dk", "es"))
    Future.unit
  }
}
