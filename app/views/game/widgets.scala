package views.html
package game

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.game.{ Game, Pov, Player }

import controllers.routes

object widgets {

  private val separator = " • "

  def apply(
    games: Seq[Game],
    user: Option[lidraughts.user.User] = None,
    ownerLink: Boolean = false
  )(implicit ctx: Context): Frag = games map { g =>
    val fromPlayer = user flatMap g.player
    val firstPlayer = fromPlayer | g.player(g.naturalOrientation)
    st.article(cls := "game-row paginated")(
      a(cls := "game-row__overlay", href := gameLink(g, firstPlayer.color, ownerLink)),
      div(cls := "game-row__board")(
        views.html.board.bits.mini(Pov(g, firstPlayer))(span)
      ),
      div(cls := "game-row__infos")(
        div(cls := "header", dataIcon := bits.gameIcon(g))(
          div(cls := "header__text")(
            strong(
              if (g.imported) frag(
                span("IMPORT"),
                g.pdnImport.flatMap(_.user).map { user =>
                  frag(" ", trans.by(userIdLink(user.some, None, false)))
                },
                separator,
                if (g.variant.exotic) bits.variantLink(g.variant, g.variant.name.toUpperCase)
                else g.variant.name.toUpperCase
              )
              else frag(
                showClock(g),
                separator,
                g.perfType.fold(draughts.variant.FromPosition.name)(_.name),
                separator,
                if (g.rated) trans.rated.txt() else trans.casual.txt()
              )
            ),
            g.pdnImport.flatMap(_.date).fold(momentFromNow(g.createdAt))(frag(_)),
            g.tournamentId.map { tourId =>
              frag(separator, tournamentLink(tourId))
            } orElse
              g.simulId.map { simulId =>
                frag(separator, views.html.simul.bits.link(simulId))
              } orElse
              isGranted(_.Beta) ?? g.swissId.map { swissId =>
                frag(separator, views.html.swiss.bits.link(lidraughts.swiss.Swiss.Id(swissId)))
              },
            g.metadata.microMatchGameNr map { gameNr =>
              frag(separator, trans.microMatchGameX(gameNr))
            }
          )
        ),
        div(cls := "versus")(
          gamePlayer(g.variant, g.whitePlayer),
          div(cls := "swords", dataIcon := "U"),
          gamePlayer(g.variant, g.blackPlayer)
        ),
        div(cls := "result")(
          if (g.isBeingPlayed) trans.playingRightNow() else {
            if (g.finishedOrAborted)
              span(cls := g.winner.flatMap(w => fromPlayer.map(p => if (p == w) "win" else "loss")))(
                gameEndStatus(g),
                g.winner.map { winner =>
                  frag(
                    (g.status != draughts.Status.Mate).option(", "),
                    winner.color.fold(trans.whiteIsVictorious(), trans.blackIsVictorious())
                  )
                }
              )
            else g.turnColor.fold(trans.whitePlays(), trans.blackPlays())
          }
        ),
        if (g.turns > 0) {
          val pdnMoves = g.pdnMoves take 20
          div(cls := "opening")(
            (!g.fromPosition ?? g.opening) map { opening =>
              strong(opening.opening.fullName)
            },
            div(cls := "pdn")(
              pdnMoves.take(6).grouped(2).zipWithIndex map {
                case (Vector(w, b), i) => s"${i + 1}. $w $b"
                case (Vector(w), i) => s"${i + 1}. $w"
                case _ => ""
              } mkString " ",
              g.turns > 6 option s" ... ${1 + (g.turns - 1) / 2} moves "
            )
          )
        } else frag(br, br),
        g.metadata.analysed option
          div(cls := "metadata text", dataIcon := "")(trans.computerAnalysisAvailable()),
        g.pdnImport.flatMap(_.user).map { user =>
          div(cls := "metadata")(trans.pdnImportBy(userIdLink(user.some)))
        }
      )
    )
  }

  def showClock(game: Game)(implicit ctx: Context) = game.clock.map { clock =>
    frag(clock.config.show)
  } getOrElse {
    game.daysPerTurn.map { days =>
      span(title := trans.correspondence.txt())(
        if (days == 1) trans.oneDay() else trans.nbDays.pluralSame(days)
      )
    }.getOrElse {
      span(title := trans.unlimited.txt())("∞")
    }
  }

  private lazy val anonSpan = span(cls := "anon")(lidraughts.user.User.anonymous)

  private def gamePlayer(variant: draughts.variant.Variant, player: Player)(implicit ctx: Context) =
    div(cls := s"player ${player.color.name}")(
      player.playerUser map { playerUser =>
        frag(
          userIdLink(playerUser.id.some, withOnline = false),
          br,
          player.berserk option berserkIconSpan,
          playerUser.rating,
          player.provisional option "?",
          playerUser.ratingDiff map { d => frag(" ", showRatingDiff(d)) }
        )
      } getOrElse {
        player.aiLevel map { level =>
          frag(
            span(aiName(level, false)),
            br,
            aiRating(level)
          )
        } getOrElse {
          (player.nameSplit.fold[Frag](anonSpan) {
            case (name, rating) => frag(
              span(name),
              rating.map { r => frag(br, r) }
            )
          })
        }
      }
    )
}
