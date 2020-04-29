package views.html
package swiss

import play.api.libs.json.Json

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.String.html.safeJsonValue
import lidraughts.swiss.Swiss

import controllers.routes

object show {

  def apply(
    s: Swiss,
    data: play.api.libs.json.JsObject,
    chatOption: Option[lidraughts.chat.UserChat.Mine]
  )(implicit ctx: Context): Frag =
    views.html.base.layout(
      title = s"${s.name} #${s.id}",
      moreJs = frag(
        jsAt(s"compiled/lidraughts.swiss${isProd ?? (".min")}.js"),
        embedJsUnsafe(s"""LidraughtsSwiss.boot(${
          safeJsonValue(
            Json.obj(
              "data" -> data,
              "i18n" -> bits.jsI18n,
              "userId" -> ctx.userId,
              "chat" -> chatOption.map { c =>
                chat.json(
                  c.chat,
                  name = trans.chatRoom.txt(),
                  timeout = c.timeout,
                  public = true,
                  resourceId = lidraughts.chat.Chat.ResourceId(s"swiss/${c.chat.id}")
                )
              }
            )
          )
        })""")
      ),
      moreCss = cssTag("swiss.show"),
      draughtsground = false,
      openGraph = lidraughts.app.ui
        .OpenGraph(
          title = s"${s.name}: ${s.variant.name} ${s.clock.show} #${s.id}",
          url = s"$netBaseUrl${routes.Swiss.show(s.id.value).url}",
          description = s"${s.nbPlayers} players compete in the ${showEnglishDate(s.startsAt)} ${s.name} swiss tournament " +
            s"organized by ${teamIdToName(s.teamId)}. " +
            s.winnerId.fold("Winner is not yet decided.") { winnerId =>
              s"${usernameOrId(winnerId)} takes the prize home!"
            }
        )
        .some
    )(
        main(cls := "swiss")(
          st.aside(cls := "swiss__side")(
            swiss.side(s, chatOption.isDefined)
          ),
          div(cls := "swiss__main")(div(cls := "box"))
        )
      )
}
