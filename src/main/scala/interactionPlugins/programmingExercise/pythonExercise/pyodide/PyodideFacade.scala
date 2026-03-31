package interactionPlugins.programmingExercise.pythonExercise.pyodide
/*
import scala.scalajs.js
import scala.scalajs.js.`|`
import scala.scalajs.js.annotation.*

object PyodideFacade {

  @js.native
  trait PyProxy extends js.Object

  @js.native
  trait PyodideStdoutOptions extends js.Object

  object PyodideStdoutOptions:
    def batched(handler: js.Function1[String, Unit]): PyodideStdoutOptions =
      js.Dynamic.literal(batched = handler).asInstanceOf[PyodideStdoutOptions]

    def raw(handler: js.Function1[String, Unit]): PyodideStdoutOptions =
      js.Dynamic.literal(raw = handler).asInstanceOf[PyodideStdoutOptions]

    def write(handler: js.Function1[Double, Unit]): PyodideStdoutOptions =
      js.Dynamic.literal(write = handler).asInstanceOf[PyodideStdoutOptions]

  @js.native
  trait PyodideStderrOptions extends js.Object

  object PyodideStderrOptions:
    def batched(handler: js.Function1[String, Unit]): PyodideStderrOptions =
      js.Dynamic.literal(batched = handler).asInstanceOf[PyodideStderrOptions]

    def raw(handler: js.Function1[String, Unit]): PyodideStderrOptions =
      js.Dynamic.literal(raw = handler).asInstanceOf[PyodideStderrOptions]

    def write(handler: js.Function1[Double, Unit]): PyodideStderrOptions =
      js.Dynamic.literal(write = handler).asInstanceOf[PyodideStderrOptions]

  @js.native
  trait PyodideStdinOptions extends js.Object

  object PyodideStdinOptions:
    def apply(
               stdin: js.Function0[String | Null],
               isatty: js.UndefOr[Boolean] = js.undefined,
               autoEOF: js.UndefOr[Boolean] = js.undefined
             ): PyodideStdinOptions =
      js.Dynamic.literal(
        stdin = stdin,
        isatty = isatty,
        autoEOF = autoEOF
      ).asInstanceOf[PyodideStdinOptions]

  @js.native
  trait PyodideLoadPackageOptions extends js.Object

  @js.native
  trait PyodideRunOptions extends js.Object

  object PyodideRunOptions:
    def apply(
               globals: js.UndefOr[PyProxy] = js.undefined,
               locals: js.UndefOr[PyProxy] = js.undefined,
               filename: js.UndefOr[String] = js.undefined
             ): PyodideRunOptions =
      js.Dynamic.literal(
        globals = globals,
        locals = locals,
        filename = filename
      ).asInstanceOf[PyodideRunOptions]

  @js.native
  trait PyodideConfig extends js.Object

  object PyodideConfig:
    def apply(
               indexURL: js.UndefOr[String] = js.undefined,
               packages: js.UndefOr[js.Array[String]] = js.undefined,
               fullStdLib: js.UndefOr[Boolean] = js.undefined,
               stdin: js.UndefOr[js.Function0[String | Null]] = js.undefined,
               stdout: js.UndefOr[js.Function1[String, Unit]] = js.undefined,
               stderr: js.UndefOr[js.Function1[String, Unit]] = js.undefined,
               args: js.UndefOr[js.Array[String]] = js.undefined
             ): PyodideConfig =
      js.Dynamic.literal(
        indexURL = indexURL,
        packages = packages,
        fullStdLib = fullStdLib,
        stdin = stdin,
        stdout = stdout,
        stderr = stderr,
        args = args
      ).asInstanceOf[PyodideConfig]

  @js.native
  trait PyodideInterface extends js.Object:
    val version: String = js.native
    val globals: PyProxy = js.native
    val loadedPackages: js.Dictionary[String] = js.native

    def runPython(code: String, options: js.UndefOr[PyodideRunOptions] = js.undefined): js.Any = js.native

    def runPythonAsync(code: String, options: js.UndefOr[PyodideRunOptions] = js.undefined): js.Promise[js.Any] = js.native

    def loadPackage(
                     names: String | js.Array[String],
                     options: js.UndefOr[PyodideLoadPackageOptions] = js.undefined
                   ): js.Promise[js.Any] = js.native

    def loadPackagesFromImports(
                                 code: String,
                                 options: js.UndefOr[PyodideLoadPackageOptions] = js.undefined
                               ): js.Promise[js.Any] = js.native

    def pyimport(moduleName: String): PyProxy = js.native

    def registerJsModule(name: String, module: js.Object): Unit = js.native

    def unregisterJsModule(name: String): Unit = js.native

    def setStdout(options: PyodideStdoutOptions): Unit = js.native

    def setStderr(options: PyodideStderrOptions): Unit = js.native

    def setStdin(options: PyodideStdinOptions): Unit = js.native

    def toPy(obj: js.Any): PyProxy = js.native

  object PyodideInterface:
    extension (p: PyodideInterface)
      def registerModule(name: String, module: js.Object): Unit =
        p.registerJsModule(name, module)

  @js.native
  @JSImport("pyodide", JSImport.Namespace)
  object Pyodide extends js.Object:
    val version: String = js.native

    def loadPyodide(
                     options: js.UndefOr[PyodideConfig] = js.undefined
                   ): js.Promise[PyodideInterface] = js.native



}*/
