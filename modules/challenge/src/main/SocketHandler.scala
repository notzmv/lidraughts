package lidraughts.challenge

import akka.actor._
import akka.pattern.ask

import lidraughts.hub.actorApi.map._
import lidraughts.socket.actorApi.{ Connected => _, _ }
import lidraughts.socket.Handler
import lidraughts.socket.Socket.{ Sri, SocketVersion }
import lidraughts.user.User
import lidraughts.common.ApiVersion

private[challenge] final class SocketHandler(
    hub: lidraughts.hub.Env,
    socketMap: SocketMap,
    pingChallenge: Challenge.ID => Funit
) {

  import ChallengeSocket._

  def join(
    challengeId: Challenge.ID,
    sri: Sri,
    userId: Option[User.ID],
    owner: Boolean,
    version: Option[SocketVersion],
    apiVersion: ApiVersion
  ): Fu[JsSocketHandler] = {
    val socket = socketMap getOrMake challengeId
    socket.ask[Connected](Join(sri, userId, owner, version, _)) map {
      case Connected(enum, member) => Handler.iteratee(
        hub,
        controller(socket, challengeId, sri, member),
        member,
        socket,
        sri,
        apiVersion
      ) -> enum
    }
  }

  private def controller(
    socket: ChallengeSocket,
    challengeId: Challenge.ID,
    sri: Sri,
    member: Member
  ): Handler.Controller = {
    case ("ping", _) if member.owner => pingChallenge(challengeId)
  }
}
