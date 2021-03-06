package es.ucm.fdi.sscheck.gen

import org.scalacheck.{Properties, Gen}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Prop.{forAll, exists, AnyOperators, collect}
import org.scalatest._
import org.scalatest.Matchers._
import org.scalatest.prop.PropertyChecks._
import org.scalatest.Inspectors.{forAll => testForAll}
import Window.seq2batch
import BatchGenConversions._
import PStreamGen._
import Buildables.{buildableBatch, buildablePDStreamFromBatch}
import PStreamMatchers._
import es.ucm.fdi.sscheck.prop.UtilsProp

/** Tests for the LTL inspired HO generators defined at BatchGen and DStreamGen 
 *  
 * WARNING: due to using nested forall, shrinking might generate wrong counterexamples, 
 * so the _ORIGINAL versions should be used in case of test failure 
 * 
 * Note most tests check completeness of the generators, but not correctness, i.e. that
 * all the generated data corresponds to some input data. We could do that but logically
 * it makes sense the interpreting the TL formulas as requiring something to happen, but
 * not requiring that something doesn't happen, has we don't have negation.  
 * */
object TLGenTest extends Properties("TLGen temporal logic generators properties") {
  /*
   * NOTE the use of the import alias org.scalatest.Inspectors.{forAll => testForAll} to
   * distinguish between ScalaTest forAll inspector used in matchers, and ScalaTest
   * forAll adapter for ScalaCheck 
   * */  
  // 
  // Tests for BatchGen LTL inspired HO generators
  // 
  property("BatchGen.now() applied to a batch generator returns a dstream generator " +  
      "with exactly one batch, and doesn't introduce new elements") =
    forAll ("batch" |: arbitrary[Window[Int]]) { batch : Window[Int] =>
      // note here we are dropping some elements
      val g = WindowGen.now(Gen.someOf(batch))
      forAll ("dstream" |: g) { dstream : PStream[Int] =>
        dstream should have length (1)
        dstream(0).length should be <= (batch.length) 
        testForAll (dstream(0)) { batch should contain (_)}
        true
      }
    }
  
  property("BatchGen.now() applied to a constant batch generator returns a dstream generator " +
      "with exactly that batch as the only batch")  = 
    forAll ("batch" |: arbitrary[Window[Int]]) { batch : Window[Int] =>
      // using a constant generator
      val g = WindowGen.now(batch : Window[Int])
      forAll ("dstream" |: g) { dstream : PStream[Int] =>
        dstream should have length (1)
        dstream(0) should be (batch)
        true
      }
    }
  
  property("BatchGen.next() returns a dstream generator that has exactly two batches, " + 
       "the first one is emptyBatch, and the second one is its argument") = 
    forAll ("batch" |: arbitrary[Window[Int]]) { batch : Window[Int] =>
      // using a constant generator
      val g = WindowGen.next(batch : Window[Int])
      forAll ("dstream" |: g) { dstream : PStream[Int] =>
        dstream should have length (2)
        dstream(0) should be (Window.empty)
        dstream(1) should be (batch)
        true
      }
    }  
  
   property("BatchGen.laterN(n, bg) generates n batches and then bg" ) =
    forAll ("batch" |: arbitrary[Window[Int]], "n" |: Gen.choose(-10, 30))  {
      (batch : Window[Int], n : Int) =>
        // using a constant generator
        val g = WindowGen.laterN(n, batch)
        forAll ("nextDStream" |: g) { nextDStream : PStream[Int] =>
          nextDStream should have length (math.max(0, n) + 1)
          testForAll (nextDStream.slice(0, n)) {_ should be (Window.empty)}
          nextDStream(nextDStream.length-1) should be (batch)
          true  
        }
    }
  
