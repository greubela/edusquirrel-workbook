package interactionPlugins.turtleStitchPlugin

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import datastructures.core.language.{AppLanguage, HumanLanguage, TranslationMaps}
import datastructures.web.storage.AsyncDataCache
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
            }(ctx.owner)
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
        val options = js.Dynamic.literal(
          parentNode = parentNode,
          width = 1440,
          height = 1000,
          hidden = false
        )

        TurtleStitchEditor.TurtleStitchPoCNative
          .createEditor(options.asInstanceOf[js.Object])
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

  def downloadDst()(using ec: ExecutionContext): Future[Unit] =
    withEditorHandle(_.downloadDst(projektXml.now()).toFuture)

  private def withEditorHandle[T](task: TurtleStitchEditor.JsEditorHandle => Future[T])(using ec: ExecutionContext): Future[T] = {
    handle match {
      case Some(existing) => task(existing)
      case None =>
        TurtleStitchEditor.TurtleStitchPoCNative
          .createEditor(js.Dynamic.literal(hidden = true).asInstanceOf[js.Object])
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
    def downloadDst(xml_content: String): JsPromise[Unit] = js.native
    def setProjectXml(xml_content: String): JsPromise[Unit] = js.native
    def getProjectXml(): JsPromise[String] = js.native
    def setProjectChangeListener(callback: js.Function1[String, Unit]): Unit = js.native
    def clearProjectChangeListener(): Unit = js.native
    def destroy(): Unit = js.native
  }
  private def getOrCreateSingletonEditor()(using ec: ExecutionContext): Future[JsEditorHandle] = synchronized {
    singletonEditor match {
      case Some(editor) => Future.successful(editor)
      case None =>
        singletonEditorCreation match {
          case Some(inProgress) => inProgress
          case None =>
            val creating = TurtleStitchPoCNative
              .createEditor(js.Dynamic.literal(hidden = true).asInstanceOf[js.Object])
              .toFuture
              .map { editor =>
                synchronized {
                  singletonEditor = Some(editor)
                  singletonEditorCreation = None
                }
                editor
              }
              .recoverWith { case err =>
                synchronized {
                  singletonEditorCreation = None
                }
                Future.failed(err)
              }
            singletonEditorCreation = Some(creating)
            creating
        }
    }
  }

  private def withSingletonEditor[T](task: JsEditorHandle => Future[T])(using ec: ExecutionContext): Future[T] = {
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
      .createEditor(js.Dynamic.literal(hidden = true).asInstanceOf[js.Object])
      .toFuture
      .flatMap { editor =>
        task(editor).andThen { case _ =>
          try editor.destroy()
          catch { case _: Throwable => () }
        }
      }
  }

  private[turtleStitchPlugin] def turtleLang(language: HumanLanguage): String =
    AppLanguage.turtleStitchLangMap.getOrElse(language, "en")

  val programSvgDataSrcStorage: AsyncDataCache[(String, HumanLanguage), String] = new AsyncDataCache[(String, HumanLanguage), String]("ProgramSvgDataSrc", false) {
    protected def executeLoading(in: (String, HumanLanguage))(ec: ExecutionContext): Future[String] = {
      val (xml, language) = in
      withSingletonEditor(_.calcProgramSvg(xml, turtleLang(language)).toFuture)(using ec)
    }

    protected def defaultValueWhileLoading(in: (String, HumanLanguage)): Option[String] =
      Some(TranslationMaps.languageMapImageLoading.getInLanguage(in._2))

    protected def formatInputForLogging(in: (String, HumanLanguage)): String =
      s"XmlInput(${in._1.length}, ${in._1.substring(0, 60)}, ${turtleLang(in._2)})"

    protected def formatOutputForLogging(out: String): String =
      s"SvgOutput(${out.length}, ${out.substring(0, 60)} ...)"
  }

  val programOutputDataSrcStorage: AsyncDataCache[String, String] = new AsyncDataCache[String, String]("ProgramPngDataSrc", false) {
    protected def executeLoading(xml: String)(ec: ExecutionContext): Future[String] =
      withSingletonEditor(_.simulateGreenFlag(xml).toFuture)(using ec)

    protected def defaultValueWhileLoading(in: String): Option[String] = None

    protected def formatInputForLogging(in: String): String =
      s"XmlInput(${in.length}, ${in.substring(0, 60)})"

    protected def formatOutputForLogging(out: String): String =
      s"PngOutput(${out.length}, ${out.substring(0, 60)} ...)"
  }

  def downloadDst(xml: String)(using ec: ExecutionContext): Future[Unit] =
    withSingletonEditor(_.downloadDst(xml).toFuture)
}
