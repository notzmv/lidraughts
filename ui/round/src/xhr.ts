import RoundController from './ctrl';

export const headers = {
  'Accept': 'application/vnd.lidraughts.v3+json'
};

export function reload(ctrl: RoundController) {
  return $.ajax({
    url: ctrl.data.url.round,
    headers
  }).fail(window.lidraughts.reload);
}

export function whatsNext(ctrl: RoundController) {
  return $.ajax({
    url: '/whats-next/' + ctrl.data.game.id + ctrl.data.player.id,
    headers
  });
}

export function timeOutGame(ctrl: RoundController, simulId: string, seconds: number) {
  return $.ajax({
    method: 'POST',
    url: '/timeout/' + simulId + '/' + ctrl.data.game.id + '/' + seconds,
    headers
  });
}

export function challengeRematch(gameId: string) {
  return $.ajax({
    method: 'POST',
    url: '/challenge/rematch-of/' + gameId,
    headers
  });
}
