package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.geometry.{Bounds, Point}
import contentmanagement.model.language.AppLanguage.{English, Python}
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.controlStructures.{BeIfElse, BeSequence, BeSequenceInfo, BeWhile}
import contentmanagement.model.vm.code.usage.BeUseValue
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeDataType.BeDataTypeAtomic
import contentmanagement.webElements.HtmlAppElement
import contentmanagement.webElements.svg.shapes.TextShape
import contentmanagement.webElements.svg.shapes.composite.ShapeAroundShape
import contentmanagement.webElements.svg.shapes.datatypes.RectangleShape
import interactionPlugins.blockEnvironment.programming.BeProgram

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

  def fakeElement(editorState: EditorState, programToDisplay: BeProgram, textString: String): (BeProgram, Element) = {
    val renderingInfo = editorState.rendererConfigVar.now()
    val pointZero = Point[Double](0, 0)
    val amends: Seq[L.Modifier[L.SvgElement]] = List(svg.fill := renderingInfo.colorPalette.yellows(1).toWebStyleString)
    val textShape = TextShape(LanguageMap.universalMap(textString))
    val res = ShapeAroundShape(RectangleShape, textShape).addAmends(amends)
    val resDiv = res
      .render(renderingInfo, new Bounds[Double](pointZero, res.displaySize(renderingInfo)))
      .toPlainDisplayDiv

    (programToDisplay, resDiv
      .amend(editorState.libaryTreeControllerConfig.now().getHtmlDragDropAmends(programToDisplay, resDiv))
      .amend(editorState.libaryTreeControllerConfig.now().getMouseAmendsForDiv(programToDisplay, resDiv)))
  }
  
  private def fakeLibraryTabElements(editorState: EditorState, toFake: List[(String, BeProgram)]): Map[BeProgram, Element] = {
    val resMap = mutable.HashMap[BeProgram, Element]()
    toFake.map(tup => fakeElement(editorState, tup._2, tup._1)).foreach(tup => resMap.put(tup._1, tup._2))
    resMap.toMap    
  }
  
  def getFakedControlFlowPrograms(editorState: EditorState): Map[BeProgram, Element] = {

    val condSequence = BeSequence(List(BeUseValue(BeDataValueLiteral("true"), None)), BeSequenceInfo(Some(BeDataType.Boolean), Some(1)))
    val passSequence = BeExpression.pass
    val whileProg = BeProgram(BeWhile(condSequence, passSequence))
    val ifProg = BeProgram(BeIfElse(condSequence, passSequence, passSequence))
    
    fakeLibraryTabElements(editorState, List( ("WHILE", whileProg), ("IF/ELSE", ifProg)))
    
  }

  def getDefaultLibraryPrograms(): List[BeProgram] = {

    val forwardName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "forward", AppLanguage.German -> "vorwärts"))
    val rotateName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "rotateClockwise", AppLanguage.German -> "dreheImUhrzeigersinn"))
    val distName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "distance", AppLanguage.German -> "distanz"))
    val degName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "degree", AppLanguage.German -> "grad"))

    val dayName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "addDays", AppLanguage.German -> "addiereTage"))
    val dateName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "date", AppLanguage.German -> "datum"))
    val dayNrName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "dayNr", AppLanguage.German -> "tageAnzahl"))

    List(

      BeProgram.createSimpleFunc(forwardName, List(distName), List(BeDataType.Numeric), List("100"), None),
      BeProgram.createSimpleFunc(rotateName, List(degName), List(BeDataType.Numeric), List("100"), None),
      BeProgram.createSimpleFunc(dayName, List(dateName, dayNrName), List(BeDataType.Date, BeDataType.Numeric), List("11.10.1999", "3"), None),
      //BeProgram.parseSimpleIf(),
      //BeProgram.parseSimpleWhile()
    )
  }


}
