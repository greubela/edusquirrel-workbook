package it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.Promise as JsPromise
import scala.scalajs.js.annotation.JSGlobal
import scala.util.Success

case class TurtleStitchEditor(projektXml: Var[String]) extends HtmlAppElement {

  private var handle: Option[TurtleStitchEditor.JsEditorHandle] = None
  private var updatingFromEditor = false
  private var updatingFromVar = false

  private val domElement: L.Element =
    div(
      cls := "turtle-stitch-editor",
      width := "100%",
      minHeight := "620px",
      height := "75vh",
      onMountCallback { ctx =>
        mount(ctx.thisNode.ref)
          .`then`[Unit]({ jsHandle =>
            jsHandle.setProjectChangeListener((xml: String) => {
              if (!updatingFromVar && projektXml.now() != xml) {
                updatingFromEditor = true
                projektXml.writer.onNext(xml)
                updatingFromEditor = false
              }
            })

            val initialXml = projektXml.now()
            if (initialXml.nonEmpty) {
              updatingFromVar = true
              jsHandle.setProjectXml(initialXml)
                .`then`[Unit]((_: Unit) => {
                  updatingFromVar = false
                  ()
                })
                .`catch`((_: scala.Any) => {
                  updatingFromVar = false
                  ()
                })
            }

            projektXml.signal.foreach { xml =>
              if (!updatingFromEditor && !updatingFromVar) {
                if (xml.nonEmpty) {
                  updatingFromVar = true
                  jsHandle.setProjectXml(xml)
                    .`then`[Unit]((_: Unit) => {
                      updatingFromVar = false
                      ()
                    })
                    .`catch`((_: scala.Any) => {
                      updatingFromVar = false
                      ()
                    })
                }
              }
            }(using ctx.owner)
            ()
          })
          .`catch`((err: scala.Any) => {
            dom.console.error("Failed to create TurtleStitch editor", err.asInstanceOf[js.Any])
            (): Unit
          })
      },
      onUnmountCallback { _ =>
        unmount()
      }
    )

  override def getDomElement(): Element = domElement

  def mount(parentNode: dom.Node): JsPromise[TurtleStitchEditor.JsEditorHandle] = {
    handle match {
      case Some(existing) => JsPromise.resolve(existing)
      case None =>
        TurtleStitchEditor.TurtleStitchPoCNative
          .createEditor(TurtleStitchEditor.editorOptions(hidden = false, parentNode = Some(parentNode)))
          .`then`[TurtleStitchEditor.JsEditorHandle]({ created =>
            handle = Some(created)
            created
          })
    }
  }

  def unmount(): Unit = {
    handle.foreach { current =>
      current.clearProjectChangeListener()
      current.destroy()
    }
    handle = None
  }

  def calcProgramSvg(language: HumanLanguage)(using ec: ExecutionContext): Future[String] =
    withEditorHandle(_.calcProgramSvg(projektXml.now(), TurtleStitchEditor.turtleLang(language)).toFuture)

  def simulateGreenFlag()(using ec: ExecutionContext): Future[String] =
    withEditorHandle(_.simulateGreenFlag(projektXml.now()).toFuture)

  def getGreenFlagAsLispCode()(using ec: ExecutionContext): Future[String] =
    withEditorHandle(_.getGreenFlagAsLispCode(projektXml.now()).toFuture)

  def downloadDst()(using ec: ExecutionContext): Future[Unit] =
    withEditorHandle(_.downloadDst(projektXml.now()).toFuture)

  private def withEditorHandle[T](task: TurtleStitchEditor.JsEditorHandle => Future[T])(using ec: ExecutionContext): Future[T] = {
    handle match {
      case Some(existing) => task(existing)
      case None =>
        TurtleStitchEditor.TurtleStitchPoCNative
          .createEditor(TurtleStitchEditor.editorOptions(hidden = true))
          .toFuture
          .flatMap { temporary =>
            task(temporary).andThen { case _ =>
              try temporary.destroy()
              catch { case _: Throwable => () }
            }
          }
    }
  }
}

object TurtleStitchEditor {
  private var singletonEditor: Option[JsEditorHandle] = None
  private var singletonEditorCreation: Option[Future[JsEditorHandle]] = None
  private var singletonExecutionQueue: Future[Unit] = Future.successful(())

  @js.native
  @JSGlobal("TurtleStitchPoC")
  object TurtleStitchPoCNative extends js.Object {
    def createEditor(options: js.Object): JsPromise[JsEditorHandle] = js.native
  }

  @js.native
  trait JsEditorHandle extends js.Object {
    def calcProgramSvg(xml_content: String, language: String): JsPromise[String] = js.native
    def simulateGreenFlag(xml_content: String): JsPromise[String] = js.native
    def getGreenFlagAsLispCode(xml_content: String): JsPromise[String] = js.native
    def downloadDst(xml_content: String): JsPromise[Unit] = js.native
    def setProjectXml(xml_content: String): JsPromise[Unit] = js.native
    def getProjectXml(): JsPromise[String] = js.native
    def setProjectChangeListener(callback: js.Function1[String, Unit]): Unit = js.native
    def clearProjectChangeListener(): Unit = js.native
    def destroy(): Unit = js.native
  }

  private[turtleStitchPlugin] def editorOptions(
                                                  hidden: Boolean,
                                                  parentNode: Option[dom.Node] = None,
                                                  width: Option[Int] = None,
                                                  height: Option[Int] = None
                                                ): js.Object = {
    val raw = js.Dynamic.literal(hidden = hidden)
    parentNode.foreach(node => raw.updateDynamic("parentNode")(node))
    width.foreach(w => raw.updateDynamic("width")(w))
    height.foreach(h => raw.updateDynamic("height")(h))
    raw.asInstanceOf[js.Object]
  }
  private def getOrCreateSingletonEditor()(using ec: ExecutionContext): Future[JsEditorHandle] = synchronized {
    singletonEditor match {
      case Some(editor) => Future.successful(editor)
      case None =>
        singletonEditorCreation match {
          case Some(inProgress) => inProgress
          case None =>
            val creating = TurtleStitchPoCNative
              .createEditor(editorOptions(hidden = true))
              .toFuture
              .map { editor =>
                TurtleStitchEditor.synchronized {
                  singletonEditor = Some(editor)
                  singletonEditorCreation = None
                }
                editor
              }
              .recoverWith { case err =>
                TurtleStitchEditor.synchronized {
                  singletonEditorCreation = None
                }
                Future.failed(err)
              }
            singletonEditorCreation = Some(creating)
            creating
        }
    }
  }

  def withSingletonEditor[T](task: JsEditorHandle => Future[T])(using ec: ExecutionContext): Future[T] = {
    val result = Promise[T]()
    synchronized {
      singletonExecutionQueue = singletonExecutionQueue
        .recover { case _ => () }
        .flatMap { _ =>
          getOrCreateSingletonEditor()
            .flatMap(task)
            .transform { taskResult =>
              result.tryComplete(taskResult)
              Success(())
            }
        }
    }
    result.future
  }
  
  private def withFreshEditor[T](task: JsEditorHandle => Future[T])(using ec: ExecutionContext): Future[T] = {
    TurtleStitchPoCNative
      .createEditor(editorOptions(hidden = true))
      .toFuture
      .flatMap { editor =>
        task(editor).andThen { case _ =>
          try editor.destroy()
          catch { case _: Throwable => () }
        }
      }
  }
  
  def turtleLang(language: HumanLanguage): String =
    AppLanguage.turtleStitchLangMap.getOrElse(language, "en")


  private val basicCacheLogger: Logger = Logger.withNameAndPrefixes(Some("TurtleStitchEditor::ProgramPngDataSrcStore"), PrintToStdLogger.printWarnAndError)
  private val programOutputDataSrcStorage: AsyncDataCache[String, String] = new AsyncDataCache[String, String](basicCacheLogger) {
    protected def executeLoading(xml: String)(ec: ExecutionContext): Future[String] =
      withSingletonEditor(_.simulateGreenFlag(xml).toFuture)(using ec)

    protected def defaultValueWhileLoading(in: String): Option[String] = None

    protected def formatInputForLogging(in: String): String = {
      if(in.length > 60) s"XmlInput(${in.length}, ${in.substring(0, 60)})"
      else s"XmlInput($in)"
    }

    protected def formatOutputForLogging(out: String): String = {
      if(out.length > 60) s"PngOutput(${out.length}, ${out.substring(0, 60)} ...)"
      else s"PngOutput($out)"
    }
  }

  def downloadDst(xml: String)(using ec: ExecutionContext): Future[Unit] =
    withSingletonEditor(_.downloadDst(xml).toFuture)

  def getGreenFlagAsLispCode(xml: String)(using ec: ExecutionContext): Future[String] =
    withSingletonEditor(_.getGreenFlagAsLispCode(xml).toFuture)
}
