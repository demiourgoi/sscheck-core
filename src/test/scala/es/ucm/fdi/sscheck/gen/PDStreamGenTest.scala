package es.ucm.fdi.sscheck.gen

import org.scalacheck.{Properties, Gen}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Prop.{forAll, exists, AnyOperators, collect}
// import Buildables.buildableSeq
import Buildables._
import org.scalatest._
import org.scalatest.Matchers._
import org.scalatest.prop.PropertyChecks._
import org.scalatest.Inspectors.{forAll => testForAll}
import PDStreamGenConversions._

/*
 * NOTE the use of the import alias org.scalatest.Inspectors.{forAll => testForAll} to
 * distinguish between ScalaTest forAll inspector used in matchers, and ScalaTest
 * forAll adapter for ScalaCheck 
 * */

object PDStreamGenTest extends Properties("Properties class PDStreamGen") {  
  property("""dstreamUnion() should respect expected batch sizes""") = {
    // using small lists as we'll use Prop.exists
    val (batchMaxSize, dstreamMaxSize) = (100, 20)
    forAll ("batchSize1" |: Gen.choose(0, batchMaxSize), 
            "batchSize2" |: Gen.choose(0, batchMaxSize),
            "dstreamSize1" |: Gen.choose(0, dstreamMaxSize),
            "dstreamSize2" |: Gen.choose(0, dstreamMaxSize)) 
            { (batchSize1 : Int, batchSize2 : Int, dstreamSize1: Int, dstreamSize2: Int) => 
      def batchGen1 : Gen[Batch[Int]] = BatchGen.ofN(batchSize1, arbitrary[Int])
      def dstreamGen1 : Gen[PDStream[Int]] = PDStreamGen.ofN(dstreamSize1, batchGen1)
      def batchGen2 : Gen[Batch[Int]] = BatchGen.ofN(batchSize2, arbitrary[Int])
      def dstreamGen2 : Gen[PDStream[Int]] = PDStreamGen.ofN(dstreamSize2, batchGen2)
      val (gs1, gs2) = (dstreamGen1, dstreamGen2) 
      forAll ("dsUnion" |: gs1  + gs2) { (dsUnion : PDStream[Int]) =>
        collect (s"batchSize1=${batchSize1}, batchSize2=${batchSize2}, dstreamSize1=${dstreamSize1}, dstreamSize2=${dstreamSize2}") {
          dsUnion should have length (math.max(dstreamSize1, dstreamSize2))
          // if no batch is generated then the effective batch size is 0
          def effectiveBatchSize(dstreamSize : Int, batchSize : Int) = 
            if (dstreamSize <= 0) 0 else batchSize
          val (effectiveBatchSize1, effectiveBatchSize2) = 
            (effectiveBatchSize(dstreamSize1, batchSize1), effectiveBatchSize(dstreamSize2, batchSize2))
          // in general batches don't have the effectiveBatchSize1 + effectiveBatchSize2 size
          // because the smallest dstream is filled with empty batches
          testForAll (dsUnion : Seq[Batch[Int]]) {
            (batch : Batch[Int]) =>  batch.length should be <= (effectiveBatchSize1 + effectiveBatchSize2)
          }
          // but the batches in the prefix where both dstreams have not empty batches should
          // have a size of exactly effectiveBatchSize1 + effectiveBatchSize2 
          testForAll (dsUnion.slice(0, math.min(dstreamSize1, dstreamSize2)) : Seq[Batch[Int]]) {
            _ should have length (effectiveBatchSize1 + effectiveBatchSize2)
          }
          true // need to finish like that when using ScalaCheck matchers
        }
      }
    }
  }
    
  /* This property is removed because the example proving it
   * is almost never found, and this causes a failure of 
   * the automatic tests
   * 
   * property("""dstreamUnion() generates batches as the union of the batches generated by its inputs
(weak, existential)""") = {
    // using small lists as we'll use Prop.exists
    val (batchMaxSize, dstreamMaxSize) = (2, 1)
    def batchGen : Gen[Batch[Int]] = BatchGen.ofNtoM(0, batchMaxSize, arbitrary[Int]) 
    def dstreamGen : Gen[DStream[Int]] = DStreamGen.ofNtoM(0, dstreamMaxSize, batchGen)
    val (gs1, gs2) = (dstreamGen, dstreamGen)
    
    forAll ("dsUnion" |: dstreamUnion(gs1, gs2)) { (dsUnion : DStream[Int]) =>
      // dsUnion.length should be (1)
      exists ("gsi zip" |: Gen.zip(gs1, gs2)) { (ds12 : (DStream[Int], DStream[Int])) =>
       // dsUnion should be (ds12._1 ++ ds12._2): this raises an exception on the first failure 
       // => not the existential behaviour. TODO: experiment wrapping in Try and map to Boolean
        dsUnion ?= ds12._1 ++ ds12._2
      }
    }
  } */
  
}
  
