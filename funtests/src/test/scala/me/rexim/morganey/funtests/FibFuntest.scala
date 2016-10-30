package me.rexim.morganey.funtests

import java.io.File

import org.scalatest._
import scala.sys.process._

// TODO: merge with SamplesFuntest
class FibFuntest extends FlatSpec with Matchers with MorganeyProcess {
  "15 first fibonacci numbers sample" should "print 15 first fibonacci numbers" in {
    val expectedOutput = "numbers: [0,1,1,2,3,5,8,13,21,34,55,89,144,233,377]\n"
    val actualOutput = morganey("./docs/samples/fib.mgn").!!
    actualOutput should be (expectedOutput)
  }
}
