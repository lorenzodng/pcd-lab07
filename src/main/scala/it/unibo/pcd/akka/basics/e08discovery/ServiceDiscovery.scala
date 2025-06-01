package it.unibo.pcd.akka.basics.e08discovery
import akka.actor.typed.ActorRef
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import it.unibo.pcd.akka.basics.e08discovery.IDGenerator.RequestId
import java.util.UUID
import scala.language.postfixOps

//esempio di attori che interagiscono tra loro attraverso un meccanismo di discovery, ovvero un meccanismo che permette agli attori di scoprire altri attori registrati in un sistema

//attore
object IDGenerator:
  case class RequestId(self: ActorRef[String])

  val key = ServiceKey[RequestId]("id-generator") //definisco una chiave (dato che è val, sarà visibile anche al di fuori di questo object)
                                                  //la chiave identifica tutti gli attori che fanno parte di un insieme, e cambia in corrispondenza di un intervento di aggiunta o rimozione degli attori in quell'insieme
  def apply(): Behavior[RequestId] = Behaviors.setup[RequestId]: ctx => //definisco un attore che riceve solo messaggi di tipo RequestId
    ctx.system.receptionist ! Receptionist.register(key, ctx.self) //registro l'attore al Receptionist (sarebbe un attore speciale di sistema che funge da registro centrale per contenere determinati attori) attraverso la chiave
    Behaviors.receiveMessage:
      case RequestId(sender) => //se il messaggio ricevuto è di tipo RequestId (anche se il tipo di messaggio che l'attore può ricevere è già noto, è comunque necessario specificare il case per utilizzare l'argomento)
        sender ! UUID.randomUUID().toString //invio al mittente un identificativo (UUID) random come stringa
        ctx.log.info("request received!")
        Behaviors.same //ripeto il comportamento

//attore
object UsingSubscribe:
  def apply(): Behavior[Nothing] = Behaviors.setup[Receptionist.Listing]: ctx => //definisco un attore che riceve solo messaggi di tipo Receptionist.Listing
      val myPrinter = ctx.spawnAnonymous(Printer()) //creo un attore Printer
      ctx.system.receptionist ! Receptionist.Subscribe(key = IDGenerator.key, ctx.self) //registro l'attore a Receptionist attraverso la chiave
      Behaviors.receiveMessagePartial[Receptionist.Listing]: //utilizzo "receiveMessagePartial" perchè sono interessato ai messaggi relativi a una specifica chiave (in alteri termini sfrutto il case)
        case IDGenerator.key.Listing(lst) => //se è ricevuto un messaggio che notifica i cambiamenti degli attori registrati con quella chiave al receptionist su Receptionist
          ctx.log.info("new generator (subscribe)" + lst.size)
          lst.foreach(_ ! RequestId(myPrinter)) //per ogni attore nella lista, invio un messaggio RequestId passando come riferimento il proprio attore myPrinter.
          Behaviors.same // ripeto il comportamento
  .narrow //impedisce l’invio di messaggi espliciti mandati dall’esterno da altri attori una volta che apply è stato inizializzato (N.B.: il comportamento dell'attore ha inizio una volta che apply è stato preparato, quindi una volta che è stato "eseguito" per la prima volta), ma non quelli "impliciti", come quelli inviati dalle modifiche di Receptionist

//attore
object UsingFind:
  def apply(): Behavior[Nothing] = Behaviors.setup[Receptionist.Listing]: ctx =>
      val myPrinter = ctx.spawnAnonymous(Printer())
      ctx.system.receptionist ! Receptionist.Find(key = IDGenerator.key, ctx.self)
      Behaviors.receiveMessagePartial[Receptionist.Listing]:
        case IDGenerator.key.Listing(lst) =>
          ctx.log.info("new generator (find)" + lst.size)
          lst.foreach(_ ! RequestId(myPrinter))
          Behaviors.stopped //dopo la prima esecuzione, fermo il comportamento
  .narrow

//attore
object Printer:
  def apply(): Behavior[String] = Behaviors.receive: //definisco un attore che riceve solo messaggi di tipo String
    case (ctx, msg) =>
      ctx.log.info(msg) //stampo il messaggio ricevuto
      Behaviors.same //ripeto il comportamento


@main def checkDiscovery: Unit =
  enum Command:
    case Start
    
  val system = ActorSystem.create(Behaviors.setup[Command]: ctx => //creo un actor system
        ctx.spawnAnonymous(IDGenerator()) //creo un attore IDGenerator
        ctx.spawnAnonymous(UsingSubscribe()) //creo un attore UsingSubscribe
        ctx.spawnAnonymous(UsingFind()) //creo un attore UsingFind
        Behaviors.receiveMessage:
          case Command.Start =>
            ctx.spawnAnonymous(IDGenerator()) //creo un secondo attore IDGenerator (UsingSubscribe e UsingFind quindi verranno notificati due volte)
            Behaviors.same //ripeto il comportamento
  .narrow, "discovery-check") //non può ricevere messaggi dall'esterno (non sarebbe necessario, ma è per maggiore sicurezza)
  
  Thread.sleep(300)
  
  system ! Command.Start //invio un messaggio Start all'actor system
  
  Thread.sleep(500)
  
  system.terminate() //termino l'actor system arrestando tutti gli attori
