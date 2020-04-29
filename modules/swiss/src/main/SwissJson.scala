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

final class SwissJson( // lightUserApi: lila.user.LightUserApi
) {

  def apply(
    swiss: Swiss,
    // rounds: List[SwissRound],
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
        "nbPlayers" -> swiss.nbPlayers
      )
      .add("isStarted" -> swiss.isStarted)
      .add("isFinished" -> swiss.isFinished)
      .add("socketVersion" -> socketVersion.map(_.value))
      .add("quote" -> swiss.isCreated.option(lidraughts.quote.Quote.one(swiss.id.value)))
      .add("description" -> swiss.description)
  }

  private def formatDate(date: DateTime) = ISODateTimeFormat.dateTime print date

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
