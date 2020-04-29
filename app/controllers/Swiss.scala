package controllers

import play.api.mvc._

import lidraughts.api.Context
import lidraughts.app._
import lidraughts.chat.Chat
import lidraughts.swiss.{ Swiss => SwissModel }
import views._

object Swiss extends LidraughtsController {

  private def env = Env.swiss

  private def swissNotFound(implicit ctx: Context) = NotFound(html.swiss.bits.notFound())

  def show(id: String) = Open { implicit ctx =>
    env.api.byId(SwissModel.Id(id)) flatMap {
      _.fold(swissNotFound.fuccess) { swiss =>
        for {
          version <- env.version(swiss.id)
          // rounds  <- env.roundsOf(swiss)
          json <- env.json(
            swiss = swiss,
            // rounds = rounds,
            me = ctx.me,
            socketVersion = version.some
          )
          canChat <- canHaveChat(swiss)
          chat <- canChat ?? Env.chat.api.userChat.cached
            .findMine(Chat.Id(swiss.id.value), ctx.me)
            .dmap(some)
          _ <- chat ?? { c => Env.user.lightUserApi.preloadMany(c.chat.userIds) }
        } yield Ok(html.swiss.show(swiss, json, chat))
      }
    }
  }

  def form(teamId: String) = Auth { implicit ctx => me =>
    Ok(html.swiss.form.create(env.forms.create, teamId)).fuccess
  }

  def create(teamId: String) = AuthBody { implicit ctx => me =>
    env.forms.create
      .bindFromRequest()(ctx.body)
      .fold(
        err => BadRequest(html.swiss.form.create(err, teamId)).fuccess,
        data =>
          Tournament.rateLimitCreation(me, false, ctx.req) {
            env.api.create(data, me, teamId) map { swiss =>
              Redirect(routes.Swiss.show(swiss.id.value))
            }
          }
      )
  }

  private def canHaveChat(swiss: SwissModel)(implicit ctx: Context): Fu[Boolean] =
    (swiss.hasChat && ctx.noKid) ?? ctx.userId.?? {
      Env.team.api.belongsTo(swiss.teamId, _)
    }
}
