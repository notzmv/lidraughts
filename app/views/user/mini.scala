package views.html.user

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.user.User

import controllers.routes

object mini {

  def apply(
    u: User,
    playing: Option[lidraughts.game.Pov],
    blocked: Boolean,
    followable: Boolean,
    rel: Option[lidraughts.relation.Relation],
    ping: Option[Int],
    crosstable: Option[lidraughts.game.Crosstable]
  )(implicit ctx: Context) = frag(
    div(cls := "upt__info")(
      div(cls := "upt__info__top")(
        div(cls := "left")(
          userLink(u, withPowerTip = false, withProfileName = true),
          u.profileOrDefault.countryInfo map { c =>
            val hasRoomForNameText = u.username.size + c.shortName.size < 20
            span(
              cls := "upt__info__top__country",
              title := (!hasRoomForNameText).option(c.name)
            )(
                img(cls := "flag", src := staticUrl(s"images/flags/${c.code}.png")),
                hasRoomForNameText option c.shortName
              )
          }
        ),
        ping map bits.signalBars
      ),
      if (u.engine && !ctx.me.has(u) && !isGranted(_.UserSpy))
        div(cls := "upt__info__warning")(trans.thisPlayerUsesDraughtsComputerAssistance())
      else
        div(cls := "upt__info__ratings")(u.best8Perfs map { showPerfRating(u, _) })
    ),
    ctx.userId map { myId =>
      frag(
        (myId != u.id && u.enabled) option div(cls := "upt__actions btn-rack")(
          a(dataIcon := "1", cls := "btn-rack__btn", title := trans.watchGames.txt(), href := routes.User.tv(u.username)),
          !blocked option frag(
            a(dataIcon := "c", cls := "btn-rack__btn", title := trans.chat.txt(), href := s"${routes.Message.form()}?user=${u.username}"),
            a(dataIcon := "U", cls := "btn-rack__btn", title := trans.challengeToPlay.txt(), href := s"${routes.Lobby.home()}?user=${u.username}#friend")
          ),
          views.html.relation.mini(u.id, blocked, followable, rel)
        ),
        crosstable.flatMap(_.nonEmpty) map { cross =>
          a(
            cls := "upt__score",
            href := s"${routes.User.games(u.username, "me")}#games",
            title := trans.nbGames.pluralTxt(cross.nbGames, cross.nbGames.localize)
          )(trans.yourScore(raw(s"""<strong>${cross.showScore(myId)}</strong> - <strong>${~cross.showOpponentScore(myId)}</strong>""")))
        }
      )
    },
    isGranted(_.UserSpy) option div(cls := "upt__mod")(
      span(
        trans.nbGames.plural(u.count.game, u.count.game.localize),
        " ", momentFromNowOnce(u.createdAt)
      ),
      (u.lameOrTroll || u.disabled) option span(cls := "upt__mod__marks")(mod.userMarks(u, None))
    ),
    playing.ifFalse(ctx.pref.isBlindfold).map {
      views.html.game.mini(_)
    }
  )
}
