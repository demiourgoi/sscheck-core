package es.ucm.fdi.sscheck.gen

import scala.language.implicitConversions
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.{Seq => MSeq}

object Window {
  def apply[A](points : A*): Window[A] = new Window(ListBuffer.from(points))
  def empty[A] : Window[A] = new Window(points = ListBuffer.empty)

  implicit def seq2batch[A](seq : Seq[A]) : Window[A] = new Window(ListBuffer.from(seq))
  implicit def mseq2batch[A](seq : MSeq[A]) : Window[A] = new Window(seq)
}

/** Objects of this class represent batches of elements
 *  in a discrete data stream
 * */
case class Window[A](points : MSeq[A]) extends MSeq[A] {
  override def apply(idx : Int) = points.apply(idx)
  override def iterator = points.iterator
  override def length = points.length
  override def update(idx: Int, elem: A): Unit = points.update(idx, elem)
}