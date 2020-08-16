import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import { opposite } from 'draughtsground/util';
import { player as renderPlayer } from './util';
import { Board } from '../interfaces';

export function many(boards: Board[], boardSize: BoardData): VNode {
  return h('div.swiss__boards.now-playing', boards.map(board => renderBoard(board, boardSize)));
}

export function top(boards: Board[], boardSize: BoardData): VNode {
  return h('div.swiss__board__top.swiss__table',
    boards.slice(0, 1).map(board => renderBoard(board, boardSize))
  );
}

const renderBoard = (board: Board, boardSize: BoardData): VNode =>
  h(`div.swiss__board.mini-game.mini-game-${board.id}.mini-game--init.is2d.is${boardSize.key}`, {
    key: board.id,
    hook: {
      insert(vnode) {
        window.lichess.miniGame.init(vnode.elm as HTMLElement, `${board.fen}|${boardSize.size[0]}x${boardSize.size[1]}|${board.orientation}|${board.lastMove || ''}`)
        window.lichess.powertip.manualUserIn(vnode.elm as HTMLElement);
      }
    }
  }, [
    boardPlayer(board, opposite(board.orientation)),
    h('a.cg-wrap', {
      attrs: {
        href: `/${board.id}/${board.orientation}`
      }
    }),
    boardPlayer(board, board.orientation)
  ]);

function boardPlayer(board: Board, color: Color) {
  const player = board[color];
  return h('span.mini-game__player', [
    h('span.mini-game__user', [
      h('strong', '#' + player.rank),
      renderPlayer(player, true, true)
    ]),
    board.clock ? h(`span.mini-game__clock.mini-game__clock--${color}`, {
      attrs: {
        'data-time': board.clock[color]
      }
    }) : h('span.mini-game__result', board.winner ? (board.winner == color ? '1' : '0') : '½')
  ]);
}
