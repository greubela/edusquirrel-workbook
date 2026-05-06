package it.evadid.distribution

import it.evadid.distribution.ExecutionCommand.{ExecutionInfo, ExecutionResult}

import scala.concurrent.Future

trait ExecutionServer {
  
  def getExecutor: Executor
    
}
