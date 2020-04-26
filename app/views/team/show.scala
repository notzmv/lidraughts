package views.html.team

import play.api.libs.json.Json

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.paginator.Paginator
import lidraughts.common.String.html.{ richText, safeJsonValue }
import lidraughts.team.Team

import controllers.routes

object show {

  def apply(
    t: Team,
    members: Paginator[lidraughts.common.LightUser],
    info: lidraughts.app.mashup.TeamInfo,
    chatOption: Option[lidraughts.chat.UserChat.Mine],
    socketVersion: Option[lidraughts.socket.Socket.SocketVersion]
  )(implicit ctx: Context) =
    bits.layout(
      title = t.name,
      openGraph = lidraughts.app.ui.OpenGraph(
        title = s"${t.name} team",
        url = s"$netBaseUrl${routes.Team.show(t.id).url}",
        description = shorten(t.description, 152)
      ).some,
      moreJs =
        for {
          v <- socketVersion
          chat <- chatOption
        } yield frag(
          jsAt(s"compiled/lidraughts.chat${isProd ?? (".min")}.js"),
          embedJsUnsafe(s"""lidraughts.team=${
            safeJsonValue(
              Json.obj(
                "id" -> t.id,
                "socketVersion" -> v.value,
                "chat" -> views.html.chat.json(
                  chat.chat,
                  name = trans.chatRoom.txt(),
                  timeout = chat.timeout,
                  public = true,
                  resourceId = lidraughts.chat.Chat.ResourceId(s"team/${chat.chat.id}")
                )
              )
            )
          }""")
        )
    )(
        main(cls := "team-show box", socketVersion.map { v =>
          data("socket-version") := v.value
        })(
          div(cls := "box__top")(
            h1(cls := "text", dataIcon := "f")(t.name, " ", em("TEAM")),
            div(
              if (t.disabled) span(cls := "staff")("CLOSED")
              else trans.nbMembers.plural(t.nbMembers, strong(t.nbMembers.localize))
            )
          ),
          (info.mine || t.enabled) option div(cls := "team-show__content")(
            div(cls := "team-show__content__col1")(
              st.section(cls := "team-show__meta")(
                p(trans.teamLeader(), ": ", userIdLink(t.createdBy.some))
              ),
              chatOption.isDefined option views.html.chat.frag,
              div(cls := "team-show__actions")(
                (t.enabled && !info.mine) option frag(
                  if (info.requestedByMe) strong("Your join request is being reviewed by the team leader")
                  else ctx.me.??(_.canTeam) option
                    postForm(cls := "inline", action := routes.Team.join(t.id))(
                      submitButton(cls := "button button-green")(trans.joinTeam.txt())
                    )
                ),
                (info.mine && !info.createdByMe) option
                  postForm(cls := "quit", action := routes.Team.quit(t.id))(
                    submitButton(cls := "button button-empty button-red confirm")(trans.quitTeam.txt())
                  ),
                info.createdByMe option frag(
                  a(href := routes.Tournament.teamBattleForm(t.id), cls := "button button-empty text", dataIcon := "g")(
                    span(
                      strong("Team battle"),
                      em("A battle of multiple teams, each players scores points for their team")
                    )
                  ),
                  a(href := s"${routes.Tournament.form()}?team=${t.id}", cls := "button button-empty text", dataIcon := "g")(
                    span(
                      strong("Team tournament"),
                      em("An arena tournament that only members of your team can join")
                    )
                  )
                ),
                (info.createdByMe || isGranted(_.Admin)) option
                  a(href := routes.Team.edit(t.id), cls := "button button-empty text", dataIcon := "%")(trans.settings())
              ),
              div(cls := "team-show__members")(
                st.section(cls := "recent-members")(
                  h2(trans.teamRecentMembers()),
                  div(cls := "userlist infinitescroll")(
                    pagerNext(members, np => routes.Team.show(t.id, np).url),
                    members.currentPageResults.map { member =>
                      div(cls := "paginated")(lightUserLink(member))
                    }
                  )
                )
              )
            ),
            div(cls := "team-show__content__col2")(
              st.section(cls := "team-show__desc")(
                richText(t.description),
                t.location.map { loc =>
                  frag(br, trans.location(), ": ", richText(loc))
                },
                info.hasRequests option div(cls := "requests")(
                  h2(info.requests.size, " join requests"),
                  views.html.team.request.list(info.requests, t.some)
                )
              ),
              div(cls := "team-show__tour-forum")(
                info.tournaments.nonEmpty option frag(
                  st.section(cls := "team-show__tour")(
                    h2(trans.tournaments()),
                    info.tournaments.span(_.isCreated) match {
                      case (created, started) =>
                        views.html.tournament.bits.forTeam(created.sortBy(_.startsAt) ::: started)
                    }
                  )
                ),
                NotForKids {
                  st.section(cls := "team-show__forum")(
                    h2(a(href := teamForumUrl(t.id))(trans.forum())),
                    info.forumPosts.take(10).map { post =>
                      a(cls := "team-show__forum__post", href := routes.ForumPost.redirect(post.postId))(
                        div(cls := "meta")(
                          strong(post.topicName),
                          em(
                            post.userId map usernameOrId,
                            " • ",
                            momentFromNow(post.createdAt)
                          )
                        ),
                        p(shorten(post.text, 200))
                      )
                    },
                    a(cls := "more", href := teamForumUrl(t.id))(t.name, " ", trans.forum(), " »")
                  )
                }
              )
            )
          )
        )
      )
}
