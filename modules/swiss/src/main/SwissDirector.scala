package lidraughts.swiss

import draughts.{ Black, Centis, Color, White }
import org.joda.time.DateTime

import lidraughts.db.dsl._
import lidraughts.game.{ IdGenerator, Game, GameRepo, Player => GamePlayer }

final private class SwissDirector(
    swissColl: Coll,
    playerColl: Coll,
    pairingColl: Coll,
    pairingSystem: PairingSystem,
    onStart: Game.ID => Unit
) {
  import BsonHandlers._

  // sequenced by SwissApi
  private[swiss] def startRound(from: Swiss): Fu[Option[Swiss]] =
    fetchPlayers(from)
      .zip(fetchPrevPairings(from))
      .flatMap {
        case (players, prevPairings) =>
          val swiss = from.startRound
          val pendings = pairingSystem(swiss, players, prevPairings)
          if (pendings.isEmpty) fuccess(none[Swiss]) // terminate
          else
            for {
              pairings <- pendings.collect {
                case Right(SwissPairing.Pending(w, b)) =>
                  IdGenerator.game dmap { id =>
                    SwissPairing(
                      id = id,
                      swissId = swiss.id,
                      round = swiss.round,
                      white = w,
                      black = b,
                      status = Left(SwissPairing.Ongoing)
                    )
                  }
              }.sequenceFu
              _ <- swissColl
                .update(
                  $id(swiss.id),
                  $unset("nextRoundAt") ++ $set(
                    "round" -> swiss.round,
                    "nbOngoing" -> pairings.size
                  )
                )
                .void
              date = DateTime.now
              pairingsBson = pairings.map { p =>
                pairingHandler.write(p) ++ $doc(SwissPairing.Fields.date -> date)
              }
              _ <- pairingColl.bulkInsert(pairingsBson.toStream, ordered = true).void
              playerMap = SwissPlayer.toMap(players)
              games = pairings.map(makeGame(swiss, playerMap))
              _ <- lidraughts.common.Future.applySequentially(games) { game =>
                GameRepo.insertDenormalized(game) >>- onStart(game.id)
              }
            } yield swiss.some
      }
      .recover {
        case PairingSystem.BBPairingException(msg, input) =>
          logger.warn(s"BBPairing ${from.id} $msg")
          logger.info(s"BBPairing ${from.id} $input")
          from.some
      }

  private def fetchPlayers(swiss: Swiss) = SwissPlayer.fields { f =>
    playerColl
      .find($doc(f.swissId -> swiss.id))
      .sort($sort asc f.number)
      .list[SwissPlayer]()
  }

  private def fetchPrevPairings(swiss: Swiss) = SwissPairing.fields { f =>
    pairingColl
      .find($doc(f.swissId -> swiss.id))
      .sort($sort asc f.round)
      .list[SwissPairing]()
  }

  private def makeGame(swiss: Swiss, players: Map[SwissPlayer.Number, SwissPlayer])(
    pairing: SwissPairing
  ): Game =
    Game
      .make(
        draughts = draughts.DraughtsGame(
          variantOption = Some(swiss.variant),
          fen = none
        ) |> { g =>
            val turns = g.player.fold(0, 1)
            g.copy(
              clock = swiss.clock.toClock
                .giveTime(White, Centis(300))
                .giveTime(Black, Centis(300))
                .start
                .some,
              turns = turns,
              startedAtTurn = turns
            )
          },
        whitePlayer = makePlayer(White, players get pairing.white err s"Missing pairing white $pairing"),
        blackPlayer = makePlayer(Black, players get pairing.black err s"Missing pairing black $pairing"),
        mode = draughts.Mode(swiss.rated),
        source = lidraughts.game.Source.Swiss,
        pdnImport = None
      )
      .withId(pairing.gameId)
      .withSwissId(swiss.id.value)
      .start

  private def makePlayer(color: Color, player: SwissPlayer) =
    lidraughts.game.Player.make(color, player.userId, player.rating, player.provisional)
}
