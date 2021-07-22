package net.degoes.zio

import zio._

object ForkJoin extends App {
  import zio.console._

  val printer =
    putStrLn(".").repeat(Schedule.recurs(10))

  /**
   * EXERCISE
   *
   * Using `ZIO#fork`, fork the `printer` into a separate fiber, and then
   * print out a message, "Forked", then join the fiber using `Fiber#join`,
   * and finally, print out a message "Joined".
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      fiber <- printer.fork
      _ <- putStrLn("FORKED")
      _ <- fiber.join
      _ <- putStrLn("JOINED")
    } yield ()).exitCode

}

object ForkInterrupt extends App {
  import zio.console._
  import zio.duration._

  val infinitePrinter =
    putStrLn(".").forever

  /**
   * EXERCISE
   *
   * Using `ZIO#fork`, fork the `printer` into a separate fiber, and then
   * print out a message, "Forked", then using `ZIO.sleep`, sleep for 100
   * milliseconds, then interrupt the fiber using `Fiber#interrupt`, and
   * finally, print out a message "Interrupted".
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
//    (infinitePrinter *> ZIO.sleep(10.millis)).exitCode
    (for {
      fiber <- infinitePrinter.fork
      _ <- putStrLn("FORKED")
      _ <- ZIO.sleep(10.millis)
      _ <- fiber.interrupt
      _ <- putStrLn("INTERUPTED")
    } yield ()).exitCode
}

object ParallelFib extends App {
  import zio.console._

  /**
   * EXERCISE
   *
   * Rewrite this implementation to compute nth fibonacci number in parallel.
   */
  def fib(n: Int): UIO[BigInt] = {
    def foo(a: Fiber[Nothing, Int], b: Fiber[Nothing, Int]): Fiber[Nothing, Int] =
      a.zipWith(b)(_ + _)
//
//    val x = for {
//      _ <- ZIO.unit
//      zero <- UIO(1).fork
//      xs <- ZIO.foldLeft(0 to n)(zero) { case (aFib, n) =>
//        for {
//          _ <- ZIO.unit
//
//        } yield ()
//      }
//    } yield ()

    def loop(n: Int, original: Int): UIO[BigInt] =
      if (n <= 1) UIO(n)
      else
        for {
          aFib <- loop(n - 1, original).fork
          bFib <- loop(n - 2, original).fork
          cFib = aFib.zipWith(bFib)(_ + _)
          c <- cFib.join
        } yield {
          c
        }

    loop(n, n)
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    for {
      n <- (putStrLn(
            "What number of the fibonacci sequence should we calculate?"
          ) *> getStrLn.mapEffect(_.toInt)).eventually
      f <- fib(n)
      _ <- putStrLn(s"fib(${n}) = ${f}")
    } yield ExitCode.success
}

object AlarmAppImproved extends App {
  import zio.console._
  import zio.duration._
  import java.io.IOException
  import java.util.concurrent.TimeUnit

  lazy val getAlarmDuration: ZIO[Console, IOException, Duration] = {
    def parseDuration(input: String): IO[NumberFormatException, Duration] =
      ZIO
        .effect(
          Duration((input.toDouble * 1000.0).toLong, TimeUnit.MILLISECONDS)
        )
        .refineToOrDie[NumberFormatException]

    val fallback = putStrLn("You didn't enter a number of seconds!") *> getAlarmDuration

    for {
      _        <- putStrLn("Please enter the number of seconds to sleep: ")
      input    <- getStrLn
      duration <- parseDuration(input) orElse fallback
    } yield duration
  }

  /**
   * EXERCISE
   *
   * Create a program that asks the user for a number of seconds to sleep,
   * sleeps the specified number of seconds using ZIO.sleep(d), concurrently
   * prints a dot every second that the alarm is sleeping for, and then
   * prints out a wakeup alarm message, like "Time to wakeup!!!".
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      n <- getAlarmDuration
      f <- (putStr(".") *> ZIO.sleep(1.second)).forever.fork
      _ <- ZIO.sleep(n)
      _ <- f.interrupt
      _ <- putStrLn("Time to Wakeup!")
    } yield ()).exitCode
}

/**
 * Effects can be forked to run in separate fibers. Sharing information between fibers can be done
 * using the `Ref` data type, which is like a concurrent version of a Scala `var`.
 */
object ComputePi extends App {
  import zio.random._
  import zio.console._
  import zio.clock._
  import zio.duration._
  import zio.stm._

  /**
   * Some state to keep track of all points inside a circle,
   * and total number of points.
   */
  final case class PiState(
    inside: Ref[Long],
    total: Ref[Long]
  )

  /**
   * A function to estimate pi.
   */
  def estimatePi(inside: Long, total: Long): Double =
    (inside.toDouble / total.toDouble) * 4.0

  /**
   * A helper function that determines if a point lies in
   * a circle of 1 radius.
   */
  def insideCircle(x: Double, y: Double): Boolean =
    Math.sqrt(x * x + y * y) <= 1.0

  /**
   * An effect that computes a random (x, y) point.
   */
  val randomPoint: ZIO[Random, Nothing, (Double, Double)] =
    nextDouble zip nextDouble

  /**
   * EXERCISE
   *
   * Build a multi-fiber program that estimates the value of `pi`. Print out
   * ongoing estimates continuously until the estimation is complete.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      _ <- ZIO.unit
      state: PiState <- Ref.make(0L).zipWith(Ref.make(0L))(PiState.apply)
      _ <- step(state).tap(pi => putStrLn(pi.toString)).repeatUntil(pi => (Math.PI - pi).abs <= 0.000000001)
    } yield ()).exitCode

  def step(state: ComputePi.PiState): ZIO[Random, Nothing, Double] =
    for {
      _ <- ZIO.unit
      point <- randomPoint
      (x, y) = point
      _ <- ZIO.when(insideCircle(x, y))(state.inside.update(_ + 1)) *> state.total.update(_ + 1)
      pi <- state.inside.get.zipWith(state.total.get)(estimatePi)
    } yield pi
}

object ParallelZip extends App {
  import zio.console._

  def fib(n: Int): UIO[Int] =
    if (n <= 1) UIO(n)
    else
      UIO.effectSuspendTotal {
        (fib(n - 1) zipWith fib(n - 2))(_ + _)
      }

  /**
   * EXERCISE
   *
   * Compute fib(10) and fib(13) in parallel using `ZIO#zipPar`, and display
   * the result.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (fib(10) zipPar fib(13)).flatMap(t => putStrLn(t.toString)).exitCode
}

object StmSwap extends App {
  import zio.console._
  import zio.stm._

  /**
   * EXERCISE
   *
   * Demonstrate the following code does not reliably swap two values in the
   * presence of concurrency.
   */
  def exampleRef: UIO[Int] = {
    def swap[A](ref1: Ref[A], ref2: Ref[A]): UIO[Unit] =
      for {
        v1 <- ref1.get
        v2 <- ref2.get
        _  <- ref2.set(v1)
        _  <- ref1.set(v2)
      } yield ()

    for {
      ref1   <- Ref.make(100)
      ref2   <- Ref.make(0)
      fiber1 <- swap(ref1, ref2).repeatN(100).fork
      fiber2 <- swap(ref2, ref1).repeatN(100).fork
      _      <- (fiber1 zip fiber2).join
      value  <- (ref1.get zipWith ref2.get)(_ + _)
    } yield value
  }

