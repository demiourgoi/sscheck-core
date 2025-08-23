package io.github.demiourgoi.sscheck.gen

import scala.language.implicitConversions
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.{Seq => MSeq}
import scala.collection.immutable.{Seq => ISeq}

object Window {
  def apply[A](points : A*): Window[A] = new Window(ListBuffer.from(points))
  def apply[A](points: Iterable[A]): Window[A] = new Window(ListBuffer.from(points))
  def empty[A] : Window[A] = new Window(points = ListBuffer.empty)

  implicit def seq2batch[A](seq : Seq[A]) : Window[A] = new Window(ListBuffer.from(seq))
  implicit def iseq2batch[A](seq : ISeq[A]) : Window[A] = seq2batch(seq)
  implicit def mseq2batch[A](seq : MSeq[A]) : Window[A] = new Window(seq)
  implicit def batch2Iseq[A](window: Window[A]): ISeq[A] = window.points.toSeq
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