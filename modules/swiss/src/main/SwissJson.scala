package lidraughts.swiss

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.i18n.Lang
import play.api.libs.json._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

import lidraughts.common.{ GreatPlayer, LightUser }
import lidraughts.hub.lightTeam.TeamId
import lidraughts.quote.Quote.quoteWriter
import lidraughts.rating.PerfType
import lidraughts.socket.Socket.SocketVersion
import lidraughts.user.User

final class SwissJson(
    lightUserApi: lidraughts.user.LightUserApi
)(implicit ec: ExecutionContext) {

  def apply(
    swiss: Swiss,
    leaderboard: List[LeaderboardPlayer],
    me: Option[User],
    socketVersion: Option[SocketVersion]
  )(implicit lang: Lang): Fu[JsObject] = fuccess {
    Json
      .obj(
        "id" -> swiss.id.value,
        "createdBy" -> swiss.createdBy,
        "startsAt" -> formatDate(swiss.startsAt),
        "name" -> swiss.name,
        "perf" -> swiss.perfType,
        "clock" -> swiss.clock,
        "variant" -> swiss.variant.key,
        "nbRounds" -> swiss.nbRounds,
        "nbPlayers" -> swiss.nbPlayers,
        "leaderboard" -> leaderboard.map { l =>
          Json.obj(
            "player" -> Json.obj(
              "user" -> lightUserApi.sync(l.player.userId),
              "rating" -> l.player.rating,
              "points" -> l.player.points,
              "score" -> l.player.score
            ),
            "pairings" -> swiss.allRounds.map(l.pairings.get).map {
              _.fold[JsValue](JsNull) { p =>
                Json.obj(
                  "o" -> p.opponentOf(l.player.number),
                  "g" -> p.gameId,
                  "w" -> p.winner.map(l.player.number.==)
                )
              }
            }
          )
        }
      )
      .add("isStarted" -> swiss.isStarted)
      .add("isFinished" -> swiss.isFinished)
      .add("socketVersion" -> socketVersion.map(_.value))
      .add("quote" -> swiss.isCreated.option(lidraughts.quote.Quote.one(swiss.id.value)))
      .add("description" -> swiss.description)
  }

  private def formatDate(date: DateTime) = ISODateTimeFormat.dateTime print date

  implicit private val playerNumberWriter: Writes[SwissPlayer.Number] = Writes[SwissPlayer.Number] { n =>
    JsNumber(n.value)
  }
  implicit private val pointsWriter: Writes[Swiss.Points] = Writes[Swiss.Points] { p =>
    JsNumber(p.value)
  }
  implicit private val scoreWriter: Writes[Swiss.Score] = Writes[Swiss.Score] { s =>
    JsNumber(s.value)
  }

  implicit private val pairingWrites: OWrites[SwissPairing] = OWrites { p =>
    Json.obj(
      "gameId" -> p.gameId,
      "white" -> p.white,
      "black" -> p.black,
      "winner" -> p.winner
    )
  }

  implicit private val clockWrites: OWrites[draughts.Clock.Config] = OWrites { clock =>
    Json.obj(
      "limit" -> clock.limitSeconds,
      "increment" -> clock.incrementSeconds
    )
  }

  implicit private def perfTypeWrites(implicit lang: Lang): OWrites[PerfType] = OWrites { pt =>
    Json.obj(
      "icon" -> pt.iconChar.toString,
      "name" -> pt.name
    )
  }
}
