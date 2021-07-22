package net.degoes.zio

import zio._
import scala.collection.immutable.Nil
import scala.annotation.tailrec

object Looping extends App {
  import zio.console._

  /**
   * EXERCISE
   *
   * Implement a `repeat` combinator using `flatMap` (or `zipRight`) and recursion.
   */
  def repeat[R, E, A](n: Int)(effect: Int => ZIO[R, E, A]): ZIO[R, E, Chunk[A]] =
    repeatInternal(n, ZIO.succeed(Chunk.empty), effect(_).map(Chunk.succeed))

  @tailrec def repeatInternal[R, E, A](
    n: Int,
    prev: ZIO[R, E, Chunk[A]],
    more: Int => ZIO[R, E, Chunk[A]]
  ): ZIO[R, E, Chunk[A]] =
    n match {
      case n if n < 1 => prev
      case _ =>
        repeatInternal(n - 1, more(n).zipWith(prev)(_ ++ _), more)
    }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    repeat(100000000)(n => putStrLn(s"${n}: All work and no play makes Jack a dull boy"))
      .flatMap(c => putStrLn(s"Size: ${c.size}"))
      .exitCode
}

object Interview extends App {
  import java.io.IOException
  import zio.console._

  val questions: List[String] =
    "Where where you born?" ::
      "What color are your eyes?" ::
      "What is your favorite movie?" ::
      "What is your favorite number?" :: Nil

  /**
   * EXERCISE
   *
   * Implement the `getAllAnswers` function in such a fashion that it will ask
   * the user each question and collect them all into a list.
   */
  def getAllAnswers(questions: List[String]): ZIO[Console, IOException, List[String]] =
    questions match {
      case Nil => ZIO.succeed(Nil)
      case q :: qs =>
        (putStrLn(q) *> getStrLn).zipWith(getAllAnswers(qs)) {
          case (answer, answers: Seq[String]) =>
            answer +: answers
        }
    }

  /**
   * EXERCISE
   *
   * Use the preceding `getAllAnswers` function, together with the predefined
   * `questions`, to ask the user a bunch of questions, and print the answers.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    getAllAnswers(questions).flatMap(answers => answers.map(putStrLn(_)).reduce(_ *> _)).exitCode
}

object InterviewGeneric extends App {
  import java.io.IOException
  import zio.console._

  val questions =
    "Where where you born?" ::
      "What color are your eyes?" ::
      "What is your favorite movie?" ::
      "What is your favorite number?" :: Nil

  /**
   * EXERCISE
   *
   * Implement the `iterateAndCollect` function.
   */
  def iterateAndCollect[R, E, A, B](as: List[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
    as match {
      case Nil     => ZIO.succeed(Nil)
      case a :: as => f(a).flatMap(r => iterateAndCollect(as)(f).map(xs => r +: xs))
    }

  def getAnswer(question: String): ZIO[Console, IOException, String] =
    putStrLn(question) *> getStrLn

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    iterateAndCollect(questions)(getAnswer).orDie.exitCode
}

object InterviewForeach extends App {
  import zio.console._

  val questions =
    "Where where you born?" ::
      "What color are your eyes?" ::
      "What is your favorite movie?" ::
      "What is your favorite number?" :: Nil

  /**
   * EXERCISE
   *
   * Using `ZIO.foreach`, iterate over each question in `questions`, print the
   * question to the user (`putStrLn`), read the answer from the user
   * (`getStrLn`), and collect all answers into a collection. Finally, print
   * out the contents of the collection.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    ZIO
      .foreach(questions)(InterviewGeneric.getAnswer)
      .orDie
      .flatMap(x => putStrLn(x.toString))
      .exitCode
}

object WhileLoop extends App {
  import zio.console._

  /**
   * EXERCISE
   *
   * Implement the functional effect version of a while loop.
   */
  def whileLoop[R, E, A](cond: UIO[Boolean])(zio: ZIO[R, E, A]): ZIO[R, E, Chunk[A]] =
    cond.flatMap {
      case false => ZIO.succeed(Chunk.empty[A])
      case true =>
        zio.zipWith(whileLoop(cond)(zio))(_ +: _)
    }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    def loop(variable: Ref[Int]) =
      whileLoop(variable.get.map(_ < 100)) {
        for {
          value <- variable.get
          _     <- putStrLn(s"At iteration: ${value}")
          _     <- variable.update(_ + 1)
        } yield ()
      }

    (for {
      variable <- Ref.make(0)
      _        <- loop(variable)
    } yield 0).exitCode
  }
}

object Iterate extends App {
  import zio.console._

  /**
   * EXERCISE
   *
   * Implement the `iterate` function such that it iterates until the condition
   * evaluates to false, returning the "last" value of type `A`.
   */
  def iterate[R, E, A](start: A)(cond: A => Boolean)(f: A => ZIO[R, E, A]): ZIO[R, E, A] =
    cond(start) match {
      case false => ZIO.succeed(start)
      case true =>
        f(start).flatMap(a => iterate(a)(cond)(f))
    }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    iterate(0)(_ < 100)(i => putStrLn(s"At iteration: ${i}").as(i + 1)).exitCode
}

object TailRecursive extends App {
  import zio.duration._

  trait Response
  trait Request {
    def returnResponse(response: Response): Task[Unit]
  }

  lazy val acceptRequest: Task[Request] = Task(new Request {
    def returnResponse(response: Response): Task[Unit] =
      Task(println(s"Returning response ${response}"))
  })

  def handleRequest(request: Request): Task[Response] = Task {
    println(s"Handling request ${request}")
    new Response {}
  }

  /**
   * EXERCISE
   *
   * Make this infinite loop (which represents a webserver) effectfully tail
   * recursive.
   */
  lazy val webserver: Task[Nothing] =
    for {
      request  <- acceptRequest
      response <- handleRequest(request)
      _        <- request.returnResponse(response)
      nothing  <- webserver
    } yield nothing

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      fiber <- webserver.fork
      _     <- ZIO.sleep(100.millis)
      _     <- fiber.interrupt
    } yield ()).exitCode
}
