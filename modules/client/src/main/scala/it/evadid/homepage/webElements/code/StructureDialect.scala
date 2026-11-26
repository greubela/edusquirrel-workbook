package it.evadid.homepage.webElements.code

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python}
import it.evadid.vm.conversion.dependencies.KnownDependencies
import it.evadid.vm.conversion.structured.SourceStructure
import it.evadid.vm.conversion.structured.SourceStructure.{Param, Visibility}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateJavaStructured, ProgrammingStatePythonStructured, ProgrammingStateRaw}

import JavaMethodSource.*

/** The language rules of the structure editor: how source is read into the class diagram's model and how
  * the members its dialogs add are written. The editor and the diagram themselves are language neutral.
  */
private[code] trait StructureDialect {
  def language: ProgrammingLanguage
  /** Whether the shared source lexer reads Python. */
  def python: Boolean

  /** The source text of a state in this language. */
  def sourceOf(state: ProgrammingState): Option[String]
  /** Own classes (Python: the module box first) and library classes. */
  def analyse(code: String): (List[JavaClass], List[JavaClass])
  def balanced(code: String): Boolean = SourceStructure.balanced(code, python)
  def entryMethod(code: String, classes: List[JavaClass], balanced: Boolean, requirePublicMain: Boolean): Option[JavaMethod]

  def validName(name: String): Boolean
  def validType(value: String): Boolean
  /** The result type of operations without a result. */
  def noResult: String
  def defaultType: String
  def typeSuggestions(classNames: List[String], withNoResult: Boolean): List[String]
  def visibilities: List[Visibility]
  def visibilityText(visibility: Visibility): String
  /** The name as written in code; Python encodes the visibility in it. */
  def memberName(name: String, visibility: Visibility): String = name
  def bareName(name: String): String = name
  def constructorName(className: String): String

  def addClass(source: String, name: String): String
  def methodDeclaration(clazz: JavaClass, returnType: String, name: String, params: List[Param], isStatic: Boolean,
      visibility: Visibility): String
  def addMethod(source: String, clazz: JavaClass, declaration: String): String
  def duplicateMethod(clazz: JavaClass, name: String, params: List[Param]): Boolean
  def fieldDeclaration(visibility: Visibility, isStatic: Boolean, fieldType: String, name: String, initializer: String): String
  def saveField(source: String, clazz: JavaClass, edited: Option[JavaField], isStatic: Boolean, declaration: String): String
  /** The parameters a new constructor stores in attributes of the same name. */
  def assignedParameters(clazz: JavaClass, params: List[Param], assign: Boolean): List[String]
  def constructorDeclaration(clazz: JavaClass, visibility: Visibility, params: List[Param], assigned: List[String]): String
  def duplicateConstructor(clazz: JavaClass, params: List[Param]): Boolean
  def addConstructor(source: String, clazz: JavaClass, declaration: String): String
  def removeMember(source: String, start: Int, end: Int): String
  /** The declaration without its body, for the dialog preview. */
  def head(declaration: String): String
}

