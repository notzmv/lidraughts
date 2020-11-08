package lidraughts.team

import akka.actor._
import akka.pattern.ask

import actorApi._
import akka.actor.ActorSelection
import lidraughts.db.dsl._
import lidraughts.chat.Chat
import lidraughts.common.ApiVersion
import lidraughts.hub.actorApi.map._
import lidraughts.hub.Trouper
import lidraughts.security.Flood
import lidraughts.socket.actorApi.{ Connected => _, _ }
import lidraughts.socket.Handler
import lidraughts.socket.Socket
import lidraughts.user.User
import makeTimeout.short

private[team] final class SocketHandler(
    hub: lidraughts.hub.Env,
    socketMap: SocketMap,
    chat: ActorSelection,
    flood: Flood
) {

  def join(
    teamId: String,
    uid: Socket.Uid,
    user: Option[User],
    version: Option[Socket.SocketVersion],
    apiVersion: ApiVersion
  ): Fu[Option[JsSocketHandler]] =
    TeamRepo exists teamId flatMap {
      _ ?? {
        val socket = socketMap getOrMake teamId
        socket.ask[Connected](Join(uid, user, version, _)) map {
          case Connected(enum, member) => Handler.iteratee(
            hub,
            lidraughts.chat.Socket.in(
              chatId = Chat.Id(teamId),
              member = member,
              chat = chat,
              publicSource = lidraughts.hub.actorApi.shutup.PublicSource.Team(teamId).some
            ),
            member,
            socket,
            uid,
            apiVersion
          ) -> enum
        } map some
      }
    }
}