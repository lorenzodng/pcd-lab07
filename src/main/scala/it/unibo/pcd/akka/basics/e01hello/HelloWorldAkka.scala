package it.unibo.pcd.akka.basics.e01hello

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

//esempio di attore e trasferimento di messaggi
object HelloActor:

  //messaggi
  final case class Greet(whom: String, replyTo: ActorRef[Greeted])
  final case class Greeted(whom: String, from: ActorRef[Greet])

  //questa funzione definisce il comportamento di un attore
  def apply(): Behavior[Greet] = Behaviors.receive: (context, message) => 
    context.log.info("Hello {}!", message.whom) //viene stampato il messaggio ({} contiene il messaggio comunicato, e viene inserito in whom)
    message.replyTo ! Greeted(message.whom, context.self) //rispondo al messaggio: destinatario (mittente system.ignoreRef) ! (messaggio ({}), mittente (sè stesso))
    Behaviors.same //indico che il comportamento deve essere sempre lo stesso (quindi viene prima eseguito Behaviors.same, e dopo viene analizzato il messaggio eventualmente trasferito)

object HelloWorldAkkaTyped extends App:
  val system: ActorSystem[HelloActor.Greet] = ActorSystem(HelloActor(), name = "hello-world") //creo un sistema di attori (contenente solo l'attore HelloActor) che gestisce le operazioni sui messaggi. Durante questa creazione viene indicato il comportamento che l'attore deve assumere ogni volta che viene inviato un messaggio, e definito in apply.
  system ! HelloActor.Greet("Akka Typed", system.ignoreRef) //invio un messaggio: destinatario (system) ! (messaggio, mittente (system.ignoreRef)) (system.ignoreRef è un attore "fittizio" che ignora i messaggi, quindi non fa nulla)
  Thread.sleep(5000)
  system.terminate()
