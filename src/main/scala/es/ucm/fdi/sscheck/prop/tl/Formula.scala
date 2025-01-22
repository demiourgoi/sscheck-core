package es.ucm.fdi.sscheck.prop.tl

import org.specs2.execute.Result
import org.scalacheck.Prop

// Explicit import is required since parallel collections are not in the standard lib
// https://stackoverflow.com/questions/57287607/missing-par-method-from-scala-collections
import scala.collection.parallel.CollectionConverters._
import scala.collection.parallel.ParSeq
import scala.collection.parallel.{ExecutionContextTaskSupport, TaskSupport}
import scalaz.syntax.std.boolean._
import scalaz.syntax.traverse._
import scalaz.std.list._
import scalaz.std.option._

import scala.annotation.tailrec
import scala.language.{implicitConversions, postfixOps}
object Formula {
  // default implicit FormulaParallelism https://stackoverflow.com/questions/12767074/how-to-provide-default-value-for-implicit-parameters-at-class-level
  implicit val defaultFormulaParallelism: FormulaParallelism =
    TaskSupportFormulaParallelism(new ExecutionContextTaskSupport())

  /** More succinct notation for timeouts when combined with TimeoutMissingFormula.on()  
   */
  implicit def intToTimeout(t : Int) : Timeout = Timeout(t)
  /** Completes a TimeoutMissingFormula when a timeout is implicitly available
   */
  implicit def timeoutMissingFormulaToFormula[T](f : TimeoutMissingFormula[T])
                                                (implicit t : Timeout) : Formula[T] = f.on(t)
  /** @return a formula where the result of applying letterToResult to the
    *         current letter must hold now
   */
  implicit def resultFunToNow[T, R](letterToResult : T => R)(implicit ev: R => Result): BindNext[T] =
    now(letterToResult andThen implicitly[Function[R, Result]])

  /** @return a formula where the result of applying letterToStatus to the
    *         current letter must hold now
    */
  implicit def statusFunToNow[T](letterToStatus: T => Prop.Status): BindNext[T] =
    now(letterToStatus)

  /** More succinct notation for BindNext formulas
   */
  implicit def atomsConsumerToBindNext[T](atomsConsumer: T => Formula[T]): BindNext[T] =
    BindNext.fromAtomsConsumer(atomsConsumer)

 /** Convert a Specs2 Result into a ScalaCheck Prop.Status
   * See
   * - trait Status: https://github.com/rickynils/scalacheck/blob/1.12.2/src/main/scala/org/scalacheck/Prop.scala
   * - subclasses of Result: https://etorreborre.github.io/specs2/api/SPECS2-3.6.2/index.html#org.specs2.execute.Result
   * and https://etorreborre.github.io/specs2/guide/SPECS2-3.6.2/org.specs2.guide.StandardResults.html
   * */
  @tailrec
  def resultToPropStatus(result : Result) : Prop.Status = result match {
    case err : org.specs2.execute.Error => Prop.Exception(err.t)
    case _ : org.specs2.execute.Failure => Prop.False
    case _ : org.specs2.execute.Pending => Prop.Undecided
    case _ : org.specs2.execute.Skipped => Prop.Undecided
    /* Prop.True is the same as passed, see lazy val passed
    * at https://github.com/rickynils/scalacheck/blob/1.12.2/src/main/scala/org/scalacheck/Prop.scala
    * TOOD: use Prop.Proof instead?
    */
    case _ : org.specs2.execute.Success => Prop.True
    case dec : org.specs2.execute.DecoratedResult[_] => resultToPropStatus(dec.result)
    case _ => Prop.Undecided
  }

/** @return a formula where the result of applying to the current letter
  *         the projection proj and then assertion must hold now
  */
def at[T, A, R](proj : (T) => A)(assertion : A => R)(implicit ev: R => Result): Formula[T] =
  now(proj andThen assertion andThen implicitly[Function[R, Result]])

/** @return a formula where the result of applying to the current tletter
  *         the projection proj and then assertion must hold now
 */
def atS[T, A](proj : (T) => A)(assertion: A => Prop.Status): Formula[T] =
  now(assertion compose proj)

/** @return a formula where the result of applying to the current tletter
  *         the projection proj and then assertion must hold in the next
  *         instant
  */
def atF[T, A](proj : (T) => A)(atomsConsumer : A => Formula[T]): Formula[T] =
  next(atomsConsumer compose proj)

  // Factories for non-temporal connectives: note these act as clever constructors
  // for `Or` and `And`
  def or[T](phis: Formula[T]*): Formula[T] =
    if (phis.isEmpty) Solved(Prop.False)
    else if (phis.length == 1) phis(0)
    else Or(phis:_*)
  def and[T](phis: Formula[T]*): Formula[T] =
    if (phis.isEmpty) Solved(Prop.True)
    else if (phis.length == 1) phis(0)
    else And(phis:_*)

