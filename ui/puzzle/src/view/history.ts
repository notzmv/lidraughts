import { h, thunk } from 'snabbdom';
import { VNode } from 'snabbdom/vnode';

import { Controller } from '../interfaces';
import { puzzleUrl } from '../util';

const historySize = 15;

function render(ctrl: Controller): VNode {
  const data = ctrl.getData();
  const slots: any[] = [];
  for (let i = 0; i < historySize; i++) slots[i] = data.user.recent[i] || null;
  return h('div.puzzle__history', slots.map(function(s) {
    if (s) return h('a', {
      class: {
        current: data.puzzle.id === s[0],
        win: s[1] >= 0,
        loss: s[1] < 0
      },
      attrs: { href: puzzleUrl(data.puzzle.variant.key) + s[0] }
    }, s[1] > 0 ? '+' + s[1] : '−' + (-s[1]));
  }));
}

export default function(ctrl) {
  if (!ctrl.getData().user) return;
  return thunk('div.puzzle__history', render, [ctrl, ctrl.recentHash()]);
};
