package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.geometry.{Bounds, Point}
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.homepage.HtmlAppElement
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.BeProgram
import todomove.datastructures.core.vm.code.BeExpression
import todomove.datastructures.core.vm.code.controlStructures.{BeIfElse, BeSequence, BeSequenceInfo, BeWhile}
import todomove.datastructures.core.vm.code.usage.BeUseValue
import todomove.datastructures.core.vm.types.{BeDataType, BeDataValueLiteral}
import todomove.webElementsOld.webElements.svg.shapes.TextShape
import todomove.webElementsOld.webElements.svg.shapes.composite.ShapeAroundShape
import todomove.webElementsOld.webElements.svg.shapes.datatypes.RectangleShape

import scala.collection.mutable
case class HtmlBlockLibraryTab(
                                elements: List[(BeProgram, Element)]
                              ) extends HtmlAppElement {

  private lazy val domElement = div(
    cls := "block-library-tab",
    elements.map(_._2)
  )

  override def getDomElement(): Element = domElement

}

object HtmlBlockLibraryTab {


  def apply(editorState: EditorState, programs: List[BeProgram]): HtmlBlockLibraryTab = {
    HtmlBlockLibraryTab(programs.map(curProgram => (curProgram, progToElement(curProgram, editorState))))
  }

  def apply(editorState: EditorState, allPrograms: List[BeProgram], existingRenderings: Map[BeProgram, Element]): HtmlBlockLibraryTab = {
    // println("allPrograms: " + allPrograms.map(_.fullProgram.expressionIO.getInLanguage(Python, English)).mkString("\n"))
    // println("fakedsize: " + existingRenderings.size + " / " + allPrograms.size + " = " + existingRenderings.size.toDouble / allPrograms.size.toDouble * 100 + "%")
    HtmlBlockLibraryTab(allPrograms.map(curProgram => (curProgram, existingRenderings.getOrElse(curProgram, progToElement(curProgram, editorState)))))
  }

  private def controlStructureProgToElement(curProgram: BeProgram, editorState: EditorState): Element =
    HtmlBeTreeDisplay.forControlStructureInLibraryTab(curProgram, editorState).treeInContainerDiv

  private def progToElement(curProgram: BeProgram, editorState: EditorState): Element =
    HtmlBeTreeDisplay.forLibraryTab(curProgram, editorState).treeInContainerDiv

  def getDefaultTurtleLibraryTab(editorState: EditorState): HtmlBlockLibraryTab = {
    val defaultPrograms = getDefaultLibraryPrograms()
    val defaultFaked = getFakedControlFlowPrograms(editorState)
    HtmlBlockLibraryTab(editorState, defaultPrograms ++ defaultFaked.keys, defaultFaked)
  }

  def fakeElement(editorState: EditorState, programToDisplay: BeProgram, text: LanguageMap[HumanLanguage]): (BeProgram, Element) = {

    val pointZero = Point[Double](0, 0)
    val textShape = TextShape(text)
    val res = ShapeAroundShape(RectangleShape, textShape)
    val resDiv = div(
      child <-- editorState.rendererConfigVar.signal.map(renderingInfo => {

        val amends: Seq[L.Modifier[L.SvgElement]] = List(svg.fill := renderingInfo.colorPalette.yellows(2).toWebColor.webStyleRgbString)

        res
          .addAmends(amends)
          .render(renderingInfo, new Bounds[Double](pointZero, res.displaySize(renderingInfo)))
          .toPlainDisplayDiv
      })
    )

    (programToDisplay, resDiv
      .amend(editorState.libaryTreeControllerConfig.now().getHtmlDragDropAmends(programToDisplay, resDiv))
      .amend(editorState.libaryTreeControllerConfig.now().getMouseAmendsForDiv(programToDisplay, resDiv)))
  }

  private def fakeLibraryTabElementsWithUniversalMap(editorState: EditorState, toFake: List[(String, BeProgram)]): Map[BeProgram, Element] = {
    fakeLibraryTabElements(editorState, toFake.map(tup => (LanguageMap.universalMap(tup._1), tup._2)))
  }

  private def fakeLibraryTabElements(editorState: EditorState, toFake: List[(LanguageMap[HumanLanguage], BeProgram)]): Map[BeProgram, Element] = {
    val resMap = mutable.HashMap[BeProgram, Element]()
    toFake.map(tup => fakeElement(editorState, tup._2, tup._1)).foreach(tup => resMap.put(tup._1, tup._2))
    resMap.toMap
  }