  // Factories for temporal connectives
  // Using https://spray.readthedocs.io/en/latest/blog/2012-12-13-the-magnet-pattern.html
  // to handle JVM type erasure on overrides

  def next(magnet: NextMagnet): magnet.Result = magnet()
  sealed trait NextMagnet {
    type Result
    def apply(): Result
  }
  object NextMagnet {
    implicit def fromFormula[T](phi: Formula[T]): NextMagnet {type Result = Formula[T]} =
      new NextMagnet {
        override type Result = Formula[T]
        override def apply(): Result = Next(phi)
      }

    /** @return an application of BindNext to letterToFormula: the result of applying
     *         letterToFormula to the current letter must hold in the next instant
     * */
    implicit def fromLetterToFormula[T](letterToFormula: T => Formula[T]): NextMagnet {type Result = Formula[T]} =
      new NextMagnet {
        override type Result = Formula[T]
        override def apply(): Result = BindNext.fromAtomsConsumer(letterToFormula)
      }

    /** @return an application of BindNext to letterToFormula: the result of applying
     *         letterToFormula to the current letter must hold in the next instant
     * */
    implicit def fromLetterTimeToFormula[T](letterToFormula: (T, Time) => Formula[T]): NextMagnet {type Result = Formula[T]} =
      new NextMagnet {
        override type Result = Formula[T]
        override def apply(): Result = BindNext.fromAtomsTimeConsumer(letterToFormula)
      }

    /** @return the result of applying next to phi the number of
     *  times specified
     * */
    implicit def fromNextTimesToFormula[T](args: (Int, Formula[T])): NextMagnet {type Result = Formula[T]} =
      new NextMagnet {
        override type Result = Formula[T]
        override def apply(): Result = {
          val (times, phi) = args
          require(times >= 0, s"times should be >=0, found $times")
          (1 to times).foldLeft(phi) { (f, _) => new Next[T](f) }
        }
      }
  }

  /**
   *  NOTE the Scaladoc description for the variants of now() in the companion NowMagnet are true
   *  without a next because Result corresponds to a timeless formula, and because
   *  NextFormula.consume() leaves the formula in a solved state without a need to consume
   *  any additional letter after the first one
   *  */
  def now(magnet: NowMagnet): magnet.Result = magnet()
  sealed trait NowMagnet {
    type Result
    def apply(): Result
  }
  object NowMagnet {
    /** @return a formula where the result of applying letterToResult to the
     *         current letter must hold now
     */
    implicit def fromLetterToResult[T](letterToResult: T => Result): NowMagnet {type Result = BindNext[T]} =
      new NowMagnet {
        override type Result = BindNext[T]
        override def apply(): Result = BindNext(letterToResult)
      }

    /** @return a formula where the result of applying letterToResult to the
     *         current letter must hold now
     */
    implicit def fromLetterTimeToResult[T](letterToResult: (T, Time) => Result): NowMagnet {type Result = BindNext[T]} =
      new NowMagnet {
        override type Result = BindNext[T]
        override def apply(): Result = BindNext(letterToResult)
      }

    /** @return a formula where the result of applying letterToStatus to the
     *         current letter must hold now
     */
    implicit def fromLetterToStatus[T](letterToStatus: T => Prop.Status): NowMagnet {type Result = BindNext[T]} =
      new NowMagnet {
        override type Result = BindNext[T]
        override def apply(): Result = BindNext.fromStatusFun(letterToStatus)
      }

    /** @return a formula where the result of applying letterToStatus to the
     *         current letter must hold now
     */
    implicit def fromLetterTimeToStatus[T](letterToStatus: (T, Time) => Prop.Status): NowMagnet {type Result = BindNext[T]} =
      new NowMagnet {
        override type Result = BindNext[T]
        override def apply(): Result = BindNext.fromStatusTimeFun(letterToStatus)
      }
  }

  def consume(magnet: ConsumeMagnet): magnet.Result = magnet()
  sealed trait ConsumeMagnet {
    type Result
    def apply(): Result
  }
  object ConsumeMagnet {
    /** @return an application of BindNext to letterToFormula: the result of applying
     *         letterToFormula to the current letter must hold in the next instant
     * */
    implicit def fromAtomsConsumer[T](letterToFormula: T => Formula[T]): ConsumeMagnet {type Result = BindNext[T]} =
      new ConsumeMagnet {
        override type Result = BindNext[T]
        override def apply(): Result = BindNext.fromAtomsConsumer(letterToFormula)
      }

    /** @return a formula where the result of applying letterToStatus to the
     *         current letter must hold in the next instant
     */
    implicit def fromStatusFun[T](letterToStatus: T => Prop.Status): ConsumeMagnet {type Result = BindNext[T]} =
      new ConsumeMagnet {
        override type Result = BindNext[T]
        override def apply(): Result = BindNext.fromStatusFun(letterToStatus)
      }

