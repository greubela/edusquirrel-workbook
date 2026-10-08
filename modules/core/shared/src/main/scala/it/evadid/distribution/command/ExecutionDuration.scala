package it.evadid.distribution.command

import upickle.default.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given

import java.time.LocalDateTime


case class ExecutionDuration(timeExecutionStarted: LocalDateTime, timeExecutionFinished: LocalDateTime) derives ReadWriter
