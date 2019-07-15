package lidraughts.socket

import lidraughts.hub.Trouper
import actorApi.{ SocketEnter, SocketLeave, PopulationTell, NbMembers, RemoteNbMembers }

final class SocketPopulation(system: akka.actor.ActorSystem) extends Trouper {

  private var nb = 0
  private var remoteNb = 0

  system.lidraughtsBus.subscribe(this, 'socketEnter, 'socketLeave)

  val process: Trouper.Receive = {

    case _: SocketEnter =>
      nb = nb + 1
      lidraughts.mon.socket.open()

    case _: SocketLeave =>
      nb = nb - 1
      lidraughts.mon.socket.close()

    case RemoteNbMembers(r) => remoteNb = r

    case PopulationTell => system.lidraughtsBus.publish(NbMembers(nb + remoteNb), 'nbMembers)
  }
}
