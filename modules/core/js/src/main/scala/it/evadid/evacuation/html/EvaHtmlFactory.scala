package it.evadid.evacuation.html

import it.evadid.evacuation.config.property.ObservableConfigProperty
import it.evadid.evacuation.config.property.discrete.ObservableDiscreteConfigProperty
import it.evadid.evacuation.core.graphic.model.EvaFileInformation
import it.evadid.evacuation.core.utility.GeneralUtility
import it.evadid.evacuation.html.elements.ObservableInputElement.HtmlInputChangeListener
import it.evadid.evacuation.html.elements.TabPaneModel.TabProperty
import it.evadid.evacuation.html.elements.{BasicObservableInputElement, ObservableFileInputElement, TabPaneModel}
import org.scalajs.dom
import org.scalajs.dom.html.{Input, TextArea}
import org.scalajs.dom.{Element, MouseEvent, document}

import scala.scalajs.js.Any

object EvaHtmlFactory {


  def createControlTabElement(textContent: String, onClick: MouseEvent => Any): Element = {
    val curTab = document.createElement("div")
    curTab.setAttribute("id", "tab-selector")
    curTab.setAttribute("class", "tab-inactive")
    curTab.textContent = textContent
    curTab.addEventListener("click", onClick)
    curTab
  }

  def createFileUploadButton(label: String, id: String, attributes: Map[String, String], listener: EvaFileInformation => Any): Element = {
    val res = new ObservableFileInputElement(id, listener, attributes)
    res.getElement
  }

  def createPropertyTickBox(property: ObservableConfigProperty[Boolean]): Element = {
    val id = "select-" + property.property.name

    val form = document.createElement("form")

    val checkbox = document.createElement("input")
    checkbox.setAttribute("type", "checkbox")
    checkbox.setAttribute("id", id)

    def syncCheckboxAttribute(): Unit = {
      if (property.getValue.value) {
        checkbox.setAttribute("checked", String.valueOf(property.getValue.value))
      } else {
        checkbox.removeAttribute("checked")
      }
    }

    syncCheckboxAttribute()

    checkbox.addEventListener("change", (e: Any) => {
      property.setValue(!property.getValue.value)
      syncCheckboxAttribute()
      checkbox.setAttribute("checked", String.valueOf(property.getValue.value))
      true
    })

    val label = document.createElement("label")
    label.setAttribute("for", id)
    label.textContent = property.property.description.getOrElse(property.property.name)

    form.appendChild(checkbox)
    form.appendChild(label)

    form
  }

  def createRadioButtonForm[T](observableConfigProperty: ObservableDiscreteConfigProperty[T], filterProperties: T => Boolean): Element = {

    val property = observableConfigProperty.property

    val mainDiv = document.createElement("div")
    mainDiv.setAttribute("class", "box-pane")


    val span = document.createElement("span")
    span.setAttribute("class", "box-span")
    span.textContent = property.description.getOrElse(property.name)
    mainDiv.appendChild(span)


    val form = document.createElement("form")

    form.setAttribute("id", "select-" + observableConfigProperty.property.name)
    form.setAttribute("class", "box-content-pane button")
    form.setAttribute("action", "");
    mainDiv.appendChild(form)

    val showValues = observableConfigProperty.possibleValues().filter(obj => filterProperties.apply(obj.value))

    showValues.foreach(curOption => {

      val text = curOption.description.getOrElse(curOption.name)
      val radioId = "radio-element-" + curOption.name

      val radioElement = document.createElement("input")
      radioElement.setAttribute("type", "radio")
      radioElement.setAttribute("name", "radio-group-" + property.name)
      radioElement.setAttribute("value", radioId)
      if (observableConfigProperty.getValue.name == curOption.name) {
        radioElement.setAttribute("checked", "true")
      }
      radioElement.addEventListener("change", (e: Any) => {
        observableConfigProperty.setValue(curOption.value)
        false
      })

      form.appendChild(radioElement)

      val textElement = document.createElement("label")
      textElement.setAttribute("for", radioId)
      textElement.textContent = text
      form.appendChild(textElement)
    })

    mainDiv
  }


  def createRadioButtonForm[T](observableConfigProperty: ObservableDiscreteConfigProperty[T]): Element = {
    createRadioButtonForm(observableConfigProperty, (_ => true): T => Boolean)
  }


  def createLabel(content: String): Element = {
    val label = document.createElement("p")
    label.textContent = content
    label
  }

