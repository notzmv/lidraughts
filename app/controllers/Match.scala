package controllers

import play.api.libs.json._
import play.api.mvc._

import lidraughts.api.Context
import lidraughts.app._
import lidraughts.common.HTTPRequest
import lidraughts.simul.{ Simul => Sim }
import lidraughts.chat.Chat
import views._

object Match extends LidraughtsController {

  private def env = Env.matches

  private def matchNotFound(implicit ctx: Context) = NotFound(html.matches.notFound())

  val home = Open { implicit ctx =>
    fetchSimuls map {
      case ((created, started), finished) =>
        Ok(html.matches.home(created, started, finished))
    }
  }

  val homeReload = Open { implicit ctx =>
    fetchSimuls map {
      case ((created, started), finished) =>
        Ok(html.matches.homeInner(created, started, finished))
    }
  }

  private def fetchSimuls =
    env.allCreated.get zip env.repo.allStarted zip env.repo.allFinished(30)

  def show(id: String) = Open { implicit ctx =>
    env.repo find id flatMap {
      _.fold(matchNotFound.fuccess) { mtch =>
        for {
          version <- env.version(mtch.id)
          json <- env.jsonView(mtch)
          chat <- canHaveChat ?? Env.chat.api.userChat.cached.findMine(Chat.Id(mtch.id), ctx.me).map(some)
          _ <- chat ?? { c => Env.user.lightUserApi.preloadMany(c.chat.userIds) }
          stream <- Env.streamer.liveStreamApi one mtch.hostId
        } yield html.matches.show(mtch, version, json, chat, stream)
      }
    } map NoCache
  }

  private[controllers] def canHaveChat(implicit ctx: Context): Boolean = ctx.me ?? { u =>
    if (ctx.kid) false
    else Env.chat.panic allowed u
  }

  def form = Auth { implicit ctx => me =>
    NoEngine {
      Ok(html.matches.form(env.forms.create, env.forms)).fuccess
    }
  }

  def create = AuthBody { implicit ctx => implicit me =>
    NoEngine {
      implicit val req = ctx.body
      env.forms.create.bindFromRequest.fold(
        err => BadRequest(html.matches.form(err, env.forms)).fuccess,
        setup => env.api.create(setup, me) map { mtch =>
          Redirect(routes.Match.show(mtch.id))
        }
      )
    }
  }

}
