package it.unibo.pcd.akka.basics.e01hello

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

//esempio di creazione attore dentro attore e terminazione del comportamento con context.stop()
//creo un attore
object Foo:
  def apply(): Behavior[String] = Behaviors.receive: (context, message) =>
    context.log.info("Hello " + message)
    Behaviors.same

//creo un attore
object HelloActor2:

  final case class Greet(whom: String, replyTo: ActorRef[Greeted])
  final case class Greeted(whom: String, from: ActorRef[Greet])

  def apply(): Behavior[Greet] = Behaviors.receive: (context, message) =>
    context.log.info("Hello {}!", message.whom)
    val ref = context.spawn(Foo(), "foo") //creo un nuovo attore
    val ref2 = context.spawnAnonymous(Foo()) //creo un nuovo attore con un riferimento casuale unico (non mi interessa il nome) N.B.: ogni attore deve avere un nome unico, perchè l'actor system altrimenti non saprebbe a chi mandare il messaggio
    ref ! "Hello!" //mando un messaggio al nuovo attore
    context.stop(ref) //stoppo il nuovo attore
    message.replyTo ! Greeted(message.whom, context.self)
    Behaviors.same

object HelloWorldAkkaTyped2 extends App:
  val system: ActorSystem[HelloActor2.Greet] = ActorSystem(HelloActor2(), name = "hello-world") //creo un sistema di attori (contenente solo l'attore HelloActor). Durante questa creazione viene indicato il comportamento che l'attore deve assumere ogni volta che viene inviato un messaggio, e definito in apply.
  system ! HelloActor2.Greet("Akka Typed", system.ignoreRef) //invio un messaggio: destinatario (system) ! (messaggio, mittente (system.ignoreRef)) (system.ignoreRef è un attore "fittizio" che ignora i messaggi, quindi non fa nulla)
  Thread.sleep(5000)
  system.terminate()