    /** @return a formula where the result of applying letterToResult to the
     *         current letter must hold in the next instant
     */
    implicit def fromResultFun[T](letterToResult: T => Result): ConsumeMagnet {type Result = BindNext[T]} =
      new ConsumeMagnet {
        override type Result = BindNext[T]
        override def apply(): Result = BindNext(letterToResult)
      }

    /** @return an application of BindNext to letterToFormula: the result of applying
     *         letterToFormula to the current letter must hold in the next instant
     * */
    implicit def fromLetterTimeToFormula[T](letterToFormula: (T, Time) => Formula[T]): ConsumeMagnet {type Result = BindNext[T]} =
      new ConsumeMagnet {
        override type Result = BindNext[T]
        override def apply(): Result = BindNext.fromAtomsTimeConsumer(letterToFormula)
      }

    /** @return a formula where the result of applying letterToStatus to the
     *         current letter must hold in the next instant
     */
    implicit def fromLetterToStatus[T](letterToStatus: (T, Time) => Prop.Status): ConsumeMagnet {type Result = BindNext[T]} =
      new ConsumeMagnet {
        override type Result = BindNext[T]
        override def apply(): Result = BindNext.fromStatusTimeFun(letterToStatus)
      }

    /** @return a formula where the result of applying letterToResult to the
     *         current letter must hold in the next instant
     */
    implicit def fromLetterToResult[T](letterToResult: (T, Time) => Result): ConsumeMagnet {type Result = BindNext[T]} =
      new ConsumeMagnet {
        override type Result = BindNext[T]
        override def apply(): Result = BindNext(letterToResult)
      }
  }

  def eventually(magnet: EventuallyMagnet): magnet.Result = magnet()
  sealed trait EventuallyMagnet {
    type Result
    def apply(): Result
  }
  object EventuallyMagnet {
    implicit def fromFormula[T](phi: Formula[T]): EventuallyMagnet {type Result = TimeoutMissingFormula[T]} =
      new EventuallyMagnet {
        override type Result = TimeoutMissingFormula[T]
        override def apply(): Result = new TimeoutMissingFormula[T](Eventually(phi, _))
      }

    /** @return a formula where eventually the result of applying letterToFormula to the
     *         current letter must hold in the next instant
     */
    implicit def fromLetterToFormula[T](letterToFormula: T => Formula[T]): EventuallyMagnet {type Result = TimeoutMissingFormula[T]} =
      new EventuallyMagnet {
        override type Result = TimeoutMissingFormula[T]
        override def apply(): Result = eventually(next(letterToFormula))
      }

    /** @return a formula where eventually the result of applying letterToResult to the
     *         current letter must hold now
     */
    implicit def fromLetterToResult[T](letterToResult: T => Result): EventuallyMagnet {type Result = TimeoutMissingFormula[T]} =
      new EventuallyMagnet {
        override type Result = TimeoutMissingFormula[T]
        override def apply(): Result = eventually(now(letterToResult))
      }

    /** @return a formula where eventually the result of applying letterToStatus to the
     *         current letter must hold now
     */
    implicit def fromLetterToStatus[T](letterToStatus: T => Prop.Status): EventuallyMagnet {type Result = TimeoutMissingFormula[T]} =
      new EventuallyMagnet {
        override type Result = TimeoutMissingFormula[T]
        override def apply(): Result = eventually(now(letterToStatus))
      }
  }

  /** Alias of eventually that can be used when there is a name class, for example
   *  with EventuallyMatchers.eventually
   * */
  def later(magnet: EventuallyMagnet): magnet.Result = eventually(magnet)

  def always(magnet: AlwaysMagnet): magnet.Result = magnet()
  sealed trait AlwaysMagnet {
    type Result
    def apply(): Result
  }
  object AlwaysMagnet {
    implicit def fromFormula[T](phi: Formula[T]): AlwaysMagnet {type Result = TimeoutMissingFormula[T]} =
      new AlwaysMagnet {
        override type Result = TimeoutMissingFormula[T]
        override def apply(): Result = new TimeoutMissingFormula[T](Always(phi, _))
      }

    /** @return a formula where always the result of applying letterToFormula to the
     *         current letter must hold in the next instant
     */
    implicit def fromLetterToFormula[T](letterToFormula: T => Formula[T]): AlwaysMagnet {type Result = TimeoutMissingFormula[T]} =
      new AlwaysMagnet {
        override type Result = TimeoutMissingFormula[T]
        override def apply(): Result = always(next(letterToFormula))
      }

    /** @return a formula where always the result of applying letterToResult to the
     *         current letter must hold now
     */
    implicit def fromLetterToResult[T](letterToResult: T => Result): AlwaysMagnet {type Result = TimeoutMissingFormula[T]} =
      new AlwaysMagnet {
        override type Result = TimeoutMissingFormula[T]
        override def apply(): Result = always(now(letterToResult))
      }

