package views.html
package forum

import play.api.data.Form

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.paginator.Paginator

import controllers.routes

object topic {

  def form(categ: lidraughts.forum.Categ, form: Form[_], captcha: lidraughts.common.Captcha)(implicit ctx: Context) =
    views.html.base.layout(
      title = "New forum topic",
      moreCss = cssTag("forum"),
      moreJs = frag(
        jsTag("forum-post.js"),
        captchaTag
      )
    ) {
        main(cls := "forum forum-topic topic-form page-small box box-pad")(
          h1(
            a(href := routes.ForumCateg.show(categ.slug), dataIcon := "I", cls := "text"),
            categ.name
          ),
          st.section(cls := "warning")(
            h2(dataIcon := "!", cls := "text")(trans.important()),
            p(
              trans.yourQuestionMayHaveBeenAnswered(
                strong(a(href := routes.Main.faq)(trans.inTheFAQ()))
              )
            ),
            p(
              trans.toReportSomeoneForCheatingOrBadBehavior(
                strong(a(href := routes.Report.form)(trans.useTheReportForm()))
              )
            ),
            p(
              trans.toRequestSupport(
                strong(a(href := routes.Page.contact())(trans.tryTheContactPage()))
              )
            )
          ),

          postForm(cls := "form3", action := routes.ForumTopic.create(categ.slug))(
            form3.group(form("name"), trans.subject())(form3.input(_)(autofocus)),
            form3.group(form("post")("text"), trans.message())(form3.textarea(_, klass = "post-text-area")(rows := 10)),
            views.html.base.captcha(form("post"), captcha),
            form3.actions(
              a(href := routes.ForumCateg.show(categ.slug))(trans.cancel()),
              isGranted(_.PublicMod) option
                form3.submit(frag("Create as mod"), nameValue = (form("post")("modIcon").name, "true").some, icon = "".some),
              form3.submit(trans.createTheTopic())
            )
          )
        )
      }

  def show(
    categ: lidraughts.forum.Categ,
    topic: lidraughts.forum.Topic,
    posts: Paginator[lidraughts.forum.Post],
    formWithCaptcha: Option[FormWithCaptcha],
    unsub: Option[Boolean],
    canModCateg: Boolean
  )(implicit ctx: Context) = views.html.base.layout(
    title = s"${topic.name} • page ${posts.currentPage}/${posts.nbPages} • ${categ.name}",
    moreJs = frag(
      jsTag("forum-post.js"),
      formWithCaptcha.isDefined option captchaTag,
      jsAt("compiled/embed-analyse.js")
    ),
    moreCss = cssTag("forum"),
    openGraph = lidraughts.app.ui.OpenGraph(
      title = topic.name,
      url = s"$netBaseUrl${routes.ForumTopic.show(categ.slug, topic.slug, posts.currentPage).url}",
      description = shorten(posts.currentPageResults.headOption.??(_.text), 152)
    ).some
  ) {
      val teamOnly = categ.team.filterNot(myTeam)
      val pager = bits.pagination(routes.ForumTopic.show(categ.slug, topic.slug, 1), posts, showPost = true)
      val content = frag(
        h1(
          a(
            href := routes.ForumCateg.show(categ.slug),
            dataIcon := "I",
            cls := "text"
          ),
          topic.name
        ),
        pager,
        div(cls := "forum-topic__posts embed_analyse")(
          posts.currentPageResults.map { p =>
            post.show(
              categ,
              topic,
              p,
              s"${routes.ForumTopic.show(categ.slug, topic.slug, posts.currentPage)}#${p.number}",
              canModCateg = canModCateg,
              canReact = teamOnly.isEmpty
            )
          }
        ),

        div(cls := "forum-topic__actions")(
          if (posts.hasNextPage) emptyFrag
          else if (topic.isOld)
            p(trans.thisTopicIsArchived())
          else if (formWithCaptcha.isDefined)
            h2(id := "reply")(trans.replyToThisTopic())
          else if (topic.closed) p(trans.thisTopicIsNowClosed())
          else teamOnly.map { teamId =>
            p(
              trans.joinTheTeamXToPost(
                a(href := routes.Team.show(teamId))(trans.teamNamedX(teamIdToName(teamId)))
              )
            )
          } orElse {
            ctx.isAuth option p(trans.youCannotPostYetPlaySomeGames())
          },
          div(
            unsub.map { uns =>
              postForm(cls := s"unsub ${if (uns) "on" else "off"}", action := routes.Timeline.unsub(s"forum:${topic.id}"))(
                button(cls := "button button-empty text on", dataIcon := "v", bits.dataUnsub := "off")(trans.subscribe()),
                button(cls := "button button-empty text off", dataIcon := "v", bits.dataUnsub := "on")(trans.unsubscribe())
              )
            },

            isGranted(_.ModerateForum) option
              postForm(action := routes.ForumTopic.hide(categ.slug, topic.slug))(
                button(cls := "button button-empty button-green")(if (topic.hidden) "Feature" else "Un-feature")
              ),
            canModCateg option
              postForm(action := routes.ForumTopic.close(categ.slug, topic.slug))(
                button(cls := "button button-empty button-red")(if (topic.closed) "Reopen" else "Close")
              ),
            canModCateg option
              postForm(action := routes.ForumTopic.sticky(categ.slug, topic.slug))(
                button(cls := "button button-empty button-brag")(if (topic.isSticky) "Un-sticky" else "Sticky")
              )
          )
        ),

        formWithCaptcha.map {
          case (form, captcha) => postForm(
            cls := "form3 reply",
            action := s"${routes.ForumPost.create(categ.slug, topic.slug, posts.currentPage)}#reply",
            novalidate
          )(
              form3.group(form("text"), trans.message()) { f =>
                form3.textarea(f, klass = "post-text-area")(rows := 10, bits.dataTopic := topic.id)
              },
              views.html.base.captcha(form, captcha),
              form3.actions(
                a(href := routes.ForumCateg.show(categ.slug))(trans.cancel()),
                isGranted(_.PublicMod) option
                  form3.submit(frag("Reply as mod"), nameValue = (form("modIcon").name, "true").some, icon = "".some),
                form3.submit(trans.reply())
              )
            )
        },

        pager
      )
      if (categ.isStaff)
        main(cls := "page-menu")(
          views.html.mod.menu("forum"),
          div(cls := "forum forum-topic page-small box box-pad page-menu__content")(content)
        )
      else main(cls := "forum forum-topic page-small box box-pad")(content)
    }
}
