package views.html
package tournament

import controllers.routes
import play.api.data.{ Field, Form }

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.tournament.TeamBattle
import lidraughts.tournament.Tournament
import lidraughts.user.User

object teamBattle {

  def edit(tour: Tournament, form: Form[_])(implicit ctx: Context) = views.html.base.layout(
    title = tour.fullName,
    moreCss = cssTag("tournament.form"),
    moreJs = jsTag("tournamentTeamBattleForm.js")
  )(main(cls := "page-small")(
      div(cls := "tour__form box box-pad")(
        h1(tour.fullName),
        if (tour.isFinished) p("This tournament is over, and the teams can no longer be updated.")
        else p("List the teams that will compete in this battle."),
        postForm(cls := "form3", action := routes.Tournament.teamBattleUpdate(tour.id))(
          form3.group(form("teams"), raw("One team per line. Use the auto-completion."),
            help = frag("You can copy-paste this list from a tournament to another!", br,
              "You can't remove a team if a player has already joined the tournament with it").some)(
              form3.textarea(_)(rows := 10, tour.isFinished.option(disabled))
            ),
          form3.group(form("nbLeaders"), raw("Number of leaders per team. The sum of their score is the score of the team."),
            help = frag("You really shouldn't change this value after the tournament has started!").some)(
            form3.input(_)(tpe := "number")
          ),
          form3.globalError(form),
          form3.submit("Update teams")(tour.isFinished.option(disabled))
        )
      )
    ))

  private val scoreTag = tag("score")

  def standing(
    tour: Tournament,
    battle: TeamBattle,
    standing: List[TeamBattle.RankedTeam]
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = tour.fullName,
      moreCss = cssTag("tournament.show.team-battle")
    )(
        main(cls := "box")(
          h1(a(href := routes.Tournament.show(tour.id))(tour.fullName)),
          table(cls := "slist slist-pad tour__team-standing tour__team-standing--full")(
            tbody(
              standing.map { t =>
                tr(
                  td(cls := "rank")(t.rank),
                  td(cls := "team")(teamLink(t.teamId)),
                  td(cls := "players")(
                    fragList(
                      t.leaders.map { l =>
                        scoreTag(dataHref := routes.User.show(l.userId), cls := "user-link ulpt")(l.score)
                      },
                      "+"
                    )
                  ),
                  td(cls := "total")(t.score)
                )
              }
            )
          )
        )
      )
}
