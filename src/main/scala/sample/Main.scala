package sample

import java.io.{BufferedOutputStream, FileOutputStream, File}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{TimeUnit, ThreadFactory, Executors}

import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration.Duration
import scala.io.StdIn
import scala.util.Random
import scala.util.control.NonFatal


object Main {
  def main(args: Array[String]): Unit = {
    val uploader = new S3Uploader(AWSS3Config.load(), executionContext)

    println("Generating big files ...")
    val files = for (i <- 0 until 20) yield
      generateBigFile(megabytes = 5)

    println("PRESS ENTER TO UPLOAD ...")
    StdIn.readLine()

    val periodicPing = startLiveCheck()

    try {
      println("Uploading ...")
      val futures = for (f <- files) yield
        uploader.upload(s"test/${f.getName}", f)

      val task = Future.sequence(futures)
      val uploadResults = Await.result(task, Duration.Inf)

      println(uploadResults)
    }
    catch {
      case NonFatal(ex) =>
        System.err.println(ex.getMessage)
    }
    finally {
      periodicPing.cancel(false)
      files.foreach(_.delete())
      uploader.shutdown()
      System.out.println("SHUTTING DOWN!")
    }
  }

  /**
   * Scheduler used for periodic pings, to check for live-ness.
   */
  val scheduler = Executors.newScheduledThreadPool(2,
    new ThreadFactory {
      private[this] val count = new AtomicInteger(0)
      def newThread(r: Runnable) = {
        val th = new Thread(r)
        th.setDaemon(true)
        th.setName(s"my-executor-${count.getAndIncrement}")
        th
      }
    })

  /**
   * Single threaded execution context used (instead of global).
   */
  implicit val executionContext = new ExecutionContext {
    def execute(runnable: Runnable) =
      scheduler.execute(new Runnable {
        def run() = 
          try runnable.run() catch {
            case NonFatal(ex) => reportFailure(ex)
          }
      })

    def reportFailure(cause: Throwable) = {
      cause.printStackTrace(System.err)
    }
  }

  /**
   * A periodic task whose purpose is to check if our thread-pool
   * can process other stuff - demonstrating that uploads are doing
   * blocking I/O.
   */
  def startLiveCheck() = {
    val runnable = new Runnable {
      var count = 0
      def run() = {
        count += 1
        println(s"$count - ping")
        System.out.flush()
      }
    }
    
    scheduler.scheduleAtFixedRate(
      runnable, 0, 1, TimeUnit.SECONDS)
  }

  /**
   * Generates big test file.
   */
  def generateBigFile(megabytes: Int) = {
    val kilobytes = megabytes.toLong * 1024
    val buffer = new Array[Byte](1024)
    val file = File.createTempFile("test", ".dat")
    val out = new BufferedOutputStream(new FileOutputStream(file))

    try {
      var idx = 0L
      while (idx < kilobytes) {
        Random.nextBytes(buffer)
        out.write(buffer)
        idx += 1
      }
      file
    }
    finally {
      out.close()
    }
  }
}