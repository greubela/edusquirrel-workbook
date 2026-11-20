package it.evadid.facades

import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

/** Suites exercising CodeMirror components against the installed npm packages, without the CDN loader. */
private[evadid] trait CodeMirrorSuite extends it.evadid.util.JsDomSuite {
  private def packages = CodeMirrorSuite.packages

  /** Publishes the libraries global (merged module exports, as the facade's CDN loader does) for one test. */
  protected def withLibraries[A](body: js.Dynamic => Future[A]): Future[A] = CodeMirrorSuite.loaded(packages).flatMap { libraries =>
    restoringGlobals("EduSquirrelCodeMirrorLibraries") {
      js.Dynamic.global.globalThis.EduSquirrelCodeMirrorLibraries = libraries
      body(libraries)
    }
  }

  protected def host(): dom.Element = {
    val element = dom.document.createElement("div")
    dom.document.body.appendChild(element)
    element
  }

  /** Decorations of a set as (covered text, CSS class) in document order. */
  protected def decorated(set: js.Dynamic, doc: js.Dynamic): List[(String, String)] = {
    val found = js.Array[(String, String)]()
    set.between(0, doc.length, ((from: Int, to: Int, value: js.Dynamic) => {
      found.push(doc.sliceString(from, to).asInstanceOf[String] -> value.spec.selectDynamic("class").asInstanceOf[String])
      ()
    }): js.Function3[Int, Int, js.Dynamic, Unit])
    found.toList
  }
}

private[evadid] object CodeMirrorSuite {
  val packages: List[String] = List("@codemirror/state", "@codemirror/view", "@codemirror/commands", "@codemirror/language",
    "@codemirror/search", "@codemirror/lang-python", "@codemirror/lang-sql", "@codemirror/lang-cpp",
    "@codemirror/lang-java", "@codemirror/theme-one-dark", "@replit/codemirror-indentation-markers")
  private val require = js.Dynamic.global.selectDynamic("require")
  private val cache = com.raquo.airstream.state.Var(Option.empty[js.Dynamic])

  /** The CommonJS builds share one instance per package (the test runner cannot evaluate dynamic imports). */
  def loaded(packages: List[String] = packages): Future[js.Dynamic] = Future.fromTry(scala.util.Try(cache.now().getOrElse {
    val libraries = js.Dynamic.literal()
    packages.foreach { name =>
      val module = require(it.evadid.util.JsDom.root + "/node_modules/" + name)
      js.Object.keys(module.asInstanceOf[js.Object]).foreach(key => libraries.updateDynamic(key)(module.selectDynamic(key)))
    }
    cache.set(Some(libraries))
    libraries
  }))
}