    /** @return a formula where always the result of applying letterToStatus to the
     *         current letter must hold now
     */
    implicit def fromLetterToStatus[T](letterToStatus: T => Prop.Status): AlwaysMagnet {type Result = TimeoutMissingFormula[T]} =
      new AlwaysMagnet {
        override type Result = TimeoutMissingFormula[T]
        override def apply(): Result = always(now(letterToStatus))
      }
  }
}


// using trait for the root of the AGT as recommended in http://twitter.github.io/effectivescala/
sealed trait Formula[T]
  extends Serializable {
  import Formula._

  def safeWordLength: Option[Timeout]
  def nextFormula(implicit par: FormulaParallelism): NextFormula[T]

  // non temporal builder methods
  def unary_! : Formula[T] = Not(this)
  def or(phi2 : Formula[T]): Or[T] = Or(this, phi2)
  def and(phi2 : Formula[T]): And[T] = And(this, phi2)
  def ==>(phi2 : Formula[T]): Implies[T] = Implies(this, phi2)

  // temporal builder methods: next, eventually and always are methods of the Formula companion object
  def until(magnet: UntilMagnet): magnet.Result = magnet(this)
  sealed trait UntilMagnet {
    type Result
    def apply(phi1: Formula[T]): Result
  }
  object UntilMagnet {
    implicit def fromFormula(phi2: Formula[T]): UntilMagnet {type Result = TimeoutMissingFormula[T]} =
      new UntilMagnet {
        override type Result = TimeoutMissingFormula[T]
        override def apply(phi1: Formula[T]): Result = new TimeoutMissingFormula[T](Until(phi1, phi2, _))
      }

    /** @return a formula where this formula happens until the result
     *          of applying letterToFormula to the current letter holds
     *          in the next instant
     */
    implicit def fromLetterToFormula(letterToFormula : T => Formula[T]): UntilMagnet {type Result = TimeoutMissingFormula[T]} =
      new UntilMagnet {
        override type Result = TimeoutMissingFormula[T]
        override def apply(phi1: Formula[T]): Result = phi1.until(next(letterToFormula))
      }

    /** @return a formula where this formula happens until the result
     *          of applying letterToResult to the current letter holds
     */
    implicit def fromLetterToResult(letterToResult : T => Result): UntilMagnet {type Result = TimeoutMissingFormula[T]} =
      new UntilMagnet {
        override type Result = TimeoutMissingFormula[T]
        override def apply(phi1: Formula[T]): Result = phi1.until(now(letterToResult))
      }

    /** @return a formula where this formula happens until the result
     *          of applying letterToStatus to the current letter holds
     */
    implicit def fromLetterToStatus(letterToStatus : T => Prop.Status): UntilMagnet {type Result = TimeoutMissingFormula[T]} =
      new UntilMagnet {
        override type Result = TimeoutMissingFormula[T]
        override def apply(phi1: Formula[T]): Result = phi1.until(now(letterToStatus))
      }
  }

  def release(phi2 : Formula[T]): TimeoutMissingFormula[T] = new TimeoutMissingFormula[T](Release(this, phi2, _))
  /** @return a formula where this formula releases the result
    *          of applying letterToFormula to the current letter from
    *          holding in the next instant
    */
  def release(letterToFormula : T => Formula[T]): TimeoutMissingFormula[T] = this.releaseF(letterToFormula)
  /** @return a formula where this formula releases the result
    *          of applying letterToResult to the current letter from
    *          holding now
    */
  def releaseR(letterToResult : T => Result): TimeoutMissingFormula[T] = this.release(now(letterToResult))
  /** @return a formula where this formula releases the result
    *          of applying letterToStatus to the current letter from
    *          holding now
    */
  def releaseS(letterToStatus : T => Prop.Status): TimeoutMissingFormula[T] =
    this.release(now(letterToStatus))
  /** @return a formula where this formula releases the result
    *          of applying letterToFormula to the current letter from
    *          holding in the next instant
    */
  def releaseF(letterToFormula : T => Formula[T]): TimeoutMissingFormula[T] =
    this.release(next(letterToFormula))
}

/** Restricted class of formulas that are in a form suitable for the
 *  formula evaluation procedure
 */
sealed trait NextFormula[T]
  extends Formula[T] {
  override def nextFormula(implicit par: FormulaParallelism): NextFormula[T] = this

  /** @return Option.Some if this formula is resolved, and Option.None
   *  if it is still pending resolution when some additional values
   *  of type T corresponding to more time instants are provided with
   *  a call to consume()
   * */
  def result : Option[Prop.Status]

  /** @return a new formula resulting from progressing in the evaluation
   *  of this formula by consuming the new values atoms for the atomic
   *  propositions corresponding to the values of the element of the universe
   *  at a new instant of time time. This corresponds to the notion of "letter simplification"
   *  in the paper
   */
  def consume(time: Time)(atoms: T)(implicit par: FormulaParallelism): NextFormula[T]
}