  /**
   * EXERCISE
   *
   * Using `STM`, implement a safe version of the swap function.
   */
  def exampleStm: UIO[Int] = {
      def swap[A](a: TRef[A], b: TRef[A]): UIO[Unit] =
        STM.atomically[Nothing, Unit] {
          for {
            v1 <- a.get
            v2 <- b.get
            _  <- b.set(v1)
            _  <- a.set(v2)
          } yield ()
        }


      for {
        ref1   <- TRef.makeCommit(100)
        ref2   <- TRef.makeCommit(0)
        fiber1 <- swap(ref1, ref2).repeatN(100).fork
        fiber2 <- swap(ref2, ref1).repeatN(100).fork
        _      <- (fiber1 zip fiber2).join
        value  <- (ref1.get zipWith ref2.get)(_ + _).commit
      } yield value
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    exampleStm.tap(x => putStrLn(x.toString)).mapEffect { case 100 => true }.exitCode
}

object StmLock extends App {
  import zio.console._
  import zio.stm._

  /**
   * EXERCISE
   *
   * Using STM, implement a simple binary lock by implementing the creation,
   * acquisition, and release methods.
   */
  class Lock private (tref: TRef[Boolean]) {
    def acquire: UIO[Unit] = {
        for {
          _<- tref.get.retryUntil(used => !used)
          _ <- tref.set(true)
        } yield ()
    }.commit

    def release: UIO[Unit] =
      (for {
          _ <- tref.get.retryUntil(identity)
          _ <- tref.set(false)
        } yield ()).commit
  }
  object Lock {
    def make: UIO[Lock] = TRef.makeCommit(false).map(new Lock(_))
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    for {
      lock <- Lock.make
      fiber1 <- lock.acquire
                 .bracket_(lock.release)(putStrLn("Bob  : I have the lock!"))
                 .repeat(Schedule.recurs(10))
                 .fork
      fiber2 <- lock.acquire
                 .bracket_(lock.release)(putStrLn("Sarah: I have the lock!"))
                 .repeat(Schedule.recurs(10))
                 .fork
      _ <- (fiber1 zip fiber2).join
    } yield ExitCode.success
}

object StmQueue extends App {
  import zio.console._
  import zio.stm._
  import scala.collection.immutable.{ Queue => ScalaQueue }

  /**
   * EXERCISE
   *
   * Using STM, implement a async queue with double back-pressuring.
   */
  class Queue[A] private (capacity: Int, queue: TRef[ScalaQueue[A]]) {
    def take: UIO[A]           =
      (for {
        q <- queue.get.retryUntil(_.nonEmpty)
        el <- queue.modify(_.dequeue)
      } yield el).commit

    def offer(a: A): UIO[Unit] =
      (for {
        q <- queue.get.retryUntil(_.size < capacity)
        _ <- queue.set(q.enqueue(a))
      } yield ()).commit
  }

  object Queue {
    def bounded[A](capacity: Int): UIO[Queue[A]] =
      TRef.makeCommit(ScalaQueue.empty[A]).map(new Queue(capacity, _))
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    for {
      queue <- Queue.bounded[Int](10)
      _     <- ZIO.foreach(0 to 100)(i => queue.offer(i)).fork
      _ <- ZIO.foreach(0 to 100)(
            _ => queue.take.flatMap(i => putStrLn(s"Got: ${i}"))
          )
    } yield ExitCode.success
}

object StmLunchTime extends App {
  import zio.console._
  import zio.stm._

  /**
   * EXERCISE
   *
   * Using STM, implement the missing methods of Attendee.
   */
  final case class Attendee(i: Int, state: TRef[Attendee.State]) {
    import Attendee.State._

    def isStarving: STM[Nothing, Boolean] = state.map(_ == Starving).get

    def feed: STM[Nothing, Unit] = state.set(Full)
  }
  object Attendee {
    sealed trait State
    object State {
      case object Starving extends State
      case object Full     extends State
    }
  }

