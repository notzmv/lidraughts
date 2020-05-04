package controllers

import play.api.libs.json._
import play.api.mvc._

import lidraughts.api.Context
import lidraughts.app._
import lidraughts.chat.Chat
import lidraughts.swiss.{ Swiss => SwissModel }
import lidraughts.swiss.Swiss.{ Id => SwissId }
import views._

object Swiss extends LidraughtsController {

  private def env = Env.swiss

  private def swissNotFound(implicit ctx: Context) = NotFound(html.swiss.bits.notFound())

  def show(id: String) = Open { implicit ctx =>
    env.api.byId(SwissId(id)) flatMap { swissOption =>
      val page = getInt("page")
      negotiate(
        html = swissOption.fold(swissNotFound.fuccess) { swiss =>
          for {
            version <- env.version(swiss.id)
            isInTeam <- isCtxInTheTeam(swiss.teamId)
            json <- env.json(
              swiss = swiss,
              me = ctx.me,
              reqPage = page,
              socketVersion = version.some,
              isInTeam = isInTeam
            )
            canChat <- canHaveChat(swiss)
            chat <- canChat ?? Env.chat.api.userChat.cached
              .findMine(lidraughts.chat.Chat.Id(swiss.id.value), ctx.me)
              .dmap(some)
            _ <- chat ?? { c =>
              Env.user.lightUserApi.preloadMany(c.chat.userIds)
            }
          } yield Ok(html.swiss.show(swiss, json, chat))
        },
        api = _ =>
          swissOption.fold(notFoundJson("No such swiss tournament")) { swiss =>
            for {
              socketVersion <- getBool("socketVersion").??(env version swiss.id dmap some)
              isInTeam <- isCtxInTheTeam(swiss.teamId)
              json <- env.json(
                swiss = swiss,
                me = ctx.me,
                reqPage = page,
                socketVersion = socketVersion,
                isInTeam = isInTeam
              )
            } yield Ok(json)
          }
      )
    }
  }

  private def isCtxInTheTeam(teamId: lidraughts.team.Team.ID)(implicit ctx: Context) =
    ctx.userId.??(u => Env.team.cached.teamIds(u).dmap(_ contains teamId))

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

  def websocket(id: String, apiVersion: Int) = SocketOption[JsValue] { implicit ctx =>
    getSocketUid("sri") ?? { uid =>
      env.socketHandler.join(id, uid, ctx.me, getSocketVersion, apiVersion)
    }
  }

  def join(id: String) = AuthBody { implicit ctx => me =>
    NoLameOrBot {
      Env.team.cached.teamIds(me.id) flatMap { teamIds =>
        env.api.joinWithResult(SwissId(id), me, teamIds.contains) flatMap { result =>
          negotiate(
            html = Redirect(routes.Swiss.show(id)).fuccess,
            api = _ =>
              fuccess {
                if (result) jsonOkResult
                else BadRequest(Json.obj("joined" -> false))
              }
          )
        }
      }
    }
  }

  def edit(id: String) = Auth { implicit ctx => me =>
    WithEditableSwiss(id, me) { swiss =>
      Ok(html.swiss.form.edit(swiss, env.forms.edit(swiss))).fuccess
    }
  }

  def update(id: String) = AuthBody { implicit ctx => me =>
    WithEditableSwiss(id, me) { swiss =>
      implicit val req = ctx.body
      env.forms.edit(swiss)
        .bindFromRequest
        .fold(
          err => BadRequest(html.swiss.form.edit(swiss, err)).fuccess,
          data => env.api.update(swiss, data) inject Redirect(routes.Swiss.show(id))
        )
    }

  }
  def terminate(id: String) = Auth { implicit ctx => me =>
    WithEditableSwiss(id, me) { swiss =>
      env.api kill swiss
      Redirect(routes.Team.show(swiss.teamId)).fuccess
    }
  }

  private def WithEditableSwiss(id: String, me: lidraughts.user.User)(
    f: SwissModel => Fu[Result]
  )(implicit ctx: Context): Fu[Result] =
    env.api byId SwissId(id) flatMap {
      case Some(t) if (t.createdBy == me.id && !t.isFinished) || isGranted(_.ManageTournament) =>
        f(t)
      case Some(t) => Redirect(routes.Swiss.show(t.id.value)).fuccess
      case _ => notFound
    }

  private def canHaveChat(swiss: SwissModel)(implicit ctx: Context): Fu[Boolean] =
    (swiss.hasChat && ctx.noKid) ?? ctx.userId.?? {
      Env.team.api.belongsTo(swiss.teamId, _)
    }
}
