package lidraughts.matches

import akka.actor._
import play.api.libs.iteratee._
import play.api.libs.json._
import scala.concurrent.duration._

import actorApi._
import lidraughts.hub.TimeBomb
import lidraughts.socket.actorApi.{ Connected => _, _ }
import lidraughts.socket.{ SocketActor, History, Historical }

private[matches] final class Socket(
    matchId: String,
    val history: History[Messadata],
    getMatch: Match.ID => Fu[Option[Match]],
    jsonView: JsonView,
    lightUser: lidraughts.common.LightUser.Getter,
    uidTimeout: Duration,
    socketTimeout: Duration
) extends SocketActor[Member](uidTimeout) with Historical[Member, Messadata] {

  override def preStart(): Unit = {
    super.preStart()
    lidraughtsBus.subscribe(self, Symbol(s"chat-$matchId"))
  }

  override def postStop(): Unit = {
    super.postStop()
    lidraughtsBus.unsubscribe(self)
  }

  private val timeBomb = new TimeBomb(socketTimeout)

  private var delayedCrowdNotification = false

  private def redirectPlayer(game: lidraughts.game.Game, colorOption: Option[draughts.Color]): Unit = {
    colorOption foreach { color =>
      val player = game player color
      player.userId foreach { userId =>
        membersByUserId(userId) foreach { member =>
          notifyMember("redirect", game fullIdOf player.color)(member)
        }
      }
    }
  }

  def receiveSpecific = ({

    case Reload =>
      getMatch(matchId) foreach {
        _ foreach { mtch =>
          jsonView(mtch) foreach { obj =>
            notifyVersion("reload", obj, Messadata())
          }
        }
      }

    case Aborted => notifyVersion("aborted", Json.obj(), Messadata())

    case Ping(uid, Some(v), c) => {
      ping(uid, c)
      timeBomb.delay
      withMember(uid) { m =>
        history.since(v).fold(resync(m))(_ foreach sendMessage(m))
      }
    }

    case Broom => {
      broom
      if (timeBomb.boom) self ! PoisonPill
    }

    case GetVersion => sender ! history.version

    case Socket.GetUserIds => sender ! members.values.flatMap(_.userId)

    case Quit(uid) =>
      quit(uid)
      notifyCrowd

    case NotifyCrowd =>
      delayedCrowdNotification = false
      showSpectators(lightUser)(members.values) foreach { notifyAll("crowd", _) }

  }: Actor.Receive) orElse lidraughts.chat.Socket.out(
    send = (t, d, trollish) => notifyVersion(t, d, Messadata(trollish))
  )

  def notifyCrowd: Unit = {
    if (!delayedCrowdNotification) {
      delayedCrowdNotification = true
      context.system.scheduler.scheduleOnce(500 millis, self, NotifyCrowd)
    }
  }

  protected def shouldSkipMessageFor(message: Message, member: Member) =
    message.metadata.trollish && !member.troll
}

case object Socket {
  case object GetUserIds
}
