package lidraughts.swiss
package actorApi

import scala.concurrent.Promise

import lidraughts.game.Game
import lidraughts.socket.Socket.{ Uid, SocketVersion }
import lidraughts.socket.SocketMember
import lidraughts.user.User

private[swiss] case class Member(
    channel: JsChannel,
    userId: Option[String],
    troll: Boolean
) extends SocketMember

private[swiss] object Member {
  def apply(channel: JsChannel, user: Option[User]): Member = Member(
    channel = channel,
    userId = user map (_.id),
    troll = user.??(_.troll)
  )
}

private[swiss] case class Messadata(trollish: Boolean = false)

private[swiss] case class Join(
    uid: Uid,
    user: Option[User],
    version: Option[SocketVersion],
    promise: Promise[Connected]
)
private[swiss] case class Talk(swissId: String, u: String, t: String, troll: Boolean)
private[swiss] case object Reload
private[swiss] case class StartGame(game: Game)
private[swiss] case class Connected(enumerator: JsEnumerator, member: Member)

private[swiss] case object NotifyCrowd
private[swiss] case object NotifyReload
