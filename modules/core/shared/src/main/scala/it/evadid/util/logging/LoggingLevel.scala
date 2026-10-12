package it.evadid.util.logging

import upickle.default.ReadWriter

enum LoggingLevel derives ReadWriter {
  case INFO, WARN, ERROR
}
