package lidraughts.swiss

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json._
import scala.concurrent.ExecutionContext

import lidraughts.common.{ GreatPlayer, LightUser }
import lidraughts.db.dsl._
import lidraughts.game.Game
import lidraughts.game.JsonView.boardSizeWriter
import lidraughts.hub.lightTeam.TeamId
import lidraughts.quote.Quote.quoteWriter
import lidraughts.rating.PerfType
import lidraughts.socket.Socket.SocketVersion
import lidraughts.user.User

final class SwissJson(
    swissColl: Coll,
    playerColl: Coll,
    pairingColl: Coll,
    standingApi: SwissStandingApi,
    rankingApi: SwissRankingApi,
    boardApi: SwissBoardApi,
    statsApi: SwissStatsApi,
    lightUserApi: lidraughts.user.LightUserApi
) {

  import SwissJson._
  import BsonHandlers._

  def api(swiss: Swiss) =
    swissJsonBase(swiss) ++ Json.obj(
      "rated" -> swiss.settings.rated
    )

  def apply(
    swiss: Swiss,
    me: Option[User],
    isInTeam: Boolean,
    reqPage: Option[Int] = None, // None = focus on me
    socketVersion: Option[SocketVersion] = None,
    playerInfo: Option[SwissPlayer.ViewExt] = None
  ): Fu[JsObject] =
    for {
      myInfo <- me.?? { fetchMyInfo(swiss, _) }
      page = reqPage orElse myInfo.map(_.page) getOrElse 1
      standing <- standingApi(swiss, page)
      podium <- podiumJson(swiss)
      boards <- boardApi(swiss.id)
      stats <- statsApi(swiss)
    } yield swissJsonBase(swiss) ++ Json
      .obj(
        "canJoin" -> {
          (swiss.isNotFinished && myInfo.exists(_.player.absent)) ||
            (myInfo.isEmpty && swiss.isEnterable && isInTeam)
        },
        "standing" -> standing,
        "boards" -> boards.map(boardJson)
      )
      .add("me" -> myInfo.map(myInfoJson))
      .add("joinTeam" -> (!isInTeam).option(swiss.teamId))
      .add("socketVersion" -> socketVersion.map(_.value))
      .add("playerInfo" -> playerInfo.map { playerJsonExt(swiss, _) })
      .add("podium" -> podium)
      .add("isRecentlyFinished" -> swiss.isRecentlyFinished)
      .add("stats" -> stats)
      .add("greatPlayer" -> GreatPlayer.wikiUrl(swiss.name).map { url =>
        Json.obj("name" -> swiss.name, "url" -> url)
      })

  def fetchMyInfo(swiss: Swiss, me: User): Fu[Option[MyInfo]] =
    playerColl.byId[SwissPlayer](SwissPlayer.makeId(swiss.id, me.id).value) flatMap {
      _ ?? { player =>
        updatePlayerRating(swiss, player, me) >>
          SwissPairing.fields { f =>
            (swiss.nbOngoing > 0)
              .?? {
                pairingColl
                  .find(
                    $doc(f.swissId -> swiss.id, f.players -> player.number, f.status -> SwissPairing.ongoing),
                    $doc(f.id -> true)
                  )
                  .sort($sort desc f.round)
                  .uno[Bdoc]
                  .dmap { _.flatMap(_.getAs[Game.ID](f.id)) }
              }
              .flatMap { gameId =>
                getOrGuessRank(swiss, player) dmap { rank =>
                  MyInfo(rank + 1, gameId, me, player).some
                }
              }
          }
      }
    }

  private def updatePlayerRating(swiss: Swiss, player: SwissPlayer, user: User): Funit =
    swiss.perfType
      .ifTrue(swiss.settings.rated)
      .map(user.perfs.apply)
      .filter(_.intRating != player.rating)
      .?? { perf =>
        SwissPlayer.fields { f =>
          playerColl
            .update(
              $id(SwissPlayer.makeId(swiss.id, user.id)),
              $set(f.rating -> perf.intRating, f.provisional -> perf.provisional)
            )
            .void
        }
      }

  // if the user is not yet in the cached ranking,
  // guess its rank based on other players scores in the DB
  private def getOrGuessRank(swiss: Swiss, player: SwissPlayer): Fu[Int] =
    rankingApi(swiss) flatMap {
      _ get player.number match {
        case Some(rank) => fuccess(rank)
        case None =>
          SwissPlayer.fields { f =>
            playerColl.countSel($doc(f.swissId -> player.swissId, f.score $gt player.score))
          }
      }
    }

  private def podiumJson(swiss: Swiss): Fu[Option[JsArray]] =
    swiss.isFinished ?? {
      SwissPlayer.fields { f =>
        playerColl
          .find($doc(f.swissId -> swiss.id))
          .sort($sort desc f.score)
          .list[SwissPlayer](3) map { top3 =>
            // check that the winner is still correctly denormalized
            top3.headOption.map(_.userId).filter(w => swiss.winnerId.fold(true)(w !=)) foreach {
              swissColl.updateField($id(swiss.id), "winnerId", _).void
            }
            JsArray {
              top3.map { player =>
                playerJsonBase(
                  swiss,
                  player,
                  lightUserApi.sync(player.userId) | LightUser.fallback(player.userId),
                  performance = true
                )
              }
            }.some
          }
      }
    }
}

object SwissJson {

  private def formatDate(date: DateTime) = ISODateTimeFormat.dateTime print date

