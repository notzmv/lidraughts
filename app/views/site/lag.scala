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
            span(cls := "nope-nope none")(noAndYourNetworkIsGood()),
            span(cls := "nope-yep none")(noAndYourNetworkIsBad()),
            span(cls := "yep none")(yesItWillBeFixedSoon())
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
              lidraughtsServerLatencyExplanation()
            )
          ),
          st.section(cls := "network")(
            h2(networkBetweenLidraughtsAndYou()),
            div(cls := "meter"),
            p(
              networkBetweenLidraughtsAndYouExplanation()
            )
          )
        ),
        div(cls := "last-word")(
          p(youCanFindTheseValuesAtAnyTimeByClickingOnYourUsername()),
          h2(lagCompensation()),
          p(
            lagCompensationExplanation()
          )
        )
      )
    }
}
