package todomove.webElementsOld.webElements.svg.builder.controlFlow.path

enum PathStatus derives upickle.default.ReadWriter {
  case PAUSED, FINISHED, OPEN, HANDLED
}