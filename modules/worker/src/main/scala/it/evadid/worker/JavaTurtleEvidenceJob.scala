package it.evadid.worker

import it.evadid.distribution.commandTypes.WorkerCommands.{JavaExecutionEvidenceRequest, RunPythonRequest, RunPythonResult}
import it.evadid.vm.parsing.java.turtle.{JavaTurtleInputLimits, JavaTurtleVmPrograms}
import it.evadid.vm.simulation.java.JavaTurtleExecutionEvidence
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object JavaTurtleEvidenceJob {
  def run(request: JavaExecutionEvidenceRequest,
      runPython: RunPythonRequest => Future[RunPythonResult])(using ExecutionContext): Future[JavaTurtleExecutionEvidence.Snapshot] =
    Future {
      require(request.source != null && request.limits.isValid, "Invalid execution input.")
      require(request.commandIndex.forall(index => index >= 0 && index < request.limits.maxCommands), "Invalid command index.")
      require(request.arguments.size <= JavaTurtleInputLimits.MaxParameters, "Too many invocation arguments.")
      val program = JavaTurtleVmPrograms.compile(request.source).fold(
        _ => throw IllegalArgumentException("Unsupported Java source."), identity)
      val method = request.method.map { name =>
        program.bindings.methods.find(binding => binding.originalName == name && binding != program.bindings.entryPoint)
          .getOrElse(throw IllegalArgumentException("Unknown Java method.")).id
      }
      JavaTurtleExecutionEvidence.prepare(program, method, request.arguments.map(_.toValue), request.limits, request.budget)
    }.flatMap { prepared =>
      runPython(RunPythonRequest(prepared.code)).map { result =>
        require(result.stdout.isEmpty && result.stderr.isEmpty && result.callbackOps.isEmpty,
          "Unexpected Python output.")
        val verified = prepared.verify(result.value.str, prepared.context)
        request.commandIndex.fold(verified.snapshot)(verified.snapshotForCommand)
      }
    }.recoverWith { case NonFatal(_) =>
      Future.failed(IllegalArgumentException("Java execution observations are unavailable."))
    }
}
