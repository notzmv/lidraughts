package controllers

import play.api.libs.json.JsValue
import play.api.mvc._

import lidraughts.api.Context
import lidraughts.app._
import lidraughts.relay.{ Relay => RelayModel }
import views._

object Relay extends LidraughtsController {

  private val env = Env.relay

  def index(page: Int) = Open { implicit ctx =>
    Reasonable(page) {
      for {
        fresh <- (page == 1).??(env.api.fresh(ctx.me) map some)
        pager <- env.pager.finished(ctx.me, page)
      } yield Ok(html.relay.index(fresh, pager, routes.Relay.index()))
    }
  }

  def form = Secure(_.Relay) { implicit ctx => me =>
    NoLame {
      Ok(html.relay.form.create(env.forms.create)).fuccess
    }
  }

  def create = SecureBody(_.Relay) { implicit ctx => me =>
    implicit val req = ctx.body
    env.forms.create.bindFromRequest.fold(
      err => BadRequest(html.relay.form.create(err)).fuccess,
      setup => env.api.create(setup, me) map { relay =>
        Redirect(showRoute(relay))
      }
    )
  }

  def edit(slug: String, id: String) = Auth { implicit ctx => me =>
    OptionFuResult(env.api.byIdAndContributor(id, me)) { relay =>
      Ok(html.relay.form.edit(relay, env.forms.edit(relay))).fuccess
    }
  }

  def update(slug: String, id: String) = AuthBody { implicit ctx => me =>
    OptionFuResult(env.api.byIdAndContributor(id, me)) { relay =>
      implicit val req = ctx.body
      env.forms.edit(relay).bindFromRequest.fold(
        err => BadRequest(html.relay.form.edit(relay, err)).fuccess,
        data => env.api.update(relay) { data.update(_, me) } map { r =>
          Redirect(showRoute(r))
        }
      )
    }
  }

  def show(slug: String, id: String) = Open { implicit ctx =>
    WithRelay(slug, id) { relay =>
      val sc =
        if (relay.sync.ongoing) Env.study.chapterRepo relaysAndTagsByStudyId relay.studyId flatMap { chapters =>
          chapters.find(_.looksAlive) orElse chapters.headOption match {
            case Some(chapter) => Env.study.api.byIdWithChapter(relay.studyId, chapter.id)
            case None => Env.study.api byIdWithChapter relay.studyId
          }
        }
        else Env.study.api byIdWithChapter relay.studyId
      sc flatMap { _ ?? { doShow(relay, _) } }
    }
  }

  def chapter(slug: String, id: String, chapterId: String) = Open { implicit ctx =>
    WithRelay(slug, id) { relay =>
      Env.study.api.byIdWithChapter(relay.studyId, chapterId) flatMap {
        _ ?? { doShow(relay, _) }
      }
    }
  }

  private def WithRelay(slug: String, id: String)(f: RelayModel => Fu[Result])(implicit ctx: Context): Fu[Result] =
    OptionFuResult(env.api byId id) { relay =>
      if (relay.slug != slug) Redirect(showRoute(relay)).fuccess
      else f(relay)
    }

  private def doShow(relay: RelayModel, oldSc: lidraughts.study.Study.WithChapter)(implicit ctx: Context): Fu[Result] =
    Study.CanViewResult(oldSc.study) {
      for {
        (sc, studyData) <- Study.getJsonData(oldSc)
        data = env.jsonView.makeData(relay, studyData)
        chat <- Study.chatOf(sc.study)
        sVersion <- Env.study.version(sc.study.id)
        streams <- Study.streamsOf(sc.study)
      } yield Ok(html.relay.show(relay, sc.study, data, chat, sVersion, streams))
    }

  def websocket(id: String, apiVersion: Int) = SocketOption[JsValue] { implicit ctx =>
    get("sri") ?? { uid =>
      env.api byId id flatMap {
        _ ?? { relay =>
          env.socketHandler.join(
            relayId = relay.id,
            uid = lidraughts.socket.Socket.Uid(uid),
            user = ctx.me,
            getSocketVersion,
            apiVersion
          ) map some
        }
      }
    }
  }

  private def showRoute(r: RelayModel) = routes.Relay.show(r.slug, r.id.value)

  private implicit def makeRelayId(id: String): RelayModel.Id = RelayModel.Id(id)
  private implicit def makeChapterId(id: String): lidraughts.study.Chapter.Id = lidraughts.study.Chapter.Id(id)
}
