package me.rexim.morganey.syntax

import me.rexim.morganey.ast._
import me.rexim.morganey.church.{ChurchNumberConverter, ChurchPairConverter}
import me.rexim.morganey.util._
import me.rexim.morganey.syntax.Language._

import scala.collection.immutable.NumericRange
import scala.util.parsing.combinator._
import scala.language.postfixOps

object IntMatcher {
  def unapply(rawInt: String): Option[Int] =
    try {
      Some(Integer.parseInt(rawInt))
    } catch {
      case e: NumberFormatException => None
    }
}

object LambdaParser extends LambdaParser

class LambdaParser extends JavaTokenParsers with ImplicitConversions {

  /* comment-regex taken from: http://stackoverflow.com/a/5954831 */
  protected override val whiteSpace = whiteSpacePattern.r

  def variable: Parser[LambdaVar] =
    identifier.r ^^ { LambdaVar }

  def validNumberLiteral: Parser[Int] =
    numberLiteral.r ^? ({
      case IntMatcher(x) => x
    }, { (rawInt) => s"`$rawInt' is too big"})

  def numericLiteral: Parser[LambdaTerm] =
    validNumberLiteral ^^ ChurchNumberConverter.encodeNumber

  def validCharacterLiteral: Parser[Char] = (
    escapedCharLiteral.r ^^ { s =>
      escapeSequences(s charAt 2)
    }
    | symbolCharLiteral.r ^^ { _ charAt 1 }
  )

  def characterLiteral: Parser[LambdaTerm] =
    validCharacterLiteral ^^ { c => ChurchNumberConverter.encodeNumber(c.toInt) }

  def stringLiteralTerm: Parser[LambdaTerm] =
    stringLiteral ^^ { s =>
      ChurchPairConverter.encodeString(unquoteString(s))
    }

  private def range[T](p: => Parser[T])(implicit ev: Integral[T]): Parser[LambdaTerm] = {
    val q = p
    val parser = q ~ opt(comma ~> q) ~ (dotDot ~> q)
    parser ^^ { case start ~ next ~ exit =>
      val step  = next.map(ev.minus(_, start)).getOrElse(ev.one)
      val range = NumericRange.inclusive(start, exit, step).toList
      val nums  = range map ev.toInt map ChurchNumberConverter.encodeNumber
      ChurchPairConverter.encodeList(nums)
    }
  }

  def listLiteral: Parser[LambdaTerm] = (
      brackets(repsep(term, comma)) ^^ ChurchPairConverter.encodeList
    | brackets(range(validNumberLiteral))
    | brackets(range(validCharacterLiteral))
  )

  private def lambda = lambdaLetter | lambdaSlash

  private def parenthesis[T](p: Parser[T]): Parser[T] =
    leftParenthesis ~> p <~ rightParenthesis

  private def brackets[T](p: Parser[T]): Parser[T] =
    leftBracket ~> p <~ rightBracket

  def func: Parser[LambdaFunc] =
    parenthesis((lambda ~> variable) ~ (abstractionDot ~> term)) ^^ { LambdaFunc }

  def application: Parser[LambdaApp] =
    parenthesis(term ~ term) ^^ { LambdaApp }

  def term: Parser[LambdaTerm] =
    variable | literal | func | application

  def literal: Parser[LambdaTerm] =
    numericLiteral | characterLiteral | stringLiteralTerm | listLiteral

  def binding: Parser[MorganeyBinding] =
    (variable <~ bindingAssign) ~ term ^^ { MorganeyBinding }

  def loading: Parser[MorganeyLoading] =
    loadKeyword ~> (modulePath.r ?) ^^ { MorganeyLoading }

  def replCommand: Parser[MorganeyNode] = loading | binding | term

  def script: Parser[List[MorganeyNode]] = rep(replCommand)

  def module: Parser[List[MorganeyNode]] = rep(loading | binding)
}
