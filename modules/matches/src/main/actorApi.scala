package lidraughts.matches
package actorApi

import lidraughts.game.Game
import lidraughts.socket.Socket.Uid
import lidraughts.socket.SocketMember
import lidraughts.user.User

private[matches] case class Member(
    channel: JsChannel,
    userId: Option[String],
    troll: Boolean
) extends SocketMember

private[matches] object Member {
  def apply(channel: JsChannel, user: Option[User]): Member = Member(
    channel = channel,
    userId = user map (_.id),
    troll = user.??(_.troll)
  )
}

private[matches] case class Messadata(trollish: Boolean = false)

private[matches] case object Reload
private[matches] case object Aborted
private[matches] case class Connected(enumerator: JsEnumerator, member: Member)

private[matches] case object NotifyCrowd

case class MatchTable(matches: List[Match])
