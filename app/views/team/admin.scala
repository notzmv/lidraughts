package views.html.team

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

object admin {

  import trans.team._

  def changeOwner(t: lidraughts.team.Team, userIds: Iterable[lidraughts.user.User.ID])(implicit ctx: Context) = {

    val title = s"${t.name} - ${appointOwner.txt()}"

    bits.layout(title = title) {
      main(cls := "page-menu page-small")(
        bits.menu(none),
        div(cls := "page-menu__content box box-pad")(
          h1(title),
          p(trans.team.changeOwner()),
          br,
          br,
          postForm(cls := "kick", action := routes.Team.changeOwner(t.id))(
            userIds.toList.sorted.map { userId =>
              button(name := "userId", cls := "button button-empty button-no-upper confirm", value := userId)(
                usernameOrId(userId)
              )
            }
          )
        )
      )
    }
  }

  def kick(t: lidraughts.team.Team, userIds: Iterable[lidraughts.user.User.ID])(implicit ctx: Context) = {

    val title = s"${t.name} - ${kickSomeone.txt()}"

    bits.layout(title = title) {
      main(cls := "page-menu page-small")(
        bits.menu(none),
        div(cls := "page-menu__content box box-pad")(
          h1(title),
          p(whoToKick()),
          br,
          br,
          postForm(cls := "kick", action := routes.Team.kick(t.id))(
            userIds.toList.sorted.map { userId =>
              button(name := "userId", cls := "button button-empty button-no-upper confirm", value := userId)(
                usernameOrId(userId)
              )
            }
          )
        )
      )
    }
  }
}
