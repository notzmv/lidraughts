package views.html.plan

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

object thanks {

  import trans.patron._

  def apply(patron: Option[lidraughts.plan.Patron], customer: Option[lidraughts.plan.StripeCustomer])(implicit ctx: Context) =
    views.html.base.layout(
      moreCss = cssTag("page"),
      title = thankYou.txt()
    ) {
        main(cls := "page-small page box box-pad")(

          h1(cls := "text", dataIcon := patronIconChar)(thankYou()),

          div(cls := "body")(
            p(tyvm()),
            p(transactionCompleted()),
            patron.map { pat =>
              if (pat.payPal.??(_.renew)) frag(
                p(
                  permanentPatron(), br,
                  ctx.me.map { me =>
                    a(href := routes.User.show(me.username))(checkOutProfile())
                  }
                ),
                p(paypalRenew())
              )
              else {
                if (customer.??(_.renew)) frag(
                  /* Unused, so not translated */
                  p(
                    "Note that your ", a(href := routes.Plan.index)("Patron page"),
                    " only shows invoices for your monthly subscription."
                  ),
                  p("But worry not, we received your donation! Thanks again!")
                )
                else frag(
                  if (pat.isLifetime) p(
                    nowLifetime(), br,
                    ctx.me.map { me =>
                      a(href := routes.User.show(me.username))(checkOutProfile())
                    }
                  )
                  else frag(
                    p(
                      nowOneMonth(), br,
                      ctx.me.map { me =>
                        a(href := routes.User.show(me.username))(checkOutProfile())
                      }
                    ),
                    p(downgradeNextMonth())
                  )
                )
              }
            },
            p("Success! ", a(href := routes.Lobby.home)("Return to Lidraughts homepage"), ".")
          )
        )
      }
}
