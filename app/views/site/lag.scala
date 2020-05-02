package views.html.site

import controllers.routes
import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

object lag {

  import trans.lag._

  def apply()(implicit ctx: Context) = help.layout(
    title = isLidraughtsLagging.txt(),
    active = "lag",
    moreCss = cssTag("lag"),
    moreJs = frag(
      highchartsLatestTag,
      highchartsMoreTag,
      jsTag("lag.js")
    )
  ) {
      div(cls := "box box-pad lag")(
        h1(
          isLidraughtsLagging(),
          span(cls := "answer short")(
            span(cls := "waiting")(measurementInProgressThreeDot()),
            span(cls := "nope-nope none")(strong(trans.no(), "."), " ", andYourNetworkIsGood()),
            span(cls := "nope-yep none")(strong(trans.no(), "."), " ", andYourNetworkIsBad()),
            span(cls := "yep none")(strong(trans.yes(), "."), " ", itWillBeFixedSoon())
          )
        ),
        div(cls := "answer long")(
          andNowTheLongAnswerLagComposedOfTwoValues()
        ),
        div(cls := "sections")(
          st.section(cls := "server")(
            h2(lidraughtsServerLatency()),
            div(cls := "meter"),
            p(
              lidraughtsServerLatencyExplanation(
                strong(sameForEverybody())
              )
            )
          ),
          st.section(cls := "network")(
            h2(networkBetweenLidraughtsAndYou),
            div(cls := "meter"),
            p(
              networkBetweenLidraughtsAndYouExplanation(
                strong(distanceToLidraughtsFrance()),
                strong(qualityOfYourInternetConnection())
              )
            )
          )
        ),
        div(cls := "last-word")(
          p(youCanFindTheseValuesAtAnyTimeByClickingOnYourUsername()),
          h2(lagCompensation()),
          p(
            lagCompensationExplanation(
              strong(notAHandicap())
            )
          )
        )
      )
    }
}
