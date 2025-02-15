package controllers

import ornicar.scalalib.Zero
import play.api.data.Form
import play.api.http._
import play.api.libs.json.{ Json, JsObject, JsArray, JsString, JsValue, Writes }
import play.api.mvc._
import play.api.mvc.BodyParsers.parse
import scalatags.Text.Frag

import lidraughts.api.{ PageData, Context, HeaderContext, BodyContext }
import lidraughts.app._
import lidraughts.common.{ LidraughtsCookie, HTTPRequest, ApiVersion, Nonce, Lang }
import lidraughts.notify.Notification.Notifies
import lidraughts.oauth.{ OAuthScope, OAuthServer }
import lidraughts.security.{ Permission, Granter, FingerPrintedUser, FingerHash }
import lidraughts.user.{ UserContext, User => UserModel }

private[controllers] trait LidraughtsController
  extends Controller
  with ContentTypes
  with RequestGetter
  with ResponseWriter
  with LidraughtsSocket {

  protected val controllerLogger = lidraughts.log("controller")
  protected val authLogger = lidraughts.log("auth")

  protected implicit val LidraughtsResultZero = Zero.instance[Result](Results.NotFound)

  protected implicit final class LidraughtsPimpedResult(result: Result) {
    def fuccess = scala.concurrent.Future successful result
  }

  protected implicit def LidraughtsFragToResult(frag: Frag): Result = Ok(frag)

  protected implicit def makeApiVersion(v: Int) = ApiVersion(v)

  protected val jsonOkBody = Json.obj("ok" -> true)
  protected val jsonOkResult = Ok(jsonOkBody) as JSON

  protected implicit def LidraughtsFunitToResult(funit: Funit)(implicit ctx: Context): Fu[Result] =
    negotiate(
      html = fuccess(Ok("ok")),
      api = _ => fuccess(jsonOkResult)
    )

  implicit def ctxLang(implicit ctx: Context) = ctx.lang
  implicit def ctxReq(implicit ctx: Context) = ctx.req
  implicit def reqConfig(implicit req: RequestHeader) = ui.EmbedConfig(req)

  protected def NoCache(res: Result): Result = res.withHeaders(
    CACHE_CONTROL -> "no-cache, no-store, must-revalidate", EXPIRES -> "0"
  )

  protected def Open(f: Context => Fu[Result]): Action[Unit] =
    Open(parse.empty)(f)

  protected def Open[A](parser: BodyParser[A])(f: Context => Fu[Result]): Action[A] =
    Action.async(parser)(handleOpen(f, _))

  protected def OpenBody(f: BodyContext[_] => Fu[Result]): Action[AnyContent] =
    OpenBody(parse.anyContent)(f)

  protected def OpenBody[A](parser: BodyParser[A])(f: BodyContext[A] => Fu[Result]): Action[A] =
    Action.async(parser) { req =>
      CSRF(req) {
        reqToCtx(req) flatMap f
      }
    }

  protected def OpenOrScoped(selectors: OAuthScope.Selector*)(
    open: Context => Fu[Result],
    scoped: RequestHeader => UserModel => Fu[Result]
  ): Action[Unit] = Action.async(parse.empty) { req =>
    if (HTTPRequest isOAuth req) handleScoped(selectors)(scoped)(req)
    else handleOpen(open, req)
  }

  private def handleOpen(f: Context => Fu[Result], req: RequestHeader): Fu[Result] =
    CSRF(req) {
      reqToCtx(req) flatMap f
    }

  protected def AnonOrScoped(selectors: OAuthScope.Selector*)(
    anon: RequestHeader => Fu[Result],
    scoped: RequestHeader => UserModel => Fu[Result]
  ): Action[Unit] = Action.async(parse.empty) { req =>
    if (HTTPRequest isOAuth req) handleScoped(selectors)(scoped)(req)
    else anon(req)
  }

  protected def AuthOrScoped(selectors: OAuthScope.Selector*)(
    auth: Context => UserModel => Fu[Result],
    scoped: RequestHeader => UserModel => Fu[Result]
  ): Action[Unit] = Action.async(parse.empty) { req =>
    if (HTTPRequest isOAuth req) handleScoped(selectors)(scoped)(req)
    else handleAuth(auth, req)
  }

  protected def AuthOrScopedBody(selectors: OAuthScope.Selector*)(
    auth: BodyContext[_] => UserModel => Fu[Result],
    scoped: Request[_] => UserModel => Fu[Result]
  ): Action[AnyContent] = AuthOrScopedBody(parse.anyContent)(selectors)(auth, scoped)

  protected def AuthOrScopedBody[A](parser: BodyParser[A])(selectors: Seq[OAuthScope.Selector])(
    auth: BodyContext[A] => UserModel => Fu[Result],
    scoped: Request[A] => UserModel => Fu[Result]
  ): Action[A] =
    Action.async(parser) { req =>
      if (HTTPRequest isOAuth req) ScopedBody(parser)(selectors)(scoped)(req)
      else AuthBody(parser)(auth)(req)
    }

  protected def Auth(f: Context => UserModel => Fu[Result]): Action[Unit] =
    Auth(parse.empty)(f)

  protected def Auth[A](parser: BodyParser[A])(f: Context => UserModel => Fu[Result]): Action[A] =
    Action.async(parser) { handleAuth(f, _) }

  private def handleAuth(f: Context => UserModel => Fu[Result], req: RequestHeader): Fu[Result] =
    CSRF(req) {
      reqToCtx(req) flatMap { ctx =>
        ctx.me.fold(authenticationFailed(ctx))(f(ctx))
      }
    }

  protected def AuthBody(f: BodyContext[_] => UserModel => Fu[Result]): Action[AnyContent] =
    AuthBody(parse.anyContent)(f)

  protected def AuthBody[A](parser: BodyParser[A])(f: BodyContext[A] => UserModel => Fu[Result]): Action[A] =
    Action.async(parser) { req =>
      CSRF(req) {
        reqToCtx(req) flatMap { ctx =>
          ctx.me.fold(authenticationFailed(ctx))(f(ctx))
        }
      }
    }

  protected def Secure(perm: Permission.Selector)(f: Context => UserModel => Fu[Result]): Action[AnyContent] =
    Secure(perm(Permission))(f)

  protected def Secure(perm: Permission)(f: Context => UserModel => Fu[Result]): Action[AnyContent] =
    Secure(parse.anyContent)(perm)(f)

  protected def Secure[A](parser: BodyParser[A])(perm: Permission)(f: Context => UserModel => Fu[Result]): Action[A] =
    Auth(parser) { implicit ctx => me =>
      if (isGranted(perm)) f(ctx)(me) else authorizationFailed
    }

  protected def SecureF(s: UserModel => Boolean)(f: Context => UserModel => Fu[Result]): Action[AnyContent] =
    Auth(parse.anyContent) { implicit ctx => me =>
      if (s(me)) f(ctx)(me) else authorizationFailed
    }

  protected def SecureBody[A](parser: BodyParser[A])(perm: Permission)(f: BodyContext[A] => UserModel => Fu[Result]): Action[A] =
    AuthBody(parser) { implicit ctx => me =>
      if (isGranted(perm)) f(ctx)(me) else authorizationFailed
    }

  protected def SecureBody(perm: Permission.Selector)(f: BodyContext[_] => UserModel => Fu[Result]): Action[AnyContent] =
    SecureBody(parse.anyContent)(perm(Permission))(f)

  protected def Scoped[A](parser: BodyParser[A])(selectors: Seq[OAuthScope.Selector])(f: RequestHeader => UserModel => Fu[Result]): Action[A] =
    Action.async(parser)(handleScoped(selectors)(f))

  protected def Scoped(selectors: OAuthScope.Selector*)(f: RequestHeader => UserModel => Fu[Result]): Action[Unit] =
    Scoped(parse.empty)(selectors)(f)

  protected def ScopedBody[A](parser: BodyParser[A])(selectors: Seq[OAuthScope.Selector])(f: Request[A] => UserModel => Fu[Result]): Action[A] =
    Action.async(parser)(handleScoped(selectors)(f))

  protected def ScopedBody(selectors: OAuthScope.Selector*)(f: Request[_] => UserModel => Fu[Result]): Action[AnyContent] =
    ScopedBody(parse.anyContent)(selectors)(f)

  private def handleScoped[R <: RequestHeader](selectors: Seq[OAuthScope.Selector])(f: R => UserModel => Fu[Result])(req: R): Fu[Result] = {
    val scopes = OAuthScope select selectors
    Env.security.api.oauthScoped(req, scopes) flatMap {
      case Left(e @ lidraughts.oauth.OAuthServer.MissingScope(available)) =>
        lidraughts.mon.user.oauth.usage.failure()
        OAuthServer.responseHeaders(scopes, available) {
          Unauthorized(jsonError(e.message))
        }.fuccess
      case Left(e) =>
        lidraughts.mon.user.oauth.usage.failure()
        OAuthServer.responseHeaders(scopes, Nil) { Unauthorized(jsonError(e.message)) }.fuccess
      case Right(scoped) =>
        lidraughts.mon.user.oauth.usage.success()
        f(req)(scoped.user) map OAuthServer.responseHeaders(scopes, scoped.scopes)
    }
  }

  protected def OAuthSecure(perm: Permission.Selector)(f: RequestHeader => UserModel => Fu[Result]): Action[Unit] =
    Scoped() { req => me =>
      if (isGranted(perm, me)) f(req)(me)
      else fuccess(forbiddenJsonResult)
    }

  protected def SecureOrScoped(perm: Permission.Selector)(
    secure: Context => UserModel => Fu[Result],
    scoped: RequestHeader => UserModel => Fu[Result]
  ): Action[Unit] = Action.async(parse.empty) { req =>
    if (HTTPRequest isOAuth req) securedScopedAction(perm, req)(scoped)
    else Secure(parse.empty)(perm(Permission))(secure)(req)
  }
  protected def SecureOrScopedBody(perm: Permission.Selector)(
    secure: BodyContext[_] => UserModel => Fu[Result],
    scoped: RequestHeader => UserModel => Fu[Result]
  ): Action[AnyContent] = Action.async(parse.anyContent) { req =>
    if (HTTPRequest isOAuth req) securedScopedAction(perm, req.map(_ => ()))(scoped)
    else SecureBody(parse.anyContent)(perm(Permission))(secure)(req)
  }
  private def securedScopedAction(perm: Permission.Selector, req: Request[Unit])(f: RequestHeader => UserModel => Fu[Result]) =
    Scoped() { req => me =>
      if (isGranted(perm, me)) f(req)(me) else fuccess(forbiddenJsonResult)
    }(req)

  protected def Firewall[A <: Result](
    a: => Fu[A],
    or: => Fu[Result] = fuccess(Redirect(routes.Lobby.home()))
  )(implicit ctx: Context): Fu[Result] =
    if (Env.security.firewall accepts ctx.req) a
    else {
      authLogger.info(s"Firewall blocked ${ctx.req}")
      or
    }

  protected def NoTor(res: => Fu[Result])(implicit ctx: Context) =
    if (Env.security.tor isExitNode HTTPRequest.lastRemoteAddress(ctx.req))
      Unauthorized(views.html.auth.bits.tor()).fuccess
    else res

  protected def NoEngine[A <: Result](a: => Fu[A])(implicit ctx: Context): Fu[Result] =
    if (ctx.me.exists(_.engine)) Forbidden(views.html.site.message.noEngine).fuccess else a

  protected def NoBooster[A <: Result](a: => Fu[A])(implicit ctx: Context): Fu[Result] =
    if (ctx.me.exists(_.booster)) Forbidden(views.html.site.message.noBooster).fuccess else a

  protected def NoLame[A <: Result](a: => Fu[A])(implicit ctx: Context): Fu[Result] =
    NoEngine(NoBooster(a))

  protected def NoBot[A <: Result](a: => Fu[A])(implicit ctx: Context): Fu[Result] =
    if (ctx.me.exists(_.isBot)) Forbidden(views.html.site.message.noBot).fuccess else a

  protected def NoLameOrBot[A <: Result](a: => Fu[A])(implicit ctx: Context): Fu[Result] =
    NoLame(NoBot(a))

  protected def NoShadowban[A <: Result](a: => Fu[A])(implicit ctx: Context): Fu[Result] =
    if (ctx.me.exists(_.troll)) notFound else a

  protected def NoPlayban(a: => Fu[Result])(implicit ctx: Context): Fu[Result] =
    ctx.userId.??(Env.playban.api.currentBan) flatMap {
      _.fold(a) { ban =>
        negotiate(
          html = Lobby.renderHome(Results.Forbidden),
          api = _ => fuccess {
            Forbidden(jsonError(
              s"Banned from playing for ${ban.remainingMinutes} minutes. Reason: Too many aborts, unplayed games, or rage quits."
            )) as JSON
          }
        )
      }
    }

  protected def NoCurrentGame(a: => Fu[Result])(implicit ctx: Context): Fu[Result] =
    ctx.me.??(Env.current.preloader.currentGameMyTurn) flatMap {
      _.fold(a) { current =>
        negotiate(
          html = Lobby.renderHome(Results.Forbidden),
          api = _ => fuccess {
            Forbidden(jsonError(
              s"You are already playing ${current.opponent}"
            )) as JSON
          }
        )
      }
    }

  protected def NoPlaybanOrCurrent(a: => Fu[Result])(implicit ctx: Context): Fu[Result] =
    NoPlayban(NoCurrentGame(a))

  protected def JsonOk[A: Writes](fua: Fu[A]) = fua map { a =>
    Ok(Json toJson a) as JSON
  }
  protected def JsonOk[A: Writes](a: A) = Ok(Json toJson a) as JSON
  protected def JsonOk(body: JsValue): Result = Ok(body) as JSON

  protected def JsonOptionOk[A: Writes](fua: Fu[Option[A]])(implicit ctx: Context) = fua flatMap {
    _.fold(notFound(ctx))(a => fuccess(Ok(Json toJson a) as JSON))
  }

  protected def JsonOptionFuOk[A, B: Writes](fua: Fu[Option[A]])(op: A => Fu[B])(implicit ctx: Context) =
    fua flatMap { _.fold(notFound(ctx))(a => op(a) map { b => Ok(Json toJson b) as JSON }) }

  protected def JsOk(fua: Fu[String], headers: (String, String)*) =
    fua map { a => Ok(a) as JAVASCRIPT withHeaders (headers: _*) }

  protected def FormResult[A](form: Form[A])(op: A => Fu[Result])(implicit req: Request[_]): Fu[Result] =
    form.bindFromRequest.fold(
      form => fuccess(BadRequest(form.errors mkString "\n")),
      op
    )

  protected def FormFuResult[A, B: Writeable: ContentTypeOf](form: Form[A])(err: Form[A] => Fu[B])(op: A => Fu[Result])(implicit req: Request[_]) =
    form.bindFromRequest.fold(
      form => err(form) map { BadRequest(_) },
      data => op(data)
    )

  protected def FuRedirect(fua: Fu[Call]) = fua map { Redirect(_) }

  protected def OptionOk[A, B: Writeable: ContentTypeOf](fua: Fu[Option[A]])(op: A => B)(implicit ctx: Context): Fu[Result] =
    OptionFuOk(fua) { a => fuccess(op(a)) }

  protected def OptionFuOk[A, B: Writeable: ContentTypeOf](fua: Fu[Option[A]])(op: A => Fu[B])(implicit ctx: Context) =
    fua flatMap { _.fold(notFound(ctx))(a => op(a) map { Ok(_) }) }

  protected def OptionFuRedirect[A](fua: Fu[Option[A]])(op: A => Fu[Call])(implicit ctx: Context) =
    fua flatMap {
      _.fold(notFound(ctx))(a => op(a) map { b => Redirect(b) })
    }

  protected def OptionFuRedirectUrl[A](fua: Fu[Option[A]])(op: A => Fu[String])(implicit ctx: Context) =
    fua flatMap {
      _.fold(notFound(ctx))(a => op(a) map { b => Redirect(b) })
    }

  protected def OptionResult[A](fua: Fu[Option[A]])(op: A => Result)(implicit ctx: Context) =
    OptionFuResult(fua) { a => fuccess(op(a)) }

  protected def OptionFuResult[A](fua: Fu[Option[A]])(op: A => Fu[Result])(implicit ctx: Context) =
    fua flatMap { _.fold(notFound(ctx))(a => op(a)) }

  def notFound(implicit ctx: Context): Fu[Result] = negotiate(
    html =
      if (HTTPRequest isSynchronousHttp ctx.req) fuccess(Main renderNotFound ctx)
      else fuccess(Results.NotFound("Resource not found")),
    api = _ => notFoundJson("Resource not found")
  )

  def notFoundJson(msg: String = "Not found"): Fu[Result] = fuccess {
    NotFound(jsonError(msg))
  }

  def jsonError[A: Writes](err: A): JsObject = Json.obj("error" -> err)

  def ridiculousBackwardCompatibleJsonError(err: JsObject): JsObject =
    err ++ Json.obj("error" -> err)

  protected def notFoundReq(req: RequestHeader): Fu[Result] =
    reqToCtx(req) flatMap (x => notFound(x))

  protected def isGranted(permission: Permission.Selector, user: UserModel): Boolean =
    Granter(permission(Permission))(user)

  protected def isGranted(permission: Permission.Selector)(implicit ctx: Context): Boolean =
    isGranted(permission(Permission))

  protected def isGranted(permission: Permission)(implicit ctx: Context): Boolean =
    ctx.me ?? Granter(permission)

  protected def authenticationFailed(implicit ctx: Context): Fu[Result] =
    negotiate(
      html = fuccess {
        Redirect(routes.Auth.signup) withCookies LidraughtsCookie.session(Env.security.api.AccessUri, ctx.req.uri)
      },
      api = _ => ensureSessionId(ctx.req) {
        Unauthorized(jsonError("Login required"))
      }.fuccess
    )

  private val forbiddenJsonResult = Forbidden(jsonError("Authorization failed"))

  protected def authorizationFailed(implicit ctx: Context): Fu[Result] = negotiate(
    html =
      if (HTTPRequest isSynchronousHttp ctx.req) fuccess {
        lidraughts.mon.http.response.code403()
        Forbidden(views.html.site.message.authFailed)
      }
      else fuccess(Results.Forbidden("Authorization failed")),
    api = _ => fuccess(forbiddenJsonResult)
  )

  protected def ensureSessionId(req: RequestHeader)(res: Result): Result =
    if (req.session.data.contains(LidraughtsCookie.sessionId)) res
    else res withCookies LidraughtsCookie.makeSessionId(req)

  protected def negotiate(html: => Fu[Result], api: ApiVersion => Fu[Result])(implicit req: RequestHeader): Fu[Result] =
    lidraughts.api.Mobile.Api.requestVersion(req).fold(html) { v =>
      api(v) dmap (_ as JSON)
    }.dmap(_.withHeaders("Vary" -> "Accept"))

  protected def reqToCtx(req: RequestHeader): Fu[HeaderContext] = restoreUser(req) flatMap {
    case (d, impersonatedBy) =>
      val ctx = UserContext(req, d.map(_.user), impersonatedBy, lidraughts.i18n.I18nLangPicker(req, d.map(_.user)))
      pageDataBuilder(ctx, d.exists(_.hasFingerPrint)) dmap { Context(ctx, _) }
  }

  protected def reqToCtx[A](req: Request[A]): Fu[BodyContext[A]] =
    restoreUser(req) flatMap {
      case (d, impersonatedBy) =>
        val ctx = UserContext(req, d.map(_.user), impersonatedBy, lidraughts.i18n.I18nLangPicker(req, d.map(_.user)))
        pageDataBuilder(ctx, d.exists(_.hasFingerPrint)) dmap { Context(ctx, _) }
    }

  private def pageDataBuilder(ctx: UserContext, hasFingerPrint: Boolean): Fu[PageData] = {
    val isPage = HTTPRequest isSynchronousHttp ctx.req
    val nonce = isPage option Nonce.random
    ctx.me.fold(fuccess(PageData.anon(ctx.req, nonce, blindMode(ctx)))) { me =>
      import lidraughts.relation.actorApi.OnlineFriends
      Env.pref.api.getPref(me, ctx.req) zip {
        if (isPage) {
          Env.user.lightUserApi preloadUser me
          Env.relation.online.friendsOf(me.id) zip
            Env.team.api.nbRequests(me.id) zip
            Env.challenge.api.countInFor.get(me.id) zip
            Env.notifyModule.api.unreadCount(Notifies(me.id)).dmap(_.value) zip
            Env.mod.inquiryApi.forMod(me)
        } else fuccess {
          ((((OnlineFriends.empty, 0), 0), 0), none)
        }
      } map {
        case (pref, (onlineFriends ~ teamNbRequests ~ nbChallenges ~ nbNotifications ~ inquiry)) =>
          PageData(onlineFriends, teamNbRequests, nbChallenges, nbNotifications, pref,
            blindMode = blindMode(ctx),
            hasFingerprint = hasFingerPrint,
            inquiry = inquiry,
            nonce = nonce)
      }
    }
  }

  private def blindMode(implicit ctx: UserContext) =
    ctx.req.cookies.get(Env.api.Accessibility.blindCookieName) ?? { c =>
      c.value.nonEmpty && c.value == Env.api.Accessibility.hash
    }

  // user, impersonatedBy
  type RestoredUser = (Option[FingerPrintedUser], Option[UserModel])
  private def restoreUser(req: RequestHeader): Fu[RestoredUser] =
    Env.security.api restoreUser req addEffect {
      _ ifTrue (HTTPRequest isSocket req) foreach { d =>
        Env.current.system.lidraughtsBus.publish(lidraughts.user.User.Active(d.user), 'userActive)
      }
    } dmap {
      case Some(d) if !lidraughts.common.PlayApp.isProd =>
        d.copy(user = d.user
          .addRole(lidraughts.security.Permission.Beta.name)
          .addRole(lidraughts.security.Permission.Prismic.name)).some
      case d => d
    } flatMap {
      case None => fuccess(None -> None)
      case Some(d) => lidraughts.mod.Impersonate.impersonating(d.user) map {
        _.fold[RestoredUser](d.some -> None) { impersonated =>
          FingerPrintedUser(impersonated, FingerHash.impersonate.some).some -> d.user.some
        }
      }
    }

  protected val csrfCheck = Env.security.csrfRequestHandler.check _
  protected val csrfForbiddenResult = Forbidden("Cross origin request forbidden").fuccess

  private def CSRF(req: RequestHeader)(f: => Fu[Result]): Fu[Result] =
    if (csrfCheck(req)) f else csrfForbiddenResult

  protected def XhrOnly(res: => Fu[Result])(implicit ctx: Context) =
    if (HTTPRequest isXhr ctx.req) res else notFound

  protected def XhrOrRedirectHome(res: => Fu[Result])(implicit ctx: Context) =
    if (HTTPRequest isXhr ctx.req) res
    else Redirect(routes.Lobby.home).fuccess

  protected def Reasonable(page: Int, max: Int = 40, errorPage: => Fu[Result] = BadRequest("resource too old").fuccess)(result: => Fu[Result]): Fu[Result] =
    if (page < max) result else errorPage

  protected def NotForKids(f: => Fu[Result])(implicit ctx: Context) =
    if (ctx.kid) notFound else f

  protected def NotForBots(res: => Fu[Result])(implicit ctx: Context) =
    if (HTTPRequest.isBot(ctx.req)) notFound else res

  protected def OnlyHumans(result: => Fu[Result])(implicit ctx: lidraughts.api.Context) =
    if (HTTPRequest isBot ctx.req) fuccess(NotFound)
    else result

  protected def OnlyHumansAndFacebookOrTwitter(result: => Fu[Result])(implicit ctx: lidraughts.api.Context) =
    if (HTTPRequest isFacebookOrTwitterBot ctx.req) result
    else if (HTTPRequest isBot ctx.req) fuccess(NotFound)
    else result

  private val jsonGlobalErrorRenamer = {
    import play.api.libs.json._
    __.json update (
      (__ \ "global").json copyFrom (__ \ "").json.pick
    ) andThen (__ \ "").json.prune
  }

  protected def errorsAsJson(form: Form[_])(implicit lang: Lang): JsObject = {
    val json = JsObject(
      form.errors.groupBy(_.key).mapValues { errors =>
        JsArray {
          errors.map { e =>
            JsString(lidraughts.i18n.Translator.txt.literal(e.message, lidraughts.i18n.I18nDb.Site, e.args, lang))
          }
        }
      }
    )
    json validate jsonGlobalErrorRenamer getOrElse json
  }

  protected def jsonFormError(err: Form[_])(implicit lang: Lang) =
    fuccess(BadRequest(ridiculousBackwardCompatibleJsonError(errorsAsJson(err))))

  protected def jsonFormErrorDefaultLang(err: Form[_]) =
    jsonFormError(err)(lidraughts.i18n.defaultLang)

  protected def pageHit(implicit ctx: lidraughts.api.Context) =
    if (HTTPRequest isHuman ctx.req) lidraughts.mon.http.request.path(ctx.req.path)()

  protected val noProxyBufferHeader = "X-Accel-Buffering" -> "no"
  protected val noProxyBuffer = (res: Result) => res.withHeaders(noProxyBufferHeader)

  protected val pdnContentType = "application/x-draughts-pdn"
  protected val ndJsonContentType = "application/x-ndjson"
}