  def getFakedControlFlowPrograms(editorState: EditorState): Map[BeProgram, Element] = {

    val condSequence = BeSequence(List(BeUseValue(BeDataValueLiteral("true"), None)), BeSequenceInfo(Some(BeDataType.Boolean), Some(1)))
    val passSequence = BeExpression.pass
    val whileProg = BeProgram(BeWhile(condSequence, passSequence))
    val ifProg = BeProgram(BeIfElse(condSequence, passSequence, passSequence))

    val whileMap = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
      AppLanguage.English -> "REPEAT-WHILE",
      AppLanguage.German -> "WIEDERHOLE-SOLANGE",
      AppLanguage.French -> "RÉPÉTER-TANT-QUE",
      AppLanguage.Ukrainian -> "ПОВТОРЮВАТИ-ПОКИ",
      AppLanguage.Russian -> "ПОВТОРЯТЬ-ПОКА",
      AppLanguage.Turkish -> "TEKRARLA-SÜRECE",
      AppLanguage.Danish -> "GENTAG-MENS"
    ))
    val ifMap = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
      AppLanguage.English -> "IF/THEN/ELSE",
      AppLanguage.German -> "FALLS/DANN/SONST",
      AppLanguage.French -> "SI/ALORS/SINON",
      AppLanguage.Ukrainian -> "ЯКЩО/ТОДІ/ІНАКШЕ",
      AppLanguage.Russian -> "ЕСЛИ/ТОГДА/ИНАЧЕ",
      AppLanguage.Turkish -> "EĞER/O HALDE/AKSİ",
      AppLanguage.Danish -> "HVIS/SÅ/ELLERS"
    ))

    fakeLibraryTabElements(editorState, List((whileMap, whileProg), (ifMap, ifProg)))

  }

  def getDefaultLibraryPrograms(): List[BeProgram] = {

    val forwardName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "forward",
      AppLanguage.German -> "vorwärts",
      AppLanguage.French -> "forward",
      AppLanguage.Ukrainian -> "forward",
      AppLanguage.Russian -> "forward",
      AppLanguage.Turkish -> "forward",
      AppLanguage.Danish -> "forward"
    ))
    val rotateName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "rotateClockwise",
      AppLanguage.German -> "dreheImUhrzeigersinn",
      AppLanguage.French -> "rotateClockwise",
      AppLanguage.Ukrainian -> "rotateClockwise",
      AppLanguage.Russian -> "rotateClockwise",
      AppLanguage.Turkish -> "rotateClockwise",
      AppLanguage.Danish -> "rotateClockwise"
    ))
    val distName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "distance",
      AppLanguage.German -> "distanz",
      AppLanguage.French -> "distance",
      AppLanguage.Ukrainian -> "distance",
      AppLanguage.Russian -> "distance",
      AppLanguage.Turkish -> "distance",
      AppLanguage.Danish -> "distance"
    ))
    val degName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "degree",
      AppLanguage.German -> "grad",
      AppLanguage.French -> "degree",
      AppLanguage.Ukrainian -> "degree",
      AppLanguage.Russian -> "degree",
      AppLanguage.Turkish -> "degree",
      AppLanguage.Danish -> "degree"
    ))

    val dayName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "addDays",
      AppLanguage.German -> "addiereTage",
      AppLanguage.French -> "addDays",
      AppLanguage.Ukrainian -> "addDays",
      AppLanguage.Russian -> "addDays",
      AppLanguage.Turkish -> "addDays",
      AppLanguage.Danish -> "addDays"
    ))
    val dateName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "date",
      AppLanguage.German -> "datum",
      AppLanguage.French -> "date",
      AppLanguage.Ukrainian -> "date",
      AppLanguage.Russian -> "date",
      AppLanguage.Turkish -> "date",
      AppLanguage.Danish -> "date"
    ))
    val dayNrName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "dayNr",
      AppLanguage.German -> "tageAnzahl",
      AppLanguage.French -> "dayNr",
      AppLanguage.Ukrainian -> "dayNr",
      AppLanguage.Russian -> "dayNr",
      AppLanguage.Turkish -> "dayNr",
      AppLanguage.Danish -> "dayNr"
    ))

    List(
      BeProgram.createSimpleFunc(forwardName, List(distName), List(BeDataType.Numeric), List("100"), None),
      BeProgram.createSimpleFunc(rotateName, List(degName), List(BeDataType.Numeric), List("100"), None),
      BeProgram.createSimpleFunc(dayName, List(dateName, dayNrName), List(BeDataType.Date, BeDataType.Numeric), List("11.10.1999", "3"), None),
      //BeProgram.parseSimpleIf(),
      //BeProgram.parseSimpleWhile()
    )
  }


}
