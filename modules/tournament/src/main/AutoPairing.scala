package lidraughts.tournament

import scala.concurrent.duration._

import lidraughts.game.{ Game, Player => GamePlayer, GameRepo, PovRef, Source, PerfPicker }
import lidraughts.user.User

final class AutoPairing(
    duelStore: DuelStore,
    onStart: Game.ID => Unit
) {

  def apply(tour: Tournament, pairing: Pairing, usersMap: Map[User.ID, User], ranking: Ranking): Fu[Game] = {
    val user1 = usersMap get pairing.user1 err s"Missing pairing user1 $pairing"
    val user2 = usersMap get pairing.user2 err s"Missing pairing user2 $pairing"
    val clock = tour.clock.toClock
    val perfPicker = PerfPicker.mainOrDefault(
      speed = draughts.Speed(clock.config),
      variant = tour.ratingVariant,
      daysPerTurn = none
    )
    val variant = if (tour.variant.standard && !tour.position.initialStandard) draughts.variant.FromPosition else tour.variant
    val opening = tour.openingTable.fold(-1 -> tour.position) { _.randomOpening }
    val game = Game.make(
      draughts = draughts.DraughtsGame(
        variantOption = Some(variant),
        fen = opening._2.some.filterNot(_.initialVariant(variant)).map(_.fen)
      ) |> { g =>
          val turns = g.player.fold(0, 1)
          g.copy(
            clock = clock.some,
            turns = turns,
            startedAtTurn = turns
          )
        },
      whitePlayer = GamePlayer.make(draughts.White, user1.some, perfPicker),
      blackPlayer = GamePlayer.make(draughts.Black, user2.some, perfPicker),
      mode = tour.mode,
      source = Source.Tournament,
      pdnImport = None
    ).withId(pairing.gameId)
      .withTournamentId(tour.id, tour.openingTable.map(_ => opening._1))
      .start
    (GameRepo insertDenormalized game) >>- {
      onStart(game.id)
      duelStore.add(
        tour = tour,
        game = game,
        p1 = (user1.username -> ~game.whitePlayer.rating),
        p2 = (user2.username -> ~game.blackPlayer.rating),
        ranking = ranking
      )
    } inject game
  }
}
