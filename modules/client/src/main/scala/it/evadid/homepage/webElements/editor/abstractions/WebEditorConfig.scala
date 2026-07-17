package it.evadid.homepage.webElements.editor.abstractions

trait WebEditorConfig {

  protected def additionalCssClasses: List[String]

  def inputCssClassStr(externalCssStrings: List[String]): String = additionalCssClasses.mkString(" ") ++ externalCssStrings.mkString(" ")

}