  private def swissJsonBase(swiss: Swiss) =
    Json
      .obj(
        "id" -> swiss.id.value,
        "createdBy" -> swiss.createdBy,
        "startsAt" -> formatDate(swiss.startsAt),
        "name" -> swiss.name,
        "clock" -> swiss.clock,
        "variant" -> swiss.variant,
        "round" -> swiss.round,
        "nbRounds" -> swiss.actualNbRounds,
        "nbPlayers" -> swiss.nbPlayers,
        "nbOngoing" -> swiss.nbOngoing,
        "status" -> {
          if (swiss.isStarted) "started"
          else if (swiss.isFinished) "finished"
          else "created"
        }
      )
      .add("quote" -> swiss.isCreated.option(lidraughts.quote.Quote.one(swiss.id.value)))
      .add("nextRound" -> swiss.nextRoundAt.map { next =>
        Json.obj(
          "at" -> formatDate(next),
          "in" -> (next.getSeconds - nowSeconds).toInt.atLeast(0)
        )
      })

  private[swiss] def playerJson(swiss: Swiss, view: SwissPlayer.View): JsObject =
    playerJsonBase(swiss, view, false) ++ Json
      .obj(
        "sheetMin" -> swiss.allRounds
          .map(view.pairings.get)
          .zip(view.sheet.outcomes)
          .map {
            pairingJsonOrOutcome(view.player)
          }
          .mkString("|")
      )

  def playerJsonExt(swiss: Swiss, view: SwissPlayer.ViewExt): JsObject =
    playerJsonBase(swiss, view, true) ++ Json.obj(
      "sheet" -> swiss.allRounds
        .zip(view.sheet.outcomes)
        .reverse
        .map {
          case (round, outcome) =>
            view.pairings.get(round).fold[JsValue](JsString(outcomeJson(outcome))) { p =>
              pairingJson(view.player, p.pairing) ++
                Json.obj(
                  "user" -> p.player.user,
                  "rating" -> p.player.player.rating
                )
            }
        }
    )

  private def playerJsonBase(
    swiss: Swiss,
    view: SwissPlayer.Viewish,
    performance: Boolean
  ): JsObject =
    playerJsonBase(swiss, view.player, view.user, performance) ++
      Json.obj("rank" -> view.rank)

  private def playerJsonBase(
    swiss: Swiss,
    p: SwissPlayer,
    user: LightUser,
    performance: Boolean
  ): JsObject =
    Json
      .obj(
        "user" -> user,
        "rating" -> p.rating,
        "points" -> p.points,
        "tieBreak" -> p.tieBreak
      )
      .add("performance" -> (performance ?? p.performance))
      .add("provisional" -> p.provisional)
      .add("absent" -> p.absent)

  private def outcomeJson(outcome: SwissSheet.Outcome): String =
    outcome match {
      case SwissSheet.Absent => "absent"
      case SwissSheet.Late => "late"
      case SwissSheet.Bye => "bye"
      case _ => ""
    }

  private def pairingJsonMin(player: SwissPlayer, pairing: SwissPairing): String = {
    val status =
      if (pairing.isOngoing) "o"
      else pairing.resultFor(player.number).fold("d") { r => if (r) "w" else "l" }
    s"${pairing.gameId}$status"
  }

  private def pairingJson(player: SwissPlayer, pairing: SwissPairing) =
    Json
      .obj(
        "g" -> pairing.gameId
      )
      .add("o" -> pairing.isOngoing)
      .add("w" -> pairing.resultFor(player.number))
      .add("c" -> (pairing.white == player.number))

  private def pairingJsonOrOutcome(
    player: SwissPlayer
  ): ((Option[SwissPairing], SwissSheet.Outcome)) => String = {
    case (Some(pairing), _) => pairingJsonMin(player, pairing)
    case (_, outcome) => outcomeJson(outcome)
  }

  private def myInfoJson(i: MyInfo) =
    Json
      .obj(
        "rank" -> i.rank,
        "gameId" -> i.gameId,
        "id" -> i.user.id,
        "name" -> i.user.username,
        "absent" -> i.player.absent
      )

  private[swiss] def boardJson(b: SwissBoard.WithGame) =
    Json.obj(
      "id" -> b.game.id,
      "fen" -> (draughts.format.Forsyth exportBoard b.game.board),
      "lastMove" -> ~b.game.lastMoveKeys,
      "white" -> boardPlayerJson(b.board.white),
      "black" -> boardPlayerJson(b.board.black)
    )

  private def boardPlayerJson(player: SwissBoard.Player) =
    Json.obj(
      "rank" -> player.rank,
      "rating" -> player.rating,
      "user" -> player.user
    )

  implicit val variantWriter: OWrites[draughts.variant.Variant] = OWrites { v =>
    Json.obj(
      "key" -> v.key,
      "board" -> v.boardSize
    )
  }

  implicit private val roundNumberWriter: Writes[SwissRound.Number] = Writes[SwissRound.Number] { n =>
    JsNumber(n.value)
  }
  implicit private val pointsWriter: Writes[Swiss.Points] = Writes[Swiss.Points] { p =>
    JsNumber(p.value)
  }
  implicit private val tieBreakWriter: Writes[Swiss.TieBreak] = Writes[Swiss.TieBreak] { t =>
    JsNumber(t.value)
  }
  implicit private val performanceWriter: Writes[Swiss.Performance] = Writes[Swiss.Performance] { t =>
    JsNumber(t.value.toInt)
  }

  implicit private val clockWrites: OWrites[draughts.Clock.Config] = OWrites { clock =>
    Json.obj(
      "limit" -> clock.limitSeconds,
      "increment" -> clock.incrementSeconds
    )
  }

  implicit private val statsWrites: Writes[SwissStats] = Json.writes[SwissStats]
}
