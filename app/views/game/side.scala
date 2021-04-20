package views.html
package game

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

object side {

  private val separator = " • "
  private val dataUserTv = attr("data-user-tv")
  private val dataTime = attr("data-time")

  def apply(
    pov: lidraughts.game.Pov,
    initialFen: Option[draughts.format.FEN],
    tour: Option[lidraughts.tournament.TourAndTeamVs],
    simul: Option[lidraughts.simul.Simul],
    userTv: Option[lidraughts.user.User] = None,
    bookmarked: Boolean
  )(implicit ctx: Context): Option[Frag] = ctx.noBlind option frag(
    meta(pov, initialFen, tour, simul, userTv, bookmarked),
    pov.game.userIds.filter(isStreaming) map views.html.streamer.bits.contextual
  )

  def meta(
    pov: lidraughts.game.Pov,
    initialFen: Option[draughts.format.FEN],
    tour: Option[lidraughts.tournament.TourAndTeamVs],
    simul: Option[lidraughts.simul.Simul],
    userTv: Option[lidraughts.user.User] = None,
    bookmarked: Boolean
  )(implicit ctx: Context): Option[Frag] = ctx.noBlind option {
    import pov._
    div(cls := "game__meta")(
      st.section(
        div(cls := "game__meta__infos", dataIcon := bits.gameIcon(game))(
          div(
            div(cls := "header")(
              div(cls := "setup")(
                views.html.bookmark.toggle(game, bookmarked),
                if (game.imported) div(
                  a(href := routes.Importer.importGame, title := trans.importGame.txt())("IMPORT"),
                  separator,
                  if (game.variant.exotic)
                    bits.variantLink(game.variant, game.variant.name.toUpperCase, initialFen = initialFen)
                  else
                    game.variant.name.toUpperCase
                )
                else frag(
                  widgets showClock game,
                  separator,
                  (if (game.rated) trans.rated else trans.casual).txt(),
                  separator,
                  if (game.variant.exotic)
                    bits.variantLink(game.variant, game.variant.name.toUpperCase, initialFen = initialFen)
                  else game.perfType.map { pt =>
                    span(title := pt.title)(pt.shortName)
                  }
                )
              ),
              game.pdnImport.flatMap(_.date).map(frag(_)) getOrElse {
                frag(if (game.isBeingPlayed) trans.playingRightNow() else momentFromNow(game.createdAt))
              }
            ),
            game.pdnImport.flatMap(_.date).map { date =>
              small(
                game.pdnImport.flatMap(_.user).map { user =>
                  trans.pdnImportBy(userIdLink(user.some, None, false))
                }
              )
            }
          )
        ),
        div(cls := "game__meta__players")(
          game.players.map { p =>
            frag(
              div(cls := s"player color-icon is ${p.color.name} text")(
                playerLink(p, withOnline = false, withDiff = true, withBerserk = true)
              ),
              tour.flatMap(_.teamVs).map(_.teams(p.color)) map { teamLink(_, false)(cls := "team") }
            )
          }
        )
      ),
      game.finishedOrAborted option {
        st.section(cls := "status")(
          gameEndStatus(game),
          game.winner.map { winner =>
            frag(
              (game.status != draughts.Status.Mate).option(separator),
              winner.color.fold(trans.whiteIsVictorious, trans.blackIsVictorious)()
            )
          }
        )
      },

      userTv.map { u =>
        st.section(cls := "game__tv")(
          h2(cls := "top user-tv text", dataUserTv := u.id, dataIcon := "1")(u.titleUsername)
        )
      },

      tour.map { t =>
        st.section(cls := "game__tournament")(
          a(cls := "text", dataIcon := "g", href := routes.Tournament.show(t.tour.id))(t.tour.fullName),
          div(cls := "clock", dataTime := t.tour.secondsToFinish)(t.tour.clockStatus)
        )
      } orElse game.tournamentId.map { tourId =>
        st.section(cls := "game__tournament-link")(tournamentLink(tourId))
      } orElse isGranted(_.Beta) ?? game.swissId.map { swissId =>
        st.section(cls := "game__tournament-link")(
          views.html.swiss.bits.link(lidraughts.swiss.Swiss.Id(swissId))
        )
      },

      game.metadata.microMatch map { m =>
        st.section(cls := "game__micro-match")(
          if (m.startsWith("1:") && m.length == 10) frag(
            trans.microMatch(), ": ",
            a(cls := "text", href := routes.Round.watcher(m.drop(2), (!pov.color).name))(trans.gameNumberX(1)), " ",
            span(cls := "current")(trans.gameNumberX(2))
          )
          else if (m.startsWith("2:") && m.length == 10) frag(
            trans.microMatch(), ": ",
            span(cls := "current")(trans.gameNumberX(1)), " ",
            a(cls := "text", href := routes.Round.watcher(m.drop(2), (!pov.color).name))(trans.gameNumberX(2))
          )
          else trans.microMatchGameX(1)
        )
      },

      simul.map { sim =>
        frag(
          st.section(cls := "game__simul-link")(
            a(href := routes.Simul.show(sim.id))(sim.fullName), br,
            game.playerByUserId(sim.hostId).map { p =>
              frag(
                playerLink(p, withOnline = false, withRating = false, withDiff = false),
                " vs ",
                trans.nbOpponents(sim.pairings.length)
              )
            },
            !sim.isFinished option frag(
              br,
              span(cls := "simul-ongoing")(trans.nbGamesOngoing(sim.ongoing))
            )
          ),
          !sim.isFinished option st.section(cls := "game__simul__infos")(
            simulStats(sim)
          )
        )
      }
    )
  }

  private def simulStats(sim: lidraughts.simul.Simul)(implicit ctx: Context) =
    !sim.isFinished option div(cls := "simul-stats")(
      sim.targetPct.map { target =>
        frag(
          trans.targetWinningPercentage(s"$target%"),
          br
        )
      },
      trans.currentWinningPercentage(if (sim.finished == 0) "-" else sim.winningPercentageStr),
      sim.targetPct.map { _ =>
        frag(
          br,
          trans.relativeScoreRequired(span(cls := s"simul_rel_${sim.id}")(sim.relativeScoreStr(ctx.pref.draughtsResult)))
        )
      }
    )
}
