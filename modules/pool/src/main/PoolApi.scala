package lidraughts.pool

import akka.actor._

import lidraughts.game.Game
import lidraughts.rating.RatingRange
import lidraughts.socket.Socket.{ Uid, Uids }
import lidraughts.user.User

final class PoolApi(
    val configs: List[PoolConfig],
    hookThieve: HookThieve,
    gameStarter: GameStarter,
    playbanApi: lidraughts.playban.PlaybanApi,
    system: ActorSystem
) {

  import PoolApi._
  import PoolActor._

  private val actors: Map[PoolConfig.Id, ActorRef] = configs.map { config =>
    config.id -> system.actorOf(
      Props(new PoolActor(config, hookThieve, gameStarter)),
      name = s"pool-${config.id.value}"
    )
  }.toMap

  def join(poolId: PoolConfig.Id, joiner: Joiner) =
    playbanApi.hasCurrentBan(joiner.userId) foreach {
      case false => actors foreach {
        case (id, actor) if id == poolId => playbanApi.getRageSit(joiner.userId).foreach(actor ! Join(joiner, _))
        case (_, actor) => actor ! Leave(joiner.userId)
      }
      case _ =>
    }

  def leave(poolId: PoolConfig.Id, userId: User.ID) = sendTo(poolId, Leave(userId))

  def socketIds(ids: Set[Uid]) = {
    val msg = Uids(ids)
    actors.values.foreach(_ ! msg)
  }

  private def sendTo(poolId: PoolConfig.Id, msg: Any) =
    actors get poolId foreach { _ ! msg }
}

object PoolApi {

  case class Joiner(
      userId: User.ID,
      uid: Uid,
      ratingMap: Map[String, Int],
      ratingRange: Option[RatingRange],
      lame: Boolean,
      blocking: Set[String]
  ) {

    def is(member: PoolMember) = userId == member.userId
  }

  case class Pairing(game: Game, whiteUid: Uid, blackUid: Uid)
}
