package lidraughts.mod

import lidraughts.chat.{ Chat, UserChat }
import lidraughts.report.Suspect
import lidraughts.simul.Simul
import lidraughts.tournament.Tournament

final class PublicChat(
    chatApi: lidraughts.chat.ChatApi,
    tournamentApi: lidraughts.tournament.TournamentApi,
    simulEnv: lidraughts.simul.Env
) {

  def all: Fu[(List[(Tournament, UserChat)], List[(Simul, UserChat)])] =
    tournamentChats zip simulChats

  def delete(suspect: Suspect): Funit = all.flatMap {
    case (tours, simuls) =>
      (tours.map(_._2) ::: simuls.map(_._2))
        .filter(_ hasLinesOf suspect.user)
        .map(chatApi.userChat.delete(_, suspect.user))
        .sequenceFu.void
  }

  private def tournamentChats: Fu[List[(Tournament, UserChat)]] =
    tournamentApi.fetchVisibleTournaments.flatMap {
      visibleTournaments =>
        val ids = visibleTournaments.all.map(_.id) map Chat.Id.apply
        chatApi.userChat.findAll(ids).map {
          chats =>
            chats.map { chat =>
              visibleTournaments.all.find(_.id === chat.id.value).map(tour => (tour, chat))
            }.flatten
        } map sortTournamentsByRelevance
    }

  private def simulChats: Fu[List[(Simul, UserChat)]] =
    fetchVisibleSimuls.flatMap {
      simuls =>
        val ids = simuls.map(_.id) map Chat.Id.apply
        chatApi.userChat.findAll(ids).map {
          chats =>
            chats.map { chat =>
              simuls.find(_.id === chat.id.value).map(simul => (simul, chat))
            }.flatten
        }
    }

  private def fetchVisibleSimuls: Fu[List[Simul]] = {
    simulEnv.allCreatedFeaturable.get zip
      simulEnv.repo.allStarted zip
      simulEnv.repo.allFinished(3) map {
        case ((created, started), finished) =>
          created ::: started ::: finished
      }
  }

  /**
   * Sort the tournaments by the tournaments most likely to require moderation attention
   */
  private def sortTournamentsByRelevance(tournaments: List[(Tournament, UserChat)]) =
    tournaments.sortBy(-_._1.nbPlayers)
}
