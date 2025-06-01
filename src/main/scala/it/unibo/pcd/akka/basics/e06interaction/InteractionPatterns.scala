package it.unibo.pcd.akka.basics.e06interaction

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.Success

//pattern di interazione tra attori
object HelloBehavior:

  final case class Greet(whom: String, replyTo: ActorRef[Greeted])
  final case class Greeted(whom: String, from: ActorRef[Greet])

  def apply(): Behavior[Greet] = Behaviors.receive: (context, message) =>
    context.log.info("Hello {}!", message.whom)
    message.replyTo ! Greeted(message.whom, context.self)
    Behaviors.same

//esempio di interazione tra attori con pattern ask
object InteractionPatternsAsk extends App:
  import HelloBehavior.*

  val system = ActorSystem(
    Behaviors.setup[Greeted] { ctx =>  //viene creato un attore "padre"
      val greeter = ctx.spawnAnonymous(HelloBehavior())
      given Timeout = 2.seconds //specifico quanto tempo l'attore deve aspettare una risposta
      given Scheduler = ctx.system.scheduler
      given ExecutionContext = ctx.executionContext //forzo l'esecuzione di onComplete da parte del message-loop
      val f: Future[Greeted] = greeter ? (replyTo => Greet("Bob", replyTo)) //mando un messaggio all'attore HelloBehavior (attore figlio) specificando come mittente un attore temporaneo replyTo (dura solo fino a quando riceve la risposta) e mi metto in attesa per 2 secondi. Quando mi metto in attesa con questo pattern, il message loop può continuare a reagire ad altri messaggi se arrivano prima, e quando arriva questo, l'esecuzione riprende da qui.
      f.onComplete { //una volta ottenuta la risposta, eseguo un handler
                     /*
                     N.B.: f.onComplete non fa parte esattamente del comportamento dell'attore padre, proprio perchè viene eseguito solo in corrispondenza di eventuali eventi (ovvero, in questo caso, in seguito alla risposta di HelloBehavior).
                     onComplete, quindi, viene in realtà eseguito su un thread differente rispetto al thread di Akka rappresentativo del message loop che esegue Behaviors.setup.
                     Tuttavia, se si verifica il caso in cui dovrebbe sia essere eseguito onComplete che un altro possibile handler (in questo caso non presente) dedicato alla gestione di un'ulteriore risposta, non si incorre in corse critiche grazie a ExecutionContext = ctx.executionContext che "forza" l'esecuzione di onComplete sul thread del message loop
                     */
        case Success(Greeted(who, from)) => println(s"$who has been greeted by ${from.path}!")
        case _ => println("No greet")
      }
      Behaviors.empty // non fa nulla, quindi il comportamento vale per un solo ciclo di esecuzione
    },
    name = "hello-world"
  )

//esempio interazione tra attori con pattern pipeToSelf
object InteractionPatternsPipeToSelf extends App:
  import HelloBehavior._

  val system = ActorSystem(
    Behaviors.setup[Greeted] {
      ctx =>
      val greeter = ctx.spawn(HelloBehavior(), "greeter")
      given Timeout = 2.seconds //creo un timer di 2 secondi. Ogni volta che riceve un messaggio, si ferma per 2 secondi
      given Scheduler = ctx.system.scheduler
      val f: Future[Greeted] = greeter ? (replyTo => Greet("Bob", replyTo))
      ctx.pipeToSelf(f)(_.getOrElse(Greeted("nobody", ctx.system.ignoreRef))) //trasferisco all'attore padre il messaggio di risposta
                                                                              /*
                                                                              N.B: differentemente dal caso precedente, le race conditions sono evitate attraverso pipeToSelf che permette di reinviare il messaggio ottenuto come risposta all'attore stesso,
                                                                              così da gestirlo direttamente all'interno del comportamento dell'attore mediante Behavior.receive
                                                                              */
      Behaviors.receive { //l'attore padre reagisce al messaggio di risposta
        case (ctx, Greeted(whom, from)) =>
        ctx.log.info(s"$whom has been greeted by ${from.path.name}")
        Behaviors.stopped
      }
    },
    name = "hello-world"
  )

//esempio di interazione di un attore con se stesso con pattern self-message
object InteractionPatternsSelfMessage extends App:
  val system = ActorSystem(
    Behaviors.setup[String] { ctx =>
      Behaviors.withTimers {
        timers => Behaviors.receiveMessage {
          case "" => Behaviors.stopped
          case s => //elaboro il messaggio un carattere per volta e in modo ricorsivo
            ctx.log.info("" + s.head) //aggiungo ogni carattere alla testa (già parzialmente formata)
            timers.startSingleTimer(s.tail, 300.millis) //attendo 300 ms e invio un messaggio all'attore stesso contenente la coda della stringa
            Behaviors.same
        }
      }
    },
    name = "hello-world"
  )
  system ! "hello akka" //questa è l'implementazione del pattern self-message, in quanto l'attore si invia un messaggio a se stesso

//esempio di interazione tra attori con messageAdapter
object InteractionPatternsMsgAdapter extends App:
  val system = ActorSystem(
    Behaviors.setup[String] { ctx =>
      val adaptedRef: ActorRef[Int] = ctx.messageAdapter[Int](i => if (i == 0) "" else i.toString) //creo un attore, lo adatto a ricevere interi e se il messaggio ricevuto è 0, lo converto in una stringa vuota, altrimenti lo converto in stringa
      adaptedRef ! 130 //mando un messaggio all'attore
      adaptedRef ! 0 //mando un secondo messaggio all'attore (che verrà convertito in stringa vuota)
      Behaviors.receiveMessage {
        case "" => //se ricevo una stringa vuota, termino l'attore
          ctx.log.info("Bye bye")
          Behaviors.stopped
        case s => //altrimenti, stampo la stringa
          ctx.log.info(s)
          Behaviors.same
      }
    },
    name = "hello-world"
  )