package it.evadid.util

import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.file.{CopyrightInfo, FileDescription, LoadedFile}
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import org.scalajs.dom
import java.io.IOException
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

/** A browser file reader whose read fails, as for a file removed or locked after selection. */
private class UnreadableFileReader extends js.Object {
  var onerror: js.Function1[js.Any, Unit] = _ => ()
  var onload: js.Any = js.undefined
  def readAsArrayBuffer(file: js.Any): Unit = js.timers.setTimeout(0)(onerror("NotReadableError"))
}

class FetchFromRemoteSpec extends JsDomSuite {
  // Uploaded files are jsdom Files read by jsdom's FileReader; network responses use Node's fetch types.
  override protected def domGlobals: List[String] = List("window", "document", "Blob", "File", "FileReader", "Event")

  private val logger = Logger.withNameAndPrefixes(Some("fetch-test"), PrintToStdLogger.printNothing)
  private def downloader = FetchFromRemote(logger, ExecutionContext.global)

  /** Network boundary: records requested URLs and answers each with the given response. */
  private def withFetch[A](answer: String => js.Promise[dom.Response])(body: Var[List[String]] => Future[A]): Future[A] = {
    val requests = Var(List.empty[String])
    withGlobal("fetch", ((url: String) => { requests.update(_ :+ url); answer(url) }): js.Function1[String, js.Promise[dom.Response]]) {
      body(requests)
    }
  }

  private def bytes(values: Int*): dom.Response =
    js.Dynamic.newInstance(js.Dynamic.global.Response)(new Uint8Array(js.Array(values.map(_.toShort)*))).asInstanceOf[dom.Response]

  test("URL resources are fetched once, cached and reloaded on request") {
    withFetch(_ => js.Promise.resolve[dom.Response](bytes(1, 2, 255))) { requests =>
      val fetcher = downloader
      val description = FileFactory(fetcher).fromUrl(new dom.URL("https://example.org/img/logo.png"))
      for {
        first <- description.loadData()
        second <- description.loadData()
        reloaded <- fetcher.fetch(description, forceReload = true)
      } yield {
        assertEquals(first.data.toList, List[Byte](1, 2, -1))
        assertEquals(second.description, description)
        assertEquals(reloaded.data.toList, first.data.toList)
        assertEquals(requests.now(), List("https://example.org/img/logo.png", "https://example.org/img/logo.png"))
      }
    }
  }

  test("HTTP error statuses fail with the URL and status") {
    withFetch(_ => js.Promise.resolve[dom.Response](js.Dynamic.newInstance(js.Dynamic.global.Response)("missing",
      js.Dynamic.literal(status = 404)).asInstanceOf[dom.Response])) { _ =>
      downloader.executeFetch("https://example.org/missing.png").failed.map { error =>
        assert(error.isInstanceOf[IOException])
        assert(error.getMessage.contains("https://example.org/missing.png"))
        assert(error.getMessage.contains("status 404"))
      }
    }
  }

  test("network failures and interrupted bodies become IO errors") {
    val broken = js.Dynamic.literal(ok = true, status = 200,
      arrayBuffer = (() => js.Promise.reject("stream reset")): js.Function0[js.Promise[js.Any]])
    withFetch(url =>
      if url.endsWith("offline") then js.Promise.reject("network down").asInstanceOf[js.Promise[dom.Response]]
      else js.Promise.resolve[dom.Response](broken.asInstanceOf[dom.Response])) { _ =>
      val fetcher = downloader
      fetcher.executeFetch("https://example.org/offline").failed
        .zip(fetcher.executeFetch("https://example.org/broken").failed).map { (offline, interrupted) =>
          assert(offline.getMessage.startsWith("Unknown Error while fetching 'https://example.org/offline'"))
          assert(offline.getMessage.contains("network down"))
          assert(interrupted.getMessage.startsWith("Error loading buffer after fetching 'https://example.org/broken'"))
          assert(interrupted.getMessage.contains("stream reset"))
        }
    }
  }

  test("uploaded files are read through the browser file reader") {
    val file = js.Dynamic.newInstance(window.File)(js.Array("Grüße"), "notes.txt").asInstanceOf[dom.File]
    val description = FileFactory(downloader).fromFile(file)
    assertEquals(description.asUrlString, "notes.txt")
    assertEquals(description.toString, "UploadedResourceFileDescription(notes.txt)")
    assertEquals(description.getChildrenFile("child"), None)
    description.loadData().map { loaded =>
      assertEquals(new String(loaded.data, "UTF-8"), "Grüße")
      assertEquals(loaded.description, description)
    }
  }

  test("unreadable uploads fail with the file name") {
    val file = js.Dynamic.newInstance(window.File)(js.Array("x"), "locked.bin").asInstanceOf[dom.File]
    withGlobal("FileReader", js.constructorOf[UnreadableFileReader]) {
      downloader.executeFetch(file).failed.map(error =>
        assertEquals(error.getMessage, "Could not read 'locked.bin': NotReadableError"))
    }
  }

  test("URL descriptions expose their address and only directories have children") {
    val factory = FileFactory(downloader)
    val directory = factory.fromUrl(new dom.URL("https://example.org/assets"))
    val file = factory.fromUrl(new dom.URL("https://example.org/assets/logo.png"), CopyrightInfo.unknownCopyrightInfo)
    assertEquals(directory.toString, "InternetResourceFileDescription(https://example.org/assets)")
    assertEquals(directory.getChildrenFile("logo.png").map(_.asUrlString), Some("https://example.org/assets/logo.png"))
    assertEquals(file.getChildrenFile("child"), None)
    assertEquals(file.asUrlString, "https://example.org/assets/logo.png")
    assertEquals(FileFactory.singleton.fromUrl(new dom.URL("https://example.org/a")).asUrlString, "https://example.org/a")
  }

  test("other file descriptions load through their own loader and share the cache") {
    val loads = Var(0)
    val fetcher = downloader
    val inline = new FileDescription {
      override def copyrightInfo = CopyrightInfo.unknownCopyrightInfo
      override def asUrlString = "inline.txt"
      override def loadData(): Future[LoadedFile] = { loads.update(_ + 1); Future.successful(LoadedFile(this, Array[Byte](7))) }
      override def getChildrenFile(childName: String, cCopyrightInfo: CopyrightInfo) = None
    }
    fetcher.fetch(inline).flatMap(first => fetcher.fetch(inline).map(first -> _)).map { (first, second) =>
      assertEquals(first.data.toList, List[Byte](7))
      assertEquals(second.data.toList, List[Byte](7))
      assertEquals(loads.now(), 1)
    }
  }
}
