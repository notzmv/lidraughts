package lidraughts.lobby

import play.api.libs.json._

import lidraughts.socket.RemoteSocket._, Protocol._
import lidraughts.user.{ User, UserRepo }

final class LobbyRemoteSocket(
    remoteSocketApi: lidraughts.socket.RemoteSocket,
    socket: LobbySocket,
    blocking: User.ID => Fu[Set[User.ID]],
    bus: lidraughts.common.Bus
) {

  private val send: (Path, Args*) => Unit = remoteSocketApi.sendTo("lobby-out") _

  private val handler: Handler = {
    case In.ConnectSri(sri, userOpt) =>
      userOpt map In.ConnectUser.apply foreach remoteSocketApi.baseHandler.lift
      userOpt ?? UserRepo.enabledById foreach { user =>
        (user ?? (u => blocking(u.id))) foreach { blocks =>
          val member = actorApi.LobbySocketMember(bus, user, blocks, sri)
          socket ! actorApi.JoinRemote(member)
        }
      }
    case In.DisconnectSri(sri, userOpt) =>
      userOpt map In.DisconnectUser.apply foreach remoteSocketApi.baseHandler.lift
  }

  remoteSocketApi.subscribe("lobby-in", In.baseReader)(handler orElse remoteSocketApi.baseHandler)
}

object LobbyRemoteSocket {

  object Protocol {
  }
}
