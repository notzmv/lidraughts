package controllers

import play.api.mvc._
import scala.concurrent.duration._

import lidraughts.app._
import lidraughts.common.HTTPRequest.isApiOrLocalApp
import lidraughts.common.ResponseHeaders.allowMethods

object Options extends LidraughtsController {

  val root = all("")

  def all(url: String) = Action { req =>
    if (isApiOrLocalApp(req)) NoContent.withHeaders(
      "Allow" -> allowMethods,
      "Access-Control-Max-Age" -> "1728000"
    )
    else NotFound
  }
}
