package it.evadid.vm.projectexport

import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.modules.BeModules
import it.evadid.vm.conversion.dependencies.{JavaTurtleLibrary, KnownDependencies}
import it.evadid.vm.conversion.structured.SourceStructure
import it.evadid.vm.simulation.data.{DataLibrarySources, DataWorld}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateJavaStructured, ProgrammingStateRaw}

import scala.util.control.NonFatal

/**
 * Exports a program as a BlueJ project: one folder with `package.bluej`, `README.TXT` and one
 * `<Class>.java` per class in the default package. Library classes the program calls (`Turtle`, `Web`,
 * `NumberList`, `Sudoku`) are added as their own files unless the program defines a class of that name,
 * so the project compiles and runs outside the workbook.
 */
object JavaProjectExport {
  /** One class file: the class name and its complete source. */
  final case class JavaClassFile(name: String, source: String, library: Boolean)

  def blueJ(state: ProgrammingState, title: String, world: DataWorld = DataWorld()): Either[String, ExportedProject] =
    try {
      val structured = state match {
        case value: ProgrammingStateJavaStructured => value
        case value @ ProgrammingStateRaw(Java, code) => ProgrammingStateJavaStructured(code)
        case other => other.toJavaStructured
      }
      val own = classFiles(structured)
      if own.isEmpty then Left("The program has no Java class to export.")
      else {
        val defined = own.map(_.name).toSet
        val turtleCalls = own.flatMap(file => turtleCall.findAllMatchIn(file.source).map(_.group(1))).toSet
        val libraries = libraryFiles(libraryExpression(state, structured), world, turtleCalls).filterNot(file => defined(file.name))
        val classes = own ++ libraries
        Right(ExportedProject(ExportedProject.folderName(title),
          classes.map(file => ProjectFile(s"${file.name}.java", file.source)) ++
            Seq(ProjectFile("package.bluej", packageFile(classes)), ProjectFile("README.TXT", readme(title, classes)))))
      }
    } catch {
      case NonFatal(error) => Left(Option(error.getMessage).getOrElse(error.getClass.getSimpleName))
    }

  /** The program's classes, each with every import of the program and without `package` declarations. */
  def classFiles(structured: ProgrammingStateJavaStructured): Seq[JavaClassFile] = {
    val sources = structured.definedClasses.values.toSeq
    val split = sources.flatMap { source =>
      SourceStructure.javaClasses(source).headOption.map { block =>
        val preamble = source.substring(0, block.start)
        (block.name, preamble, source.substring(block.start).trim)
      }
    }
    // Line-based: multiline regex flags are unavailable in Scala.js.
    def isImport(line: String) = line.trim.startsWith("import ") && line.trim.endsWith(";")
    def isPackage(line: String) = line.trim.startsWith("package ") && line.trim.endsWith(";")
    val imports = split.flatMap((_, preamble, _) => preamble.linesIterator.filter(isImport).map(_.trim.replaceAll("\\s+", " "))).distinct
    split.map { (name, preamble, body) =>
      val comments = preamble.linesIterator.filterNot(line => isImport(line) || isPackage(line) || line.trim.isEmpty).mkString("\n")
      val header = Seq(imports.mkString("\n"), comments).filter(_.nonEmpty).mkString("\n\n")
      JavaClassFile(name, (if header.isEmpty then body else s"$header\n\n$body") + "\n", library = false)
    }
  }

  /** The shared expression of the program, to find the libraries it calls; none when it cannot be converted. */
  private def libraryExpression(state: ProgrammingState, structured: ProgrammingStateJavaStructured): Option[BeExpression] =
    try Some(structured.toBeExpression.expression)
    catch { case NonFatal(_) => try Some(state.toBeExpression.expression) catch { case NonFatal(_) => None } }

  private val turtleCall = """\bTurtle\s*\.\s*([A-Za-z_]\w*)\s*\(""".r

  /** Library classes for the calls in `expression`, with the list and sudoku filled from `world`; the turtle class
    * also answers to the spellings in `turtleCalls`.
    */
  def libraryFiles(expression: Option[BeExpression], world: DataWorld, turtleCalls: Set[String] = Set.empty): Seq[JavaClassFile] = expression.toSeq.flatMap { program =>
    val dependencies = KnownDependencies.requiredBy(program).flatMap { dependency =>
      if dependency == KnownDependencies.turtle then Some(JavaTurtleLibrary.exportSource(turtleCalls)) else dependency.definition(Java)
    }
    val data = Seq(
      Option.when(program.moduleCalls(BeModules.NumberList).nonEmpty)(DataLibrarySources.javaNumberList(world.list)),
      Option.when(program.moduleCalls(BeModules.Sudoku).nonEmpty)(DataLibrarySources.javaSudoku(world.sudoku))).flatten
    (dependencies ++ data).flatMap { source =>
      SourceStructure.javaClasses(source).headOption.map(block => JavaClassFile(block.name, source.trim + "\n", library = true))
    }
  }

  /** BlueJ's project file: the classes as targets in a grid, the program's own classes first. */
  def packageFile(classes: Seq[JavaClassFile]): String = {
    val targets = classes.zipWithIndex.flatMap { (file, index) =>
      val number = index + 1
      Seq(s"target$number.name=${file.name}", s"target$number.type=ClassTarget", s"target$number.showInterface=false",
        s"target$number.x=${70 + (index % 4) * 150}", s"target$number.y=${30 + (index / 4) * 90}",
        s"target$number.width=120", s"target$number.height=50", s"target$number.naviview.expanded=false")
    }
    (Seq("#BlueJ package file", "package.numDependencies=0", s"package.numTargets=${classes.size}",
      "package.editor.width=900", "package.editor.height=600", "package.editor.x=40", "package.editor.y=40",
      "project.charset=UTF-8", "readme.name=@README", "readme.x=10", "readme.y=10") ++ targets).mkString("", "\n", "\n")
  }

  private def readme(title: String, classes: Seq[JavaClassFile]): String = {
    val libraries = classes.filter(_.library).map(_.name)
    val libraryNote = if libraries.isEmpty then "" else
      s"\nLIBRARIES: ${libraries.mkString(", ")} provide the workbook's ${if libraries.size == 1 then "library" else "libraries"} " +
        "outside the workbook. Their data and behaviour match the exercise.\n"
    s"""------------------------------------------------------------------------
       |This is the project README file.
       |------------------------------------------------------------------------
       |
       |PROJECT TITLE: ${title.trim}
       |PURPOSE OF PROJECT: Exported from the EduSquirrel workbook to continue working in BlueJ.
       |HOW TO START THIS PROJECT: Compile all classes, then call a class's static main method.
       |$libraryNote""".stripMargin
  }
}
