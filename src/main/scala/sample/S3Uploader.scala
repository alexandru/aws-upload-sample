package sample

import java.io.File

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.event.ProgressEventType._
import com.amazonaws.event.{ProgressEvent, SyncProgressListener}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.transfer.TransferManager
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal


final class S3Uploader(config: AWSS3Config, io: ExecutionContext) {
  def upload(key: String, file: File): Future[Unit] = {
    val p = Promise[Unit]()

    io.execute(new Runnable {
      def run() = {
        try {
          // call is blocking, shows up in profiling
          val request = manager.upload(config.bucketName, key, file)

          request.addProgressListener(new SyncProgressListener {
            def progressChanged(e: ProgressEvent) = {
              e.getEventType match {
                case TRANSFER_COMPLETED_EVENT =>
                  p.trySuccess(())
                case TRANSFER_FAILED_EVENT =>
                  p.tryFailure(new UploadFailedException(key, e))
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
      }
    })

    p.future
  }

  def shutdown() = {
    manager.shutdownNow(true)
  }

  /**
   * Thrown when an upload is canceled.
   */
  class UploadCancelledException(key: String) extends RuntimeException(key)

  /**
   * Thrown when an upload fails.
   */
  class UploadFailedException(key: String, event: ProgressEvent)
    extends RuntimeException(s"Upload for $key failed, signaled with $event")

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