  property("BatchGen.until is a strong until, i.e. the second generator always occurs, and " +
      "the first one occours before")  =
    forAll ("batch1" |: arbitrary[Window[Int]], "batch2" |: arbitrary[Window[Int]]) {
      (batch1 : Window[Int], batch2 : Window[Int]) =>
        // using constant generators
        val g = WindowGen.until(batch1, batch2)
        forAll ("untilDStream" |: g) { untilDStream : PStream[Int] =>
          testForAll (untilDStream.slice(0, untilDStream.length-1)) { _ should be (batch1)}
          if (untilDStream.length >0)
            untilDStream(untilDStream.length-1) should be (batch2)
          true
        }
    }

  property("BatchGen.eventually eventually produces data from the " + 
      "argument batch generator") = 
    forAll ("batch" |: arbitrary[Window[Int]]) { batch : Window[Int] =>
      forAll ("eventuallyDStream" |: WindowGen.eventually(batch)) { eventuallyDStream : PStream[Int] =>
        eventuallyDStream(eventuallyDStream.length-1) should be (batch)
        true
      }
    }
  
  property("BatchGen.always always produces data from the argument batch generator") = 
    forAll ("batch" |: arbitrary[Window[Int]]) { batch : Window[Int] =>
      forAll ("alwaysDStream" |: WindowGen.always(batch)) { alwaysDStream : PStream[Int] =>
        testForAll (alwaysDStream.toList) {_ should be (batch)}
        true
      }
    }
  
  property("BatchGen.release is a weak relase, i.e either bg2 happens forever, " + 
      "or it happens until bg1 happens, including the  moment when bg1 happens")  =
    forAll ("batch1" |: arbitrary[Window[Int]], "batch2" |: arbitrary[Window[Int]]) {
      (batch1 : Window[Int], batch2 : Window[Int]) =>
        // using constant generators
        val g = WindowGen.release(batch1, batch2)
        forAll ("releaseDStream" |: g) { releaseDStream : PStream[Int] =>
          testForAll (releaseDStream.slice(0, releaseDStream.length-2)) { _ should be (batch2)}
          if (releaseDStream.length >0)
            releaseDStream(releaseDStream.length-1) should  
              (be (batch1 ++ batch2) or be (batch2))
          true
        }
    }
  
  // 
  // Tests for DStreamGen LTL inspired HO generators
  //
  // small DStream generator for costly tests
  val smallDsg =  PStreamGen.ofNtoM(0, 10, WindowGen.ofNtoM(0, 5, arbitrary[Int]))
  
  property("DStreamGen.next() returns a dstream generator that has exactly 1 + the " +
      "number of batches of its argument, the first one is emptyBatch, and the rest " + 
      "are the batches generated by its argument") = 
    forAll ("dstream" |: arbitrary[PStream[Int]])  { dstream : PStream[Int] =>
      // using a constant generator
      val g = PStreamGen.next(dstream)
      forAll ("nextDStream" |: g) { nextDStream : PStream[Int] =>
        nextDStream should have length (1 + dstream.length)
        nextDStream(0) should be (Window.empty)
        nextDStream.slice(1, nextDStream.size) should be (dstream)
        true
      } 
    }
  
  property("DStreamGen.laterN(n, dsg) generates n batches and then dsg" ) =
    forAll ("dstream" |: arbitrary[PStream[Int]], "n" |: Gen.choose(-10, 30))  {
      (dstream : PStream[Int], n : Int) =>
        // using a constant generator
        val g = PStreamGen.laterN(n, dstream)
        forAll ("nextDStream" |: g) { nextDStream : PStream[Int] =>
          nextDStream should have length (math.max(0, n) + dstream.length)
          testForAll (nextDStream.slice(0, n)) {_ should be (Window.empty)}
          nextDStream.slice(n, nextDStream.length) should be (dstream.toList)
          true  
        }
    }
  