private[code] object StructureDialect {

  object JavaDialect extends StructureDialect {
    override val language: ProgrammingLanguage = Java
    override val python = false

    override def sourceOf(state: ProgrammingState): Option[String] = state match {
      case ProgrammingStateRaw(Java, code) => Some(code)
      case value: ProgrammingStateJavaStructured => Some(value.toString)
      case _ => None
    }

    override def analyse(code: String): (List[JavaClass], List[JavaClass]) = {
      val outlines = SourceStructure.javaClassOutlines(code).toList
      val libraryRanges = KnownDependencies.definitionRanges(code, Java)
      val (libraries, own) = outlines.partition(insideAny(libraryRanges, _))
      (own.map(javaClass(_, library = false)), libraries.map(javaClass(_, library = true)))
    }

    override def entryMethod(code: String, classes: List[JavaClass], balanced: Boolean, requirePublicMain: Boolean): Option[JavaMethod] =
      javaEntryMethod(code, classes, balanced, requirePublicMain)

    override def validName(name: String): Boolean = JavaMethodSource.validName(name)
    override def validType(value: String): Boolean = JavaMethodSource.validType(value)
    override val noResult = "void"
    override val defaultType = "int"
    override def typeSuggestions(classNames: List[String], withNoResult: Boolean): List[String] =
      ((if withNoResult then List("void") else Nil) ++ List("int", "double", "boolean", "char", "long", "String") ++
        classNames ++ List("int[]", "String[]", "List<String>", "List<Integer>")).distinct
    override val visibilities: List[Visibility] = Visibility.values.toList
    override def visibilityText(visibility: Visibility): String = s"${visibility.symbol} ${javaModifier(visibility).getOrElse("package")}"
    override def constructorName(className: String): String = className

    override def addClass(source: String, name: String): String = {
      val newline = lineSeparator(source)
      val separator = if source.isEmpty then "" else newline + newline
      source + separator + s"class $name {$newline}$newline"
    }
    override def methodDeclaration(clazz: JavaClass, returnType: String, name: String, params: List[Param], isStatic: Boolean,
        visibility: Visibility): String =
      JavaMethodSource.methodDeclaration(returnType, name, javaParameters(params), isStatic, visibility)
    override def addMethod(source: String, clazz: JavaClass, declaration: String): String = insertMethod(source, clazz, declaration)
    override def duplicateMethod(clazz: JavaClass, name: String, params: List[Param]): Boolean =
      clazz.methods.exists(method => method.name == name && method.params.map(_.javaType) == params.map(_.javaType))
    override def fieldDeclaration(visibility: Visibility, isStatic: Boolean, fieldType: String, name: String, initializer: String): String =
      JavaMethodSource.fieldDeclaration(visibility, isStatic, fieldType, name, initializer)
    override def saveField(source: String, clazz: JavaClass, edited: Option[JavaField], isStatic: Boolean, declaration: String): String =
      edited.fold(insertField(source, clazz, declaration))(field => replaceRange(source, field.start, field.end, declaration))
    override def assignedParameters(clazz: JavaClass, params: List[Param], assign: Boolean): List[String] = {
      val fieldNames = clazz.fields.filterNot(_.isStatic).map(_.name).toSet
      if assign then params.map(_.name).filter(fieldNames) else Nil
    }
    override def constructorDeclaration(clazz: JavaClass, visibility: Visibility, params: List[Param], assigned: List[String]): String =
      JavaMethodSource.constructorDeclaration(clazz.name, visibility, params, assigned)
    override def duplicateConstructor(clazz: JavaClass, params: List[Param]): Boolean =
      clazz.methods.exists(method => method.isConstructor && method.params.map(_.javaType) == params.map(_.javaType))
    override def addConstructor(source: String, clazz: JavaClass, declaration: String): String = insertConstructor(source, clazz, declaration)
    override def removeMember(source: String, start: Int, end: Int): String = JavaMethodSource.removeMember(source, start, end)
    override def head(declaration: String): String = declaration.takeWhile(_ != '{').trim
  }

  object PythonDialect extends StructureDialect {
    override val language: ProgrammingLanguage = Python
    override val python = true

    override def sourceOf(state: ProgrammingState): Option[String] = state match {
      case ProgrammingStateRaw(Python, code) => Some(code)
      case value: ProgrammingStatePythonStructured => Some(value.toString)
      case _ => None
    }

    override def analyse(code: String): (List[JavaClass], List[JavaClass]) = PythonStructure.analyse(code)
    override def entryMethod(code: String, classes: List[JavaClass], balanced: Boolean, requirePublicMain: Boolean): Option[JavaMethod] =
      PythonStructure.entryMethod(classes, balanced)

    override def validName(name: String): Boolean = PythonStructure.validName(name)
    override def validType(value: String): Boolean = PythonStructure.validType(value)
    override val noResult = "None"
    override val defaultType = "float"
    override def typeSuggestions(classNames: List[String], withNoResult: Boolean): List[String] =
      ((if withNoResult then List("None") else Nil) ++ List("float", "int", "bool", "str") ++ classNames ++
        List("list[float]", "list[str]", "dict[str, float]")).distinct
    override val visibilities: List[Visibility] = List(Visibility.Public, Visibility.Protected, Visibility.Private)
    override def visibilityText(visibility: Visibility): String = visibility match {
      case Visibility.Private => s"${visibility.symbol} __name"
      case Visibility.Protected => s"${visibility.symbol} _name"
      case _ => s"${visibility.symbol} name"
    }
    override def memberName(name: String, visibility: Visibility): String = PythonStructure.memberName(name, visibility)
    override def bareName(name: String): String = PythonStructure.bareName(name)
    override def constructorName(className: String): String = "__init__"

    override def addClass(source: String, name: String): String = PythonStructure.addClass(source, name)
    override def methodDeclaration(clazz: JavaClass, returnType: String, name: String, params: List[Param], isStatic: Boolean,
        visibility: Visibility): String =
      PythonStructure.methodDeclaration(!clazz.isModule, returnType, name, params, isStatic && !clazz.isModule)
    override def addMethod(source: String, clazz: JavaClass, declaration: String): String = PythonStructure.addMethod(source, clazz, declaration)
    /** Python has no overloading: a second definition replaces the first. */
    override def duplicateMethod(clazz: JavaClass, name: String, params: List[Param]): Boolean = clazz.methods.exists(_.name == name)
    override def fieldDeclaration(visibility: Visibility, isStatic: Boolean, fieldType: String, name: String, initializer: String): String =
      PythonStructure.fieldDeclaration(isStatic, fieldType, name, initializer)
    override def saveField(source: String, clazz: JavaClass, edited: Option[JavaField], isStatic: Boolean, declaration: String): String =
      PythonStructure.saveField(source, clazz, edited, isStatic, declaration)
    /** Python declares instance attributes by assigning them, so every parameter becomes one. */
    override def assignedParameters(clazz: JavaClass, params: List[Param], assign: Boolean): List[String] =
      if assign then params.map(_.name) else Nil
    override def constructorDeclaration(clazz: JavaClass, visibility: Visibility, params: List[Param], assigned: List[String]): String =
      PythonStructure.constructorDeclaration(params, assigned)
    override def duplicateConstructor(clazz: JavaClass, params: List[Param]): Boolean = clazz.methods.exists(_.isConstructor)
    override def addConstructor(source: String, clazz: JavaClass, declaration: String): String =
      PythonStructure.addConstructor(source, clazz, declaration)
    override def removeMember(source: String, start: Int, end: Int): String = PythonStructure.removeMember(source, start, end)
    override def head(declaration: String): String = PythonStructure.head(declaration)
  }
}
