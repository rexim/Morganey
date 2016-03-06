package me.rexim.morganey.reduction

import java.util.concurrent.FutureTask

import me.rexim.morganey.ast.{LambdaApp, LambdaFunc, LambdaTerm}
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

object NormalOrder {
  import CallByName._

  implicit class NormalOrderStrategy(val term: LambdaTerm) {

    def norReduce(): LambdaTerm = {
      var result = term
      while (!result.norIsFinished()) {
        result = result.norStepReduce()
      }
      result
    }

    def norReduceCancellable(): (Future[LambdaTerm], () => Unit) = {
      @volatile var cancelled = false
      val computation = Future {
        var result = term

        while (!cancelled && !result.norIsFinished()) {
          result = result.norStepReduce()
        }

        if (cancelled) {
          throw new ComputationCancelledException()
        }

        result
      }
      val cancel = () => cancelled = true

      (computation, cancel)
    }

    def norStepReduce(): LambdaTerm = term match {
      case LambdaApp(LambdaFunc(x, t), r) => t.substitute(x -> r)
      case LambdaApp(l, r) if !l.cbnIsFinished() => LambdaApp(l.cbnStepReduce(), r)
      case LambdaApp(l, r) if !l.norIsFinished() => LambdaApp(l.norStepReduce(), r)
      case LambdaApp(l, r) if !r.norIsFinished() => LambdaApp(l, r.norStepReduce())
      case LambdaFunc(x, t) => LambdaFunc(x, t.norStepReduce())
      case other => other
    }

    def norIsFinished(): Boolean = term match {
      case LambdaApp(LambdaFunc(_, _), _) => false
      case LambdaApp(l, r) => l.norIsFinished() && r.norIsFinished()
      case LambdaFunc(_, t) => t.norIsFinished()
      case _ => true
    }
  }
}