  property("DStreamGen.until is a strong until, i.e. the second generator always occurs, " +
      "and the first one occours before") = {
    // explicitly limiting generator sizes to avoid too slow tests
    forAll ("dstream1" |: smallDsg, "dstream2" |: smallDsg) { (dstream1 : PStream[Int], dstream2 : PStream[Int]) =>
        // using constant generators
        val (dstream1Len, dstream2Len) = (dstream1.length, dstream2.length)
        val g = PStreamGen.until(dstream1, dstream2)
        forAll ("untilDStream" |: g) { untilDStream : PStream[Int] =>
          for {i <- 0 until untilDStream.length - dstream1Len - dstream2Len} {
            dstream1 should beSubsetOf (untilDStream.slice(i, i + dstream1Len))
          }
          // FIXME esto falla si el phi es mas largo que phi2, pq se descuenta mal
          if (dstream1Len <= dstream2Len) {
            val tail = untilDStream.slice(untilDStream.length - dstream2Len, untilDStream.length)
            dstream2 should beSubsetOf(tail)
          } 
            /* else  there is no reference for the start of dstream2 in untilDStream, 
             * for example: 
             * 
> dstream1: PDStream(Batch(-1, -1, 285357432, -2147483648, 1268745804), Bat
  ch(), Batch(), Batch(-13643088), Batch(591481111, -1926229852, 2147483647
  , 1, -732424421), Batch(0, -1), Batch(0, -1936654354, -1, 44866036), Batc
  h(1983936151))
> dstream2: PDStream(Batch(1, 1), Batch())
> untilDStream: PDStream(Batch(-1, -1, 285357432, -2147483648, 1268745804),
   Batch(1, 1), Batch(), Batch(-13643088), Batch(591481111, -1926229852, 21
  47483647, 1, -732424421), Batch(0, -1), Batch(0, -1936654354, -1, 4486603
  6), Batch(1983936151)) 
             */          
          true 
        }
    }
  }
   
  property("DStreamGen.eventually eventually produces data from the argument generator") = 
    forAll ("dstream" |: arbitrary[PStream[Int]]) { dstream : PStream[Int] =>
      forAll ("eventuallyDStream" |: PStreamGen.eventually(dstream)) { eventuallyDStream : PStream[Int] =>
        val eventuallyDStreamLen = eventuallyDStream.length
        val ending = eventuallyDStream.slice(eventuallyDStreamLen - dstream.length, eventuallyDStreamLen) 
        ending should be (dstream)
        true
      }
    }
  
  property("DStreamGen.always always produces data from the argument generator") =
    // explicitly limiting generator sizes to avoid too slow tests
    forAll (smallDsg) { dstream : PStream[Int] =>
      val dstreamLen = dstream.length
      forAll ("alwaysDStream" |: PStreamGen.always(dstream)) { alwaysDStream : PStream[Int] =>
        for {i <- 0 until alwaysDStream.length - dstreamLen} {
           dstream should beSubsetOf (alwaysDStream.slice(i, i + dstreamLen))
        }
        true
      }
    }
   
   property("DStreamGen.release is a weak relase, i.e either the second generator happens forever, " + 
      "or it happens until the first generator happens, including the  moment when the first generator happens")  =
     {
    // explicitly limiting generator sizes to avoid too slow tests
    forAll ("dstream1" |: smallDsg, "dstream2" |: smallDsg) { 
      (dstream1 : PStream[Int], dstream2 : PStream[Int]) =>
        // using constant generators
        val (dstream1Len, dstream2Len) = (dstream1.length, dstream2.length)
        val g = PStreamGen.release(dstream1, dstream2)
        forAll ("releaseDStream" |: g) { releaseDStream : PStream[Int] =>
          // this is similar to always, but note the use of max to account for
          // the case when dstream1 happens and dstream1 is longer than dstream2
          // We don't check if dstream1 happens, because it might not
          for {i <- 0 until releaseDStream.length - math.max(dstream1Len, dstream2Len)} {
           dstream2 should beSubsetOf (releaseDStream.slice(i, i + dstream2Len))
          }
          true 
        }
    }
  }
         
}