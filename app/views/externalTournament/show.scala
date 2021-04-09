package views.html
package externalTournament

import play.api.libs.json.Json

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.externalTournament.ExternalTournament
import lidraughts.user.User

import controllers.routes

object show {

  def apply(
    tour: ExternalTournament,
    upcoming: List[lidraughts.challenge.Challenge],
    finished: List[lidraughts.game.Game]
  )(implicit ctx: Context) = views.html.base.layout(
    title = tour.title,
    moreCss = cssTag("tour-ext"),
    draughtsground = false
  )(
      main(cls := s"page-small box box-pad tour-ext")(
        h1(cls := "text tour-title")(tour.title),
        h2("Upcoming games"),
        table(cls := "slist slist-pad")(tbody(
          upcoming map { c =>
            val challenger = c.challenger.fold(
              _ => User.anonymous,
              reg => s"${usernameOrId(reg.id)} (${reg.rating.show})"
            )
            val players = c.destUser.fold(s"Challenge from $challenger") { dest =>
              val destUser = s"${usernameOrId(dest.id)} (${dest.rating.show})"
              c.finalColor.fold(s"$challenger vs $destUser", s"$destUser vs $challenger")
            }
            tr(
              td(
                c.external.flatMap(_.startsAt).fold(frag("Unknown"))(absClientDateTime)
              ),
              td(
                a(href := routes.Challenge.show(c.id))(players)
              )
            )
          }
        )),
        h2("Finished games"),
        table(cls := "slist slist-pad")(tbody(
          finished map { g =>
            val players = s"${playerText(g.whitePlayer)} vs ${playerText(g.blackPlayer)}"
            tr(
              td(
                absClientDateTime(g.createdAt)
              ),
              td(
                a(href := routes.Round.watcher(g.id, "white"))(players)
              )
            )
          }
        ))
      )
    )
}
