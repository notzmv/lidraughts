package lidraughts.challenge

import Challenge.TimeControl
import lidraughts.game.{ Game, Pov, PovRef, GameRepo }
import lidraughts.user.{ User, UserRepo }

object ChallengeMaker {

  def makeRematchFor(gameId: Game.ID, dest: User): Fu[Option[Challenge]] =
    GameRepo game gameId flatMap {
      _ ?? { game =>
        game.opponentByUserId(dest.id).flatMap(_.userId) ?? UserRepo.byId flatMap {
          _ ?? { challenger =>
            Pov(game, challenger) ?? { pov =>
              makeRematch(pov, challenger, dest) map some
            }
          }
        }
      }
    }

  def makeRematchOf(game: Game, challenger: User): Fu[Option[Challenge]] =
    Pov.ofUserId(game, challenger.id) ?? { pov =>
      pov.opponent.userId ?? UserRepo.byId flatMap {
        _ ?? { dest =>
          makeRematch(pov, challenger, dest) map some
        }
      }
    }

  // pov of the challenger
  private def makeRematch(pov: Pov, challenger: User, dest: User): Fu[Challenge] =
    GameRepo initialFen pov.game map { initialFen =>
      Challenge.make(
        variant = pov.game.variant,
        fenVariant = pov.game.variant.some,
        initialFen = initialFen,
        timeControl = (pov.game.clock, pov.game.daysPerTurn) match {
          case (Some(clock), _) => TimeControl.Clock(clock.config)
          case (_, Some(days)) => TimeControl.Correspondence(days)
          case _ => TimeControl.Unlimited
        },
        mode = pov.game.mode,
        color = (!pov.color).name,
        challenger = Right(challenger),
        destUser = dest.some,
        rematchOf = pov.gameId.some,
        microMatch = pov.game.metadata.microMatchGameNr.contains(2)
      )
    }
}
