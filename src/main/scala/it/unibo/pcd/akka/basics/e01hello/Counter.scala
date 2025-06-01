package it.unibo.pcd.akka.basics.e01hello

import akka.actor.typed.{ActorSystem, Behavior, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import Counter.*

//esempio di attore con comportamento variabile
object Counter:
  enum Command:
    case Tick
  export Command.*
  def apply(from: Int, to: Int): Behavior[Command] =
    Behaviors.receive: (context, msg) =>
      msg match
        case Tick if from != to => //se from == to (= 2), allora continuo con il comportamento corrente
          context.log.info(s"Count: $from")
          Counter(from - from.compareTo(to), to)
        case _ => Behaviors.stopped //se from == to (= 2), allora termino il comportamento corrente
  //oop
  def apply(to: Int): Behavior[Command] =
    Behaviors.setup(new Counter(_, 0, to))

//oop
class Counter(context: ActorContext[Counter.Command], var from: Int, val to: Int)
    extends AbstractBehavior[Counter.Command](context):
  override def onMessage(msg: Counter.Command): Behavior[Counter.Command] = msg match
    case Tick if from != to =>
      context.log.info(s"Count: $from")
      from -= from.compareTo(to)
      this
    case _ => Behaviors.stopped

@main def functionalApi: Unit =
  val system = ActorSystem[Command](guardianBehavior = Counter(0, 2), name = "counter")
  for (_ <- 0 to 2) system ! Tick //invio tre volte un messaggio all'attore

@main def OOPApi: Unit =
  val system = ActorSystem[Counter.Command](Counter(2), "counter")
  for (_ <- 0 to 2) system ! Tick //invio tre volte un messaggio all'attore
