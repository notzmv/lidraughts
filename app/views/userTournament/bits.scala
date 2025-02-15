package views.html
package userTournament

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.paginator.Paginator
import lidraughts.user.User

import controllers.routes

object bits {

  def best(u: User, pager: Paginator[lidraughts.tournament.LeaderboardApi.TourEntry])(implicit ctx: Context) =
    layout(
      u,
      title = s"${u.username} best tournaments",
      path = "best",
      moreJs = infiniteScrollTag
    ) {
      views.html.userTournament.list(u, "best", pager, "BEST")
    }

  def recent(u: User, pager: Paginator[lidraughts.tournament.LeaderboardApi.TourEntry])(implicit ctx: Context) =
    layout(
      u,
      title = s"${u.username} recent tournaments",
      path = "recent",
      moreJs = infiniteScrollTag
    ) {
      views.html.userTournament.list(u, "recent", pager, pager.nbResults.toString)
    }

  def layout(u: User, title: String, path: String, moreJs: Frag = emptyFrag)(body: Frag)(implicit ctx: Context) =
    views.html.base.layout(
      title = title,
      moreCss = cssTag("user-tournament"),
      moreJs = moreJs
    ) {
        main(cls := "page-menu")(
          st.nav(cls := "page-menu__menu subnav")(
            a(cls := path.active("created"), href := routes.UserTournament.path(u.username, "created"))(
              trans.createdTournaments()
            ),
            a(cls := path.active("recent"), href := routes.UserTournament.path(u.username, "recent"))(
              trans.recentlyPlayed()
            ),
            a(cls := path.active("best"), href := routes.UserTournament.path(u.username, "best"))(
              trans.bestResults()
            ),
            a(cls := path.active("chart"), href := routes.UserTournament.path(u.username, "chart"))(
              "Stats"
            )
          ),
          div(cls := "page-menu__content box")(body)
        )
      }
}