  def createTextField(defaultText: String): Input = {
    val field = document.createElement("INPUT").asInstanceOf[Input]
    field.setAttribute("type", "text")
    field.setAttribute("value", defaultText)
    field
  }

  case class InputPropertyChangedEvent[T](inputComponentId: String, oldValue: T, newValue: T)


  def createNumberForm(observableConfigProperty: ObservableConfigProperty[Integer], stepSize: Option[Int] = None, minValue: Option[Int] = None, maxValue: Option[Int] = None, additionalListener: List[HtmlInputChangeListener[Int]] = List()): Element = {

    createNumberField(
      observableConfigProperty.property.description.getOrElse(observableConfigProperty.property.name),
      "number-form-" + observableConfigProperty.property.name,
      observableConfigProperty.getValue.value,
      changeData => {
        observableConfigProperty.setValue(changeData.newValue)
        additionalListener.foreach(listener => listener.apply(changeData))
      },
      stepSize,
      minValue,
      maxValue
    )
  }

  def createNumberField(label: String, id: String, defaultValue: Integer, onChangeEvent: HtmlInputChangeListener[Int], stepSize: Option[Int] = None, minValue: Option[Int] = None, maxValue: Option[Int] = None): Element = {

    val root = document.createElement("div")
    root.setAttribute("class", "two-col-grid")

    root.appendChild(createLabel(label))

    val attributes = Map("min" -> minValue, "max" -> maxValue, "step" -> stepSize)
      .collect { case (name, Some(value)) => name -> value.toString }

    val field = new BasicObservableInputElement[Int](id, "number", defaultValue, onChangeEvent, GeneralUtility.factoryToEitherFactory(str => Integer.parseInt(str)), attributes)
    root.appendChild(field.getElement)

    root
  }

  def createButton(id: String, label: String, listener: dom.MouseEvent => Unit): Element = {
    val button = document.createElement("button")
    button.setAttribute("id", id)
    button.textContent = label
    button.addEventListener("click", (e: dom.MouseEvent) => {
      listener.apply(e)
      false
    })
    button
  }

  def boxElement(description: String, element: Element): Element = {
    val boxPane = document.createElement("div")
    boxPane.setAttribute("class", "box-pane")

    val span = document.createElement("span")
    span.setAttribute("class", "box-span")
    span.textContent = description

    val contentPane = document.createElement("div")
    contentPane.setAttribute("class", "box-content-pane")
    contentPane.appendChild(element)

    boxPane.appendChild(span)
    boxPane.appendChild(contentPane)

    boxPane

  }

  def getStandardTextArea(placeholder: String, lines: Int, id: String, onChange: dom.Event => Unit): TextArea = {

    val inputBox = document.createElement("textarea").asInstanceOf[TextArea]

    inputBox.setAttribute("type", "text")
    inputBox.setAttribute("id", id)
    inputBox.setAttribute("class", "textarea-input")
    inputBox.setAttribute("rows", "" + lines)
    inputBox.setAttribute("placeholder", placeholder)
    inputBox.addEventListener("input", onChange)
    inputBox.addEventListener("change", onChange)

    inputBox
  }

  def getStandardTabPane(model: TabPaneModel, elementFactories: Map[TabProperty, () => Element]): Element = {
    // Pane
    val tabPane = document.createElement("div")
    tabPane.setAttribute("id", "tab-pane")

    // Selector Container
    val selectorContainer = document.createElement("div")
    selectorContainer.setAttribute("id", "tab-selector-container")
    tabPane.appendChild(selectorContainer)

    // Content
    val contentPane = document.createElement("div")
    contentPane.setAttribute("id", "tab-content-pane")
    tabPane.appendChild(contentPane)

    // Selectors
    val selectors: List[(TabProperty, Element)] = model.tabs.map { curTab =>
      val selector = document.createElement("div")
      selector.textContent = curTab.description
      selector.setAttribute("id", "tab-selector")
      selectorContainer.appendChild(selector)
      curTab -> selector
    }

    def select(property: TabProperty, selector: Element): Unit = {
      selectors.foreach { case (_, other) => other.setAttribute("class", "tab-inactive") }

      model.currentTab.setValue(property)

      HtmlHelper.clearChildrenFromElement(contentPane)
      val content = elementFactories(property).apply()
      contentPane.appendChild(content)
      selector.setAttribute("class", "tab-active")
    }

    selectors.foreach { (curTab, selector) =>
      selector.addEventListener("click", (e: dom.MouseEvent) => select(curTab, selector))
    }

    val (firstTab, firstSelector) = selectors.head
    select(firstTab, firstSelector)

    tabPane
  }

}