/** Resolved formulas
 * */
object Solved {
  def apply[T](result: Result): Solved[T] = ofResult[T](result)
  // these are needed to resolve ambiguities with apply
  def ofResult[T](result: Result): Solved[T] = new Solved[T](Formula.resultToPropStatus(result))
  def ofStatus[T](status : Prop.Status): Solved[T] = Solved(status)
}
// see https://github.com/rickynils/scalacheck/blob/1.12.2/src/main/scala/org/scalacheck/Prop.scala
case class Solved[T](status : Prop.Status) extends NextFormula[T] {
  override def safeWordLength: Option[Timeout] = Some(Timeout(0))
  override def result: Option[Prop.Status] = Some(status)
  // do no raise an exception in call to consume, because with NextOr we will
  // keep undecided prop values until the rest of the formula in unraveled
  override def consume(time: Time)(atoms : T)
                      (implicit par: FormulaParallelism): NextFormula[T] = this
}

/** This class adds information to the time and atom consumption functions
 *  used  in Now, to enable a partial implementation of safeWordLength
 * */
abstract class TimedAtomsConsumer[T](fun: Time => Function[T, Formula[T]])
  extends Function[Time, Function[T, Formula[T]]]{

  override def apply(time: Time): (T => Formula[T]) = fun(time)
  /** @return false iff fun always returns a Solved formula
   * */
  def returnsDynamicFormula: Boolean
}
class StaticTimedAtomsConsumer[T](fun: Time => Function[T, Formula[T]]) extends TimedAtomsConsumer(fun) {
  override def returnsDynamicFormula = false
}
class DynamicTimedAtomsConsumer[T](fun: Time => Function[T, Formula[T]]) extends TimedAtomsConsumer(fun) {
  override def returnsDynamicFormula = true
}
/** Formulas that have to be resolved now, which correspond to atomic proposition
 *  as functions from the current state of the system to Specs2 assertions.
 *  Note this also includes top / true and bottom / false as constant functions
 */
object BindNext {
  /* For now going for avoiding the type erasure problem, TODO check more sophisticated
   * solutions like http://hacking-scala.org/post/73854628325/advanced-type-constraints-with-type-classes
   * or http://hacking-scala.org/post/73854628325/advanced-type-constraints-with-type-classes based or
   * using ClassTag. Also why is there no conflict with the companion apply?
   */
  def fromAtomsConsumer[T](atomsConsumer: T => Formula[T]): BindNext[T] =
    new BindNext(new DynamicTimedAtomsConsumer(Function.const(atomsConsumer)))
  def fromAtomsTimeConsumer[T](atomsTimeConsumer: (T, Time) => Formula[T]): BindNext[T] =
    new BindNext(new DynamicTimedAtomsConsumer(time => atoms => atomsTimeConsumer(atoms, time)))
  def fromStatusFun[T](atomsToStatus: T => Prop.Status): BindNext[T] =
    new BindNext(new StaticTimedAtomsConsumer[T](Function.const(atomsToStatus andThen Solved.ofStatus _)))
  def fromStatusTimeFun[T](atomsTimeToStatus: (T, Time) => Prop.Status): BindNext[T] =
    new BindNext(new StaticTimedAtomsConsumer(time => atoms => Solved.ofStatus(atomsTimeToStatus(atoms, time))))
  def apply[T, R](atomsToResult: T => R)(implicit ev: R => Result): BindNext[T] =
    new BindNext(new StaticTimedAtomsConsumer[T](
      Function.const(atomsToResult andThen implicitly[Function[R,Result]] andThen Solved.ofResult _)))
  def apply[T, R](atomsTimeToResult: (T, Time) => R)(implicit ev: R => Result): BindNext[T] =
    new BindNext(new StaticTimedAtomsConsumer(time => atoms => Solved.ofResult(atomsTimeToResult(atoms, time))))
}

case class BindNext[T](timedAtomsConsumer: TimedAtomsConsumer[T])
  extends NextFormula[T] {
  /* Note the case class structural equality gives an implementation
   * for equals equivalent to the one below, as a Function is only
   * equal to references to the same function, which corresponds to 
   * intensional function equality. That is the only thing that makes 
   * sense if we don't have a deduction system that is able to check 
   * extensional function equality  
   *
  override def equals(other : Any) : Boolean = 
    other match {
      case that : BindNext[T] => that eq this
      case _ => false
    }
    *    
    */
  // we cannot fully compute this statically, because the returned
  // formula depends on the input word, but TimedAtomsConsumer.returnsDynamicFormula
  // allows us to build a safe approximation 
  override def safeWordLength: Option[Timeout] =
    (!timedAtomsConsumer.returnsDynamicFormula) option Timeout(1)
  override def result: Option[Prop.Status] = None
  override def consume(time: Time)(atoms: T)(implicit par: FormulaParallelism): NextFormula[T] =
    timedAtomsConsumer(time)(atoms).nextFormula 
}
case class Not[T](phi : Formula[T]) extends Formula[T] {
  override def safeWordLength: Option[Timeout] = phi safeWordLength
  override def nextFormula(implicit par: FormulaParallelism): NextFormula[T] =
    new NextNot(phi.nextFormula)
}

