package views.html
package practice

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

object index {

  def apply(data: lidraughts.practice.UserPractice)(implicit ctx: Context) = views.html.base.layout(
    title = "Practice draughts positions",
    moreCss = cssTag("practice.index"),
    moreJs = embedJsUnsafe(s"""$$('.do-reset').on('click', function() {
if (confirm('${trans.learn.youWillLoseAllYourProgress.txt()}')) this.parentNode.submit();
});"""),
    openGraph = lidraughts.app.ui.OpenGraph(
      title = "Practice your draughts",
      description = "Learn how to master the most common draughts positions",
      url = s"$netBaseUrl${routes.Practice.index}"
    ).some
  ) {
      main(cls := "page-menu")(
        st.aside(cls := "page-menu__menu practice-side")(
          i(cls := "fat"),
          h1(trans.learn.practice()),
          h2(trans.learn.makesYourDraughtsPerfect()),
          div(cls := "progress")(
            div(cls := "text")(trans.learn.progressX(s"${data.progressPercent}%")),
            div(cls := "bar", style := s"width: ${data.progressPercent}%")
          ),
          postForm(action := routes.Practice.reset)(
            if (ctx.isAuth) (data.nbDoneChapters > 0) option a(cls := "do-reset")(trans.learn.resetMyProgress())
            else a(href := routes.Auth.signup)(trans.learn.signUpToSaveYourProgress())
          )
        ),
        div(cls := "page-menu__content practice-app")(
          data.structure.sections.map { section =>
            st.section(
              h2(section.name),
              div(cls := "studies")(
                section.studies.map { stud =>
                  val prog = data.progressOn(stud.id)
                  a(
                    cls := s"study ${if (prog.complete) "done" else "ongoing"}",
                    href := routes.Practice.show(section.id, stud.slug, stud.id.value)
                  )(
                      ctx.isAuth option span(cls := "ribbon-wrapper")(
                        span(cls := "ribbon")(prog.done, " / ", prog.total)
                      ),
                      i(cls := s"${stud.id}"),
                      span(cls := "text")(
                        h3(stud.name),
                        em(stud.desc)
                      )
                    )
                }
              )
            )
          }
        )
      )
    }
}
