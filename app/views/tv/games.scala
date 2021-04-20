package views.html.tv

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

object games {

  def apply(channel: lidraughts.tv.Tv.Channel, povs: List[lidraughts.game.Pov], champions: lidraughts.tv.Tv.Champions)(implicit ctx: Context) =
    views.html.base.layout(
      title = s"${channel.name} • ${trans.currentGames.txt()}",
      moreCss = cssTag("tv.games")
    ) {
        main(cls := "page-menu tv-games")(
          st.aside(cls := "page-menu__menu")(
            side.channels(channel.some, champions, "/games")
          ),
          div(cls := "page-menu__content now-playing")(
            povs map { views.html.game.mini(_) }
          )
        )
      }
}
