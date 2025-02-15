package lidraughts

import lidraughts.game.Event
import lidraughts.socket.WithSocket

package object round extends PackageObject with WithSocket {

  private[round] type SocketMap = lidraughts.hub.TrouperMap[RoundSocket]

  private[round] type Events = List[Event]

  private[round] def logger = lidraughts.log("round")
}

package round {

  private[round] sealed trait BenignError extends lidraughts.base.LidraughtsException
  private[round] case class ClientError(message: String) extends BenignError
  private[round] case class DraughtsnetError(message: String) extends BenignError

  sealed trait OnTv

  case class OnLidraughtsTv(channel: String, flip: Boolean) extends OnTv
  case class OnUserTv(userId: String, gameId: Option[String]) extends OnTv
}