  /**
   * EXERCISE
   *
   * Using STM, implement the missing methods of Table.
   */
  final case class Table(seats: TArray[Boolean]) {

    def findEmptySeat: STM[Nothing, Option[Int]] =
      seats
        .fold[(Int, Option[Int])]((0, None)) {
          case ((index, z @ Some(_)), _) => (index + 1, z)
          case ((index, None), taken) =>
            (index + 1, if (taken) None else Some(index))
        }
        .map(_._2)

    def myFindEmptySeat: STM[Nothing, Option[Int]] =
      seats.indexWhere(!_).map {
        case -1 => None
        case emptyIndex => Some(emptyIndex)
      }

    def waitForEmptySeat: ZSTM[Any, Nothing, Int] =
      myFindEmptySeat.retryUntil(_.isDefined).map(_.get)

    def takeSeat(index: Int): STM[Nothing, Unit] =
      seats.update(index, !_)

    def vacateSeat(index: Int): STM[Nothing, Unit] =
      seats.update(index, !_)

  }

  /**
   * EXERCISE
   *
   * Using STM, implement a method that feeds a single attendee.
   */
  def feedAttendee(t: Table, a: Attendee): STM[Nothing, Unit] =
    t.myFindEmptySeat
      .retryUntil(_.isDefined)
      .map(_.get)
      .tap(t.takeSeat)
      .zipLeft(a.feed)
      .flatMap(t.vacateSeat)


  def feedAttendeeEffect(t: Table, a: Attendee): URIO[Console, Unit] =
    for {
      _ <- ZIO.unit
      seat <- t.waitForEmptySeat.tap(t.takeSeat).commit
      _ <- putStrLn(s"Attendee ${a.i} eating at seat ${seat}")
      _ <- a.feed.commit
      _ <- t.vacateSeat(seat).commit
    } yield ()

  /**
   * EXERCISE
   *
   * Using STM, implement a method that feeds only the starving attendees.
   */
  def feedStarving(table: Table, attendees: Iterable[Attendee]): URIO[Console, Unit] =
    ZIO.foreachPar(attendees)(feedAttendeeEffect(table, _)).unit

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    val Attendees = 100
    val TableSize = 5

    for {
      attendees <- ZIO.foreach(0 to Attendees)(
                    i =>
                      TRef
                        .make[Attendee.State](Attendee.State.Starving)
                        .map(Attendee(i, _))
                        .commit
                  )
      table <- TArray
                .fromIterable(List.fill(TableSize)(false))
                .map(Table(_))
                .commit
      _ <- feedStarving(table, attendees)
    } yield ExitCode.success
  }
}

object StmPriorityQueue extends App {
  import zio.console._
  import zio.stm._
  import zio.duration._

  /**
   * EXERCISE
   *
   * Using STM, design a priority queue, where smaller integers are assumed
   * to have higher priority than greater integers.
   */
  class PriorityQueue[A] private (
    minLevel: TRef[Option[Int]],
    map: TMap[Int, TQueue[A]]
  ) {
    def offer(a: A, priority: Int): STM[Nothing, Unit] = ???

    def take: STM[Nothing, A] = ???
  }
  object PriorityQueue {
    def make[A]: STM[Nothing, PriorityQueue[A]] = ???
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      _     <- putStrLn("Enter any key to exit...")
      queue <- PriorityQueue.make[String].commit
      lowPriority = ZIO.foreach(0 to 100) { i =>
        ZIO.sleep(1.millis) *> queue
          .offer(s"Offer: ${i} with priority 3", 3)
          .commit
      }
      highPriority = ZIO.foreach(0 to 100) { i =>
        ZIO.sleep(2.millis) *> queue
          .offer(s"Offer: ${i} with priority 0", 0)
          .commit
      }
      _ <- ZIO.forkAll(List(lowPriority, highPriority)) *> queue.take.commit
            .flatMap(putStrLn(_))
            .forever
            .fork *>
            getStrLn
    } yield 0).exitCode
}

object StmReentrantLock extends App {
  import zio.console._
  import zio.stm._

  private final case class WriteLock(
    writeCount: Int,
    readCount: Int,
    fiberId: Fiber.Id
  )
  private final class ReadLock private (readers: Map[Fiber.Id, Int]) {
    def total: Int = readers.values.sum

    def noOtherHolder(fiberId: Fiber.Id): Boolean =
      readers.size == 0 || (readers.size == 1 && readers.contains(fiberId))

    def readLocks(fiberId: Fiber.Id): Int =
      readers.get(fiberId).fold(0)(identity)

    def adjust(fiberId: Fiber.Id, adjust: Int): ReadLock = {
      val total = readLocks(fiberId)

      val newTotal = total + adjust

      new ReadLock(
        readers =
          if (newTotal == 0) readers - fiberId
          else readers.updated(fiberId, newTotal)
      )
    }
  }
  private object ReadLock {
    val empty: ReadLock = new ReadLock(Map())

    def apply(fiberId: Fiber.Id, count: Int): ReadLock =
      if (count <= 0) empty else new ReadLock(Map(fiberId -> count))
  }

  /**
   * EXERCISE
   *
   * Using STM, implement a reentrant read/write lock.
   */
  class ReentrantReadWriteLock(data: TRef[Either[ReadLock, WriteLock]]) {
    def writeLocks: UIO[Int] = ???

    def writeLocked: UIO[Boolean] = ???

    def readLocks: UIO[Int] = ???

    def readLocked: UIO[Boolean] = ???

    val read: Managed[Nothing, Int] = ???

    val write: Managed[Nothing, Int] = ???
  }
  object ReentrantReadWriteLock {
    def make: UIO[ReentrantReadWriteLock] = ???
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = ???
}

object StmDiningPhilosophers extends App {
  import zio.console._
  import zio.stm._

  sealed trait Fork
  val Fork = new Fork {}

  final case class Placement(
    left: TRef[Option[Fork]],
    right: TRef[Option[Fork]]
  )

  final case class Roundtable(seats: Vector[Placement])

  /**
   * EXERCISE
   *
   * Using STM, implement the logic of a philosopher to take not one fork, but
   * both forks when they are both available.
   */
  def takeForks(
    left: TRef[Option[Fork]],
    right: TRef[Option[Fork]]
  ): STM[Nothing, (Fork, Fork)] =
    ???

  /**
   * EXERCISE
   *
   * Using STM, implement the logic of a philosopher to release both forks.
   */
  def putForks(left: TRef[Option[Fork]], right: TRef[Option[Fork]])(
    tuple: (Fork, Fork)
  ): STM[Nothing, Unit] = ???

  def setupTable(size: Int): ZIO[Any, Nothing, Roundtable] = {
    val makeFork = TRef.make[Option[Fork]](Some(Fork))

    (for {
      allForks0 <- STM.foreach(0 to size) { i =>
                    makeFork
                  }
      allForks = allForks0 ++ List(allForks0(0))
      placements = (allForks zip allForks.drop(1)).map {
        case (l, r) => Placement(l, r)
      }
    } yield Roundtable(placements.toVector)).commit
  }

  def eat(
    philosopher: Int,
    roundtable: Roundtable
  ): ZIO[Console, Nothing, Unit] = {
    val placement = roundtable.seats(philosopher)

    val left  = placement.left
    val right = placement.right

    for {
      forks <- takeForks(left, right).commit
      _     <- putStrLn(s"Philosopher ${philosopher} eating...")
      _     <- putForks(left, right)(forks).commit
      _     <- putStrLn(s"Philosopher ${philosopher} is done eating")
    } yield ()
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    val count = 10

    def eaters(table: Roundtable): Iterable[ZIO[Console, Nothing, Unit]] =
      (0 to count).map { index =>
        eat(index, table)
      }

    for {
      table <- setupTable(count)
      fiber <- ZIO.forkAll(eaters(table))
      _     <- fiber.join
      _     <- putStrLn("All philosophers have eaten!")
    } yield ExitCode.success
  }
}
