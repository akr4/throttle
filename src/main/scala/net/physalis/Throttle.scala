package net.physalis

import java.time.{ZonedDateTime, Clock}
import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit, Delayed, DelayQueue}

import scala.collection._

class Token(clock: Clock) extends Delayed {
  private val enqueuedAt = ZonedDateTime.now(clock)
  
  override def getDelay(unit: TimeUnit): Long =
    unit.convert(enqueuedAt.plusSeconds(10).toEpochSecond - ZonedDateTime.now(clock).toEpochSecond, TimeUnit.SECONDS)

  override def compareTo(o: Delayed): Int =
    getDelay(TimeUnit.SECONDS).compareTo(o.getDelay(TimeUnit.SECONDS))
}

class ExpiredToken (clock: Clock) extends Token(clock) {
  override def getDelay(unit: TimeUnit): Long = -1
}

class Throttle[Bucket] (
  processor: (Bucket => Unit)
)(implicit _clock: Clock) {
  import scala.concurrent._
  import ExecutionContext.Implicits.global

  private val tokenQueue = new DelayQueue[Token]
  private val buckets = new ConcurrentLinkedQueue[Bucket]
  private val waitingTasks = new ConcurrentLinkedQueue[Int]

  tokenQueue.add(new ExpiredToken(_clock))
  tokenQueue.add(new ExpiredToken(_clock))
  tokenQueue.add(new ExpiredToken(_clock))

  def put(bucket: Bucket)(implicit clock: Clock) {
    if (tokenQueue.poll(0, TimeUnit.SECONDS) != null) {
      doTask(Seq(bucket))
    } else {
      buckets.add(bucket)
      // 待ちタスクがある場合は早々に処理を終わらせる
      // タイミングにより複数の待ちタスクが生じても構わないので同期しない
      if (waitingTasks.isEmpty) {
        waitingTasks.add(0)
        Future {
          tokenQueue.take()
          val bs = remainingBuckets
          if (bs.nonEmpty) {
            doTask(bs)
          } else {
            returnToken()
          }
          waitingTasks.poll()
        }
      }
    }
  }

  private def doTask(buckets: Seq[Bucket]) {
    for (b <- buckets) {
      processor(b)
    }
    returnToken()
  }

  private def returnToken()(implicit clock: Clock) {
    tokenQueue.add(new Token(clock))
  }

  private def remainingBuckets: Seq[Bucket] = {
    def remainingBucketsR(bs: Seq[Bucket]): Seq[Bucket] = {
      val b = buckets.poll()
      if (b != null) {
        remainingBucketsR(bs :+ b)
      } else {
        bs
      }
    }

    remainingBucketsR(Seq())
  }
}
