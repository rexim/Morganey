package me.rexim.morganey

import jline.console.ConsoleReader
import me.rexim.morganey.MorganeyInterpreter.{evalOneNodeComputation, evalOneNode, readNodes}
import me.rexim.morganey.ReplHelper.smartPrintTerm
import me.rexim.morganey.ast._
import me.rexim.morganey.reduction.Computation
import me.rexim.morganey.syntax.LambdaParser
import sun.misc.{Signal, SignalHandler}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object Main extends SignalHandler {

  override def handle(signal: Signal): Unit = {
    currentComputation.foreach(_.cancel())
  }

  var currentComputation: Option[Computation[MorganeyEval]] = None

  def awaitComputationResult(computation: Computation[MorganeyEval]): Try[MorganeyEval] = {
    try {
      currentComputation = Some(computation)
      Try(Await.result(computation.future, Duration.Inf))
    } finally {
      currentComputation = None
    }
  }

  def exitRepl() = {
    System.exit(0)
  }

  def handleLine(con: ConsoleReader)(globalContext: List[MorganeyBinding], line: String): Option[List[MorganeyBinding]] = {
    import LambdaParser.{parse, replCommand}
    val nodeParseResult = parse(replCommand, line)

    if (nodeParseResult.successful) {
      val node = nodeParseResult.get
      val computation = evalOneNodeComputation(node)(globalContext)

      awaitComputationResult(computation) match {
        case Success(MorganeyEval(context, result)) =>
          result.foreach(t => con.println(smartPrintTerm(t)))
          Some(context)
        case Failure(e) =>
          con.println(e.getMessage)
          None
      }
    } else {
      con.println(nodeParseResult.toString)
      None
    }
  }

  def startRepl() = {
    Signal.handle(new Signal("INT"), this)

    val running = true
    var globalContext = List[MorganeyBinding]()
    val con = new ConsoleReader()
    con.setPrompt("λ> ")

    def line() = Option(con.readLine()).map(_.trim)

    val evalLine = handleLine(con) _

    while (running) line() match {
      case None         => exitRepl() // eof
      case Some("")     => ()
      case Some("exit") => exitRepl()
      case Some(line)   => evalLine(globalContext, line) foreach { context =>
        globalContext = context
      }
    }
  }

  def evalAndPrintNextNode(previousEval: MorganeyEval, nextNode: MorganeyNode): MorganeyEval = {
    val nextEval = previousEval.flatMap(evalOneNode(nextNode))
    nextEval.result.foreach(term => println(smartPrintTerm(term)))
    nextEval
  }

  def evalAllNodes(initialEval: MorganeyEval)(nodes: List[MorganeyNode]): MorganeyEval = {
    nodes.foldLeft(initialEval)(evalAndPrintNextNode)
  }

  def evalFile(fileName: String)(eval: MorganeyEval): Try[MorganeyEval] = {
    readNodes(fileName).map(evalAllNodes(eval))
  }

  def main(args: Array[String]) = {
    if (args.isEmpty) {
      startRepl()
    } else {
      args.toStream.foldLeft[Try[MorganeyEval]](Success(MorganeyEval())) { (evalTry, fileName) =>
        evalTry.flatMap(evalFile(fileName))
      } match {
        case Failure(e) => println(s"[ERROR] ${e.getMessage}")
        case _ =>
      }
    }
  }
}
