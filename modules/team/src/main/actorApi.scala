package lidraughts.team
package actorApi

import scala.concurrent.Promise

import lidraughts.socket.Socket.{ Uid, SocketVersion }
import lidraughts.socket.SocketMember
import lidraughts.user.User

private[team] case class Member(
    channel: JsChannel,
    userId: Option[String],
    troll: Boolean
) extends SocketMember

private[team] object Member {
  def apply(channel: JsChannel, user: Option[User]): Member = Member(
    channel = channel,
    userId = user map (_.id),
    troll = user.??(_.troll)
  )
}

private[team] case class Messadata(trollish: Boolean = false)

private[team] case class Join(
    uid: Uid,
    user: Option[User],
    version: Option[SocketVersion],
    promise: Promise[Connected]
)
private[team] case class Talk(teamId: String, u: String, t: String, troll: Boolean)
private[team] case class Connected(enumerator: JsEnumerator, member: Member)

private[team] case object NotifyCrowd

case class InsertTeam(team: Team)
case class RemoveTeam(id: String)