class NextNot[T](phi : NextFormula[T]) extends Not[T](phi) with NextFormula[T] {
  override def result: Option[Prop.Status] = None
  /** Note in the implementation of or we add Exception to the truth lattice, 
  * which always absorbs other values to signal a test evaluation error
  * */
  override def consume(time: Time)(atoms : T)(implicit par: FormulaParallelism): NextFormula[T] = {
    val phiConsumed = phi.consume(time)(atoms)
    phiConsumed.result match {
      case Some(res) => 
        Solved (res match {
          /* Prop.True is the same as passed, see lazy val passed 
             * at https://github.com/rickynils/scalacheck/blob/1.12.2/src/main/scala/org/scalacheck/Prop.scala
             * TODO: use Prop.Proof instead?
             */
          case Prop.True => Prop.False
          case Prop.Proof => Prop.False
          case Prop.False => Prop.True 
          case Prop.Exception(_) => res 
          case Prop.Undecided => Prop.Undecided
        })
      case None => new NextNot(phiConsumed)
    }
  }
}

case class Or[T](phis : Formula[T]*) extends Formula[T] {
  override def safeWordLength: Option[Timeout] =
    phis.map(_.safeWordLength)
        .toList.sequence
        .map(_.maxBy(_.instants))
  
  override def nextFormula(implicit par: FormulaParallelism): NextFormula[T] =
    NextOr(phis.map(_.nextFormula):_*)
}
object NextOr {
  def apply[T](phis: NextFormula[T]*)(implicit par: FormulaParallelism): NextFormula[T] =
    if (phis.length == 1) phis(0)
    else new NextOr(FormulaParallelism.par[T](par, phis))
}
class NextOr[T](phis: ParSeq[NextFormula[T]]) extends NextBinaryOp[T](phis) {
  /** @return the result of computing the or of s1 and s2 in 
   *  the lattice of truth values, adding Exception which always
   *  absorbs other values to signal a test evaluation error
   */
  override def apply(s1: Prop.Status, s2: Prop.Status): Prop.Status = 
    (s1, s2) match {
      case (Prop.Exception(_), _) => s1
      case (_, Prop.Exception(_)) => s2
      case (Prop.True, _) => Prop.True
      case (Prop.Proof, _) => Prop.Proof
      case (Prop.Undecided, Prop.False) => Prop.Undecided
      case _ => s2
    }  
  override protected def build(phis: ParSeq[NextFormula[T]]) =
    new NextOr(phis)

  override protected def isSolverStatus(status: Prop.Status): Boolean =
    (status == Prop.True) || (status == Prop.Proof)
}

case class And[T](phis : Formula[T]*) extends Formula[T] {
  override def safeWordLength: Option[Timeout] =
    phis.map(_.safeWordLength)
        .toList.sequence
        .map(_.maxBy(_.instants))
  override def nextFormula(implicit par: FormulaParallelism): NextFormula[T] =
    NextAnd(phis.map(_.nextFormula):_*)
}
object NextAnd {
  def apply[T](phis: NextFormula[T]*)(implicit par: FormulaParallelism): NextFormula[T] =
    if (phis.length == 1) phis(0)
    else new NextAnd(FormulaParallelism.par[T](par, phis))
}
class NextAnd[T](phis: ParSeq[NextFormula[T]]) extends NextBinaryOp[T](phis) {
  /** @return the result of computing the and of s1 and s2 in
   *  the lattice of truth values
   */
  override def apply(s1: Prop.Status, s2: Prop.Status): Prop.Status =
    (s1, s2) match {
      case (Prop.Exception(_), _) => s1
      case (_, Prop.Exception(_)) => s2
      case (Prop.False, _) => Prop.False
      case (Prop.Undecided, Prop.False) => Prop.False
      case (Prop.Undecided, _) => Prop.Undecided
      case (Prop.True, _) => s2
      case (Prop.Proof, _) => s2
    }
  override protected def build(phis: ParSeq[NextFormula[T]]) =
    new NextAnd(phis)

  override protected def isSolverStatus(status: Prop.Status): Boolean =
    status == Prop.False
}

object FormulaParallelism {
  def par[T](formulaParallelism: FormulaParallelism,
             seqPhis: Seq[NextFormula[T]]): ParSeq[NextFormula[T]] =
    formulaParallelism match {
      case TaskSupportFormulaParallelism(taskSupport) =>
        val parPhis = seqPhis.par
        parPhis.tasksupport = taskSupport
        parPhis
      case SequentialFormulaParallelism => 
        throw new NotImplementedError("SequentialFormulaParallelism not implemented after migration to Scala 2.13")
    }
}
/** If an implicit value of this type is available then formulas
  * are parallelized according to it. Otherwise [[https://docs.scala-lang.org/overviews/parallel-collections/overview.html parallel collections]]
  * with the default TaskSupport are used
  * */
sealed trait FormulaParallelism
/** Or and And operators arguments are parallelized as parallel collections with the specified task support. This means
  * we can e.g. launch actions concurrently from the driver to a Spark runtime.
  * */
case class TaskSupportFormulaParallelism(taskSupport: TaskSupport) extends FormulaParallelism
/** There is no parallelism in the formulas (but we still employ the parallel computing features of the distributed
  * engine that is used). */
object SequentialFormulaParallelism extends FormulaParallelism

/** Abstract the functionality of NextAnd and NextOr, which are binary
 *  boolean operators that apply to a collection of formulas with a reduce()
 *  */
abstract class NextBinaryOp[T](phis: ParSeq[NextFormula[T]])
  extends Function2[Prop.Status, Prop.Status, Prop.Status] 
  with NextFormula[T] {
  
  // TODO: consider replacing by getting the companion of the concrete subclass 
  // following http://stackoverflow.com/questions/9172775/get-companion-object-of-class-by-given-generic-type-scala, a
  // or something in the line of scala.collection.generic.GenericCompanion (used e.g. in Seq.companion()),
  // and then calling apply to build  
  protected def build(phis: ParSeq[NextFormula[T]]): NextFormula[T]
  
  /* return true if status solves this operator: e.g. Prop.True
   * or  Prop.Proof resolve and or without evaluating anything else, 
   * otherwise return false */
  protected def isSolverStatus(status: Prop.Status): Boolean
  
  override def safeWordLength: Option[Timeout] =
    phis.map(_.safeWordLength)
        .toList.sequence
        .map(_.maxBy(_.instants))
  override def result: Option[Prop.Status] = None
  override def consume(time: Time)(atoms : T)(implicit par: FormulaParallelism): NextFormula[T] = {
    val (phisDefined, phisUndefined) = phis
      .map { _.consume(time)(atoms) }
      .partition { _.result.isDefined }     
    val definedStatus = phisDefined.nonEmpty option {
      phisDefined
      .map { _.result.get }
      .reduce { apply(_, _) }
      }  
    // short-circuit operator if possible. Note an edge case when all the phis
    // are defined after consuming the input, but we might still not have a
    // positive (true of proof) result
    if (definedStatus.isDefined && definedStatus.get.isInstanceOf[Prop.Exception])
      Solved(definedStatus.get)
    else if ((definedStatus.isDefined && isSolverStatus(definedStatus.get)) 
             || phisUndefined.isEmpty) {
      Solved(definedStatus.getOrElse(Prop.Undecided))
    } else {
      // if definedStatus is undecided keep it in case 
      // the rest of the and is reduced to true later on
      val newPhis = definedStatus match {
        case Some(Prop.Undecided) => Solved[T](Prop.Undecided) +: phisUndefined
        case _ => phisUndefined
      }
      build(newPhis)
    }
  }
}

case class Implies[T](phi1 : Formula[T], phi2 : Formula[T]) extends Formula[T] {
  override def safeWordLength: Option[Timeout] = for {
    safeLength1 <- phi1.safeWordLength
    safeLength2 <- phi2.safeWordLength 
  } yield safeLength1 max safeLength2
  
  override def nextFormula(implicit par: FormulaParallelism): NextFormula[T] =
    NextOr(new NextNot(phi1.nextFormula), phi2.nextFormula)
}

case class Next[T](phi : Formula[T]) extends Formula[T] {
  import Formula.intToTimeout
  override def safeWordLength: Option[Timeout] = phi.safeWordLength.map(_ + 1)
  override def nextFormula(implicit par: FormulaParallelism): NextFormula[T] =
    NextNext(phi.nextFormula)
}
object NextNext {
  def apply[T](phi: => NextFormula[T]): NextFormula[T] = new NextNext(phi)
}
class NextNext[T](_phi: => NextFormula[T]) extends NextFormula[T] {
  import Formula.intToTimeout

  private lazy val phi = _phi  
  override def safeWordLength: Option[Timeout] = phi.safeWordLength.map(_ + 1)

  override def result: Option[Prop.Status] = None
  override def consume(time: Time)(atoms : T)(implicit par: FormulaParallelism): NextFormula[T] = phi
}

