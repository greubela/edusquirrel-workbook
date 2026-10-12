package todomove.webElementsOld.webElements.svg.builder.controlFlow.path

enum PathType derives upickle.default.ReadWriter {
  case BASE, CONDITION_TRUE, CONDITION_FALSE, RETURNING_PATH
}