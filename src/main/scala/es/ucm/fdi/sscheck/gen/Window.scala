package es.ucm.fdi.sscheck.gen

import scala.language.implicitConversions

object Window {
  def empty[A] : Window[A] = new Window(points = List():_*)

  implicit def seq2batch[A](seq : Seq[A]) : Window[A] = Window(seq:_*)
}

/** Objects of this class represent batches of elements
 *  in a discrete data stream
 * */
case class Window[A](points : A*) extends Seq[A] {
  override def toSeq : Seq[A] = points
  
  override def apply(idx : Int) = points.apply(idx)
  override def iterator = points.iterator
  override def length = points.length
  
}