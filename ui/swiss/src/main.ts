import { init } from 'snabbdom';
import { VNode } from 'snabbdom/vnode'
import klass from 'snabbdom/modules/class';
import attributes from 'snabbdom/modules/attributes';
import { Draughtsground } from 'draughtsground';
import { SwissOpts, Redraw } from './interfaces';
import SwissController from './ctrl';
import * as chat from 'chat';

const patch = init([klass, attributes]);

import makeCtrl from './ctrl';
import view from './view/main';

export function start(opts: SwissOpts) {

  const li = window.lidraughts;
  const element = document.querySelector('main.swiss') as HTMLElement;
  li.socket = li.StrongSocket(
    '/swiss/' + opts.data.id, opts.data.socketVersion, {
      receive: (t, d) => ctrl.socket.receive(t, d)
    });
  opts.classes = element.getAttribute('class');
  opts.socketSend = li.socket.send;
  opts.element = element;
  opts.$side = $('.swiss__side').clone();

  let vnode: VNode;

  function redraw() {
    vnode = patch(vnode, view(ctrl));
  }

  const ctrl = new SwissController(opts, redraw);

  const blueprint = view(ctrl);
  element.innerHTML = '';
  vnode = patch(element, blueprint);

  redraw();
};

// that's for the rest of lidraughts to access draughtsground
// without having to include it a second time
window.Draughtsground = Draughtsground;
window.LidraughtsChat = chat;
