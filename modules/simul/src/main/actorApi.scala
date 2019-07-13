package lidraughts.simul
package actorApi

import play.api.libs.json.JsObject
import scala.concurrent.Promise

import lidraughts.game.Game
import lidraughts.socket.RemoteSocketMember
import lidraughts.socket.Socket.{ Sri, SocketVersion }
import lidraughts.user.User

private[simul] case class SimulSocketMember(
    sri: Sri,
    userId: Option[String],
    troll: Boolean,
    bus: lidraughts.common.Bus
) extends RemoteSocketMember

private[simul] case class Messadata(trollish: Boolean = false)

private[simul] case class Join(
    sri: Sri,
    user: Option[User],
    version: Option[SocketVersion],
    promise: Promise[Connected]
)
private[simul] case class Talk(tourId: String, u: String, t: String, troll: Boolean)
private[simul] case class StartGame(game: Game, hostId: String)
private[simul] case class StartSimul(firstGame: Game, hostId: String)
private[simul] case class HostIsOn(gameId: String)
private[simul] case class ReloadEval(gameId: String, json: JsObject)
private[simul] case object Reload
private[simul] case object Aborted
private[simul] case class Connected(member: SimulSocketMember)

private[simul] case object NotifyCrowd

private[simul] case class GetUserIdsP(promise: Promise[Iterable[User.ID]])

case class SimulTable(simuls: List[Simul])