case class Eventually[T](phi : Formula[T], t : Timeout) extends Formula[T] {
  require(t.instants >=1, s"timeout must be greater or equal than 1, found ${t}")
  
  import Formula.intToTimeout
  override def safeWordLength: Option[Timeout] = phi.safeWordLength.map(_ + t - 1)
  
  override def nextFormula(implicit par: FormulaParallelism): NextFormula[T] = {
    val nextPhi = phi.nextFormula
    if (t.instants <= 1) nextPhi 
    // equivalent to paper formula assuming nt(C[phi]) = nt(C[nt(phi)]) 
    else NextOr(nextPhi, NextNext(Eventually(nextPhi, t-1).nextFormula))
  }
}
case class Always[T](phi : Formula[T], t : Timeout) extends Formula[T] {
  require(t.instants >=1, s"timeout must be greater or equal than 1, found ${t}")
  
  import Formula.intToTimeout
  override def safeWordLength: Option[Timeout] = phi.safeWordLength.map(_ + t - 1)
  override def nextFormula(implicit par: FormulaParallelism): NextFormula[T] = {
    val nextPhi = phi.nextFormula
    if (t.instants <= 1) nextPhi 
    // equivalent to paper formula assuming nt(C[phi]) = nt(C[nt(phi)]) 
    else NextAnd(nextPhi, NextNext(Always(nextPhi, t-1).nextFormula))
  }
}
case class Until[T](phi1 : Formula[T], phi2 : Formula[T], t : Timeout) extends Formula[T] {
  require(t.instants >=1, s"timeout must be greater or equal than 1, found ${t}")
  
  import Formula.intToTimeout
  override def safeWordLength: Option[Timeout] = for {
    safeLength1 <- phi1.safeWordLength
    safeLength2 <- phi2.safeWordLength 
  } yield (safeLength1 max safeLength2) + t -1 
    
  override def nextFormula(implicit par: FormulaParallelism): NextFormula[T] = {
    val (nextPhi1, nextPhi2) = (phi1.nextFormula, phi2.nextFormula)
    if (t.instants <= 1) nextPhi2
    // equivalent to paper formula assuming nt(C[phi]) = nt(C[nt(phi)]) 
    else NextOr(nextPhi2, 
                NextAnd(nextPhi1, NextNext(Until(nextPhi1, nextPhi2, t-1).nextFormula)))      
  }
}
case class Release[T](phi1 : Formula[T], phi2 : Formula[T], t : Timeout) extends Formula[T] {
  require(t.instants >=1, s"timeout must be greater or equal than 1, found ${t}")
  
  import Formula.intToTimeout
  override def safeWordLength: Option[Timeout] = for {
    safeLength1 <- phi1.safeWordLength
    safeLength2 <- phi2.safeWordLength 
  } yield (safeLength1 max safeLength2) + t -1
  
  override def nextFormula(implicit par: FormulaParallelism): NextFormula[T] = {
    val (nextPhi1, nextPhi2) = (phi1.nextFormula, phi2.nextFormula)
    if (t.instants <= 1) NextAnd(nextPhi1, nextPhi2)
    // equivalent to paper formula assuming nt(C[phi]) = nt(C[nt(phi)]) 
    else NextOr(NextAnd(nextPhi1, nextPhi2), 
                NextAnd(nextPhi2, NextNext(Release(nextPhi1, nextPhi2, t-1).nextFormula)))      

  }
}
 
/** Case class wrapping the number of instants / turns a temporal
 *  operator has to be resolved (e.g. an the formula in an 
 *  eventually happening).
 *   - A timeout of 1 means the operator has to be resolved now.
 *   - A timeout of 0 means the operator fails to be resolved  
 *  
 *  A type different to Int allows us to be more specified when requiring
 *  implicit parameters in functions
 * */
case class Timeout(val instants : Int) extends Serializable { 
  def +[T](t : T)(implicit ev: T => Timeout) = Timeout { instants + t.instants }
  def -[T](t : T)(implicit ev: T => Timeout) = Timeout { instants -  t.instants }
  def max[T](t : T)(implicit ev: T => Timeout) = Timeout { math.max(instants, t.instants) }
}

/** This class is used in the builder methods in Formula and companion, 
 *  to express formulas with a timeout operator at root that is missing 
 *  its timeout, wich can be provided to complete the formula with the on() method  
 */
class TimeoutMissingFormula[T](val toFormula : Timeout => Formula[T]) 
  extends Serializable {
  
  def on(t : Timeout): Formula[T] = toFormula(t)
  /** Alias of on that can be used for obtaining a more readable spec, for
   *  example combined with Formula.always()
   */
  def during(t : Timeout): Formula[T] = on(t)
}

/** @param millis: number of milliseconds since January 1, 1970 UTC
 * */
case class Time(millis: Long) extends Serializable
