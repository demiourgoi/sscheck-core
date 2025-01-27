package es.ucm.fdi.demiourgoi.sscheck.gen

import scala.language.implicitConversions
import scala.collection.mutable.{Seq => MSeq}
import scala.collection.mutable.ListBuffer
import org.scalatest.matchers.{Matcher, MatchResult}

object PStream {
  def apply[A](windows : Window[A]*): PStream[A] = new PStream(ListBuffer.from(windows))
  def empty[A] : PStream[A] = new PStream(ListBuffer.empty)

  implicit def batchSeq2dstream[A](windows : Seq[Window[A]]) : PStream[A] = PStream(windows:_*)
  implicit def batchMSeq2dstream[A](windows : MSeq[Window[A]]) : PStream[A] = new PStream(windows)
  implicit def seqSeq2dstream[A](windows : Seq[Seq[A]]) : PStream[A] = PStream(windows.map(Window[A](_:_*)):_*)
  implicit def seqMSeq2dstream[A](windows : Seq[MSeq[A]]) : PStream[A] = new PStream(windows.map{Window(_)})
  implicit def mseqMSeq2dstream[A](windows : MSeq[MSeq[A]]) : PStream[A] = new PStream(windows.map{Window(_)})
}

/** An object of this class represents a finite prefix of a discrete data streams,
 *  aka prefix DStream or just PDStream
 * */
case class PStream[A](windows : MSeq[Window[A]]) extends MSeq[Window[A]] {
  override def apply(idx : Int) = windows.apply(idx)
  override def iterator = windows.iterator
  override def length = windows.length
  override def update(idx: Int, elem: Window[A]): Unit = windows.update(idx, elem)

  // Note def ++(other : DStream[A]) : DStream[A] is inherited from Seq[_]

  /** @return a DStream for the batch-by-batch concatenation of this
  *  and other. Note we fill both PDStreams with empty batches. This
  *  implies both PDStreams are implicitly treated as they where
  *  infinitely extended with empty batches
  */
  def #+(other : PStream[A]) : PStream[A] = {
    windows. zipAll(other, Window.empty, Window.empty)
           . map(xs12 => xs12._1 ++ xs12._2)
  }

  /** @return true iff each batch of this dstream is contained in the batch
   *  at the same position in other. Note this implies that true can be
   *  returned for cases when other has more batches than this
   * */
  def subsetOf(other : PStream[A]) : Boolean = {
    windows
      .zip(other.windows)
      .map({case (thisBatch, otherBatch) =>
              thisBatch.forall(otherBatch.contains(_))
           })
      .forall(identity[Boolean])
  }
}

trait PStreamMatchers {
  class PStreamSubsetOf[A](expectedSuperStream : PStream[A]) extends Matcher[PStream[A]] {
    override def apply(observedStream : PStream[A]) : MatchResult = {
      // FIXME reimplement with Inspector for better report
      MatchResult(observedStream.subsetOf(expectedSuperStream),
      			s"""$observedStream is not a pointwise subset of $expectedSuperStream""",
      			s"""$observedStream is a pointwise subset of $expectedSuperStream""")
    }
  }
  def beSubsetOf[A](expectedSuperDStream : PStream[A]) = new PStreamSubsetOf(expectedSuperDStream)
}
object PStreamMatchers extends PStreamMatchers
