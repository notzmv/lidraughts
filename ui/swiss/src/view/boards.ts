import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import { player as renderPlayer } from './util';
import { Board, BoardPlayer } from '../interfaces';

export function many(boards: Board[], boardSize: BoardData): VNode {
  return h('div.swiss__boards.now-playing', boards.map(board => renderBoard(board, boardSize)));
}

export function top(boards: Board[], boardSize: BoardData): VNode {
  return h('div.swiss__board__top.swiss__table',
    boards.slice(0, 1).map(board => renderBoard(board, boardSize))
  );
}

const renderBoard = (board: Board, boardSize: BoardData): VNode =>
    h('div.swiss__board', [
      boardPlayer(board.black),
      miniBoard(board, boardSize),
      boardPlayer(board.white),
    ])

const boardPlayer = (player: BoardPlayer) =>
  h('div.swiss__board__player', [
    h('strong', '#' + player.rank),
    renderPlayer(player, true, true)
  ]);

function miniBoard(board: Board, boardSize: BoardData) {
  return h('a.mini-board.live.is2d.mini-board-' + board.id + '.is' + boardSize.key, {
    key: board.id,
    attrs: {
      href: '/' + board.id,
      'data-live': board.id,
      'data-color': 'white',
      'data-fen': board.fen,
      'data-lastmove': board.lastMove,
      'data-board': `${boardSize.size[0]}x${boardSize.size[1]}`,
    },
    hook: {
      insert(vnode) {
        window.lidraughts.parseFen($(vnode.elm as HTMLElement));
        window.lidraughts.pubsub.emit('content_loaded')
      }
    }
  }, [
    h('div.cg-wrap')
  ]);
}
