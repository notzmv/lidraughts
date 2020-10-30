package controllers

import play.api.mvc._

import lidraughts.api.Context
import lidraughts.app._
import views._

object Swiss extends LidraughtsController {

  private def env = Env.swiss

  def show(id: String) = Open { implicit ctx =>
    ???
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
          env.api.create(data, me, teamId) map { swiss =>
            Redirect(routes.Swiss.show(swiss.id.value))
          }
      )
  }
}
