package sample

import java.io.File

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.event.ProgressEventType._
import com.amazonaws.event.{ProgressEvent, ProgressListener}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.model.UploadResult
import scala.concurrent.{ExecutionContext, Future, Promise, blocking}
import scala.util.Try
import scala.util.control.NonFatal


final class S3Uploader(config: AWSS3Config, io: ExecutionContext) {
  def upload(key: String, file: File): Future[UploadResult] = {
    val p = Promise[UploadResult]()

    try {
      val request = manager.upload(config.bucketName, key, file)

      request.addProgressListener(new ProgressListener {
        def progressChanged(e: ProgressEvent) = {
          e.getEventType match {
            case TRANSFER_COMPLETED_EVENT | TRANSFER_FAILED_EVENT =>
              p.tryComplete(Try(blocking(request.waitForUploadResult())))
            case TRANSFER_CANCELED_EVENT =>
              p.tryFailure(new UploadCancelledException(key))
            case _ =>
              () // ignore
          }
        }
      })
    }
    catch {
      case NonFatal(ex) =>
        Future.failed(ex)
    }

    p.future
  }

  def shutdown() = {
    manager.shutdownNow(true)
  }

  /**
   * Thrown when an upload is canceled.
   */
  class UploadCancelledException(key: String) extends RuntimeException(key)

  private val (s3Client, manager) = {
    val credentials = new BasicAWSCredentials(
      config.accessKey,
      config.secretAccessKey
    )

    val client = new AmazonS3Client(credentials)
    client.setRegion(config.region.toAWSRegion)

    val manager = new TransferManager(
      client,
      ExecutionContextAsExecutorService(io)
    )

    (client, manager)
  }
}
