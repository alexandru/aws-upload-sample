package sample

import java.util.Collections
import java.util.concurrent.{TimeUnit, AbstractExecutorService}

import scala.concurrent.{ExecutionContextExecutorService, ExecutionContext}

/**
 * For converting a Scala ExecutionContext into a Java ExecutorService.
 */
object ExecutionContextAsExecutorService {
  def apply(ec: ExecutionContext): ExecutionContextExecutorService = ec match {
    case null => throw null
    case service: ExecutionContextExecutorService => service
    case other => new AbstractExecutorService with ExecutionContextExecutorService {
      override def prepare(): ExecutionContext = other
      override def isShutdown = false
      override def isTerminated = false
      override def shutdown() = ()
      override def shutdownNow() = Collections.emptyList[Runnable]
      override def execute(runnable: Runnable): Unit = other execute runnable
      override def reportFailure(t: Throwable): Unit = other reportFailure t
      override def awaitTermination(length: Long,unit: TimeUnit): Boolean = false
    }
  }
}
