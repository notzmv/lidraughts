import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode'
import studyView = require('../study/studyView');
import { nodeFullName, bind } from '../util';
import AnalyseCtrl from '../ctrl';
import { patch } from '../main';

export interface Opts {
  path: Tree.Path;
  root: AnalyseCtrl;
}

interface Coords {
  x: number;
  y: number;
}

const elementId = 'analyse-cm';

function getPosition(e: MouseEvent): Coords {
  let posx = 0;
  let posy = 0;
  if (e.pageX || e.pageY) {
    posx = e.pageX;
    posy = e.pageY;
  } else if (e.clientX || e.clientY) {
    posx = e.clientX + document.body.scrollLeft + document.documentElement!.scrollLeft;
    posy = e.clientY + document.body.scrollTop + document.documentElement!.scrollTop;
  }
  return {
    x: posx,
    y: posy
  }
}

function positionMenu(menu: HTMLElement, coords: Coords): void {

  const menuWidth = menu.offsetWidth + 4,
    menuHeight = menu.offsetHeight + 4,
    windowWidth = window.innerWidth,
    windowHeight = window.innerHeight;

  menu.style.left = (windowWidth - coords.x) < menuWidth ?
    windowWidth - menuWidth + "px" :
    menu.style.left = coords.x + "px";

  menu.style.top = (windowHeight - coords.y) < menuHeight ?
    windowHeight - menuHeight + "px" :
    menu.style.top = coords.y + "px";
}

function action(icon: string, text: string, handler: () => void): VNode {
  return h('a', {
    attrs: { 'data-icon': icon },
    hook: bind('click', handler)
  }, text);
}

function view(opts: Opts, coords: Coords): VNode {
  const ctrl = opts.root,
    node = ctrl.tree.nodeAtPath(opts.path),
    onMainline = ctrl.tree.pathIsMainline(opts.path);
  return h('div#' + elementId + '.visible', {
    hook: {
      insert: vnode => positionMenu(vnode.elm as HTMLElement, coords),
      postpatch: (_, vnode) => positionMenu(vnode.elm as HTMLElement, coords)
    }
  }, [
    h('p.title', nodeFullName(node)),
    onMainline ? null : action('S', ctrl.trans.noarg('promoteVariation'), () => ctrl.promote(opts.path, false)),
    onMainline ? null : action('E', ctrl.trans.noarg('makeMainLine'), () => ctrl.promote(opts.path, true)),
    (ctrl.data.puzzleEditor && node.missingAlts && node.missingAlts.length > 0) ? action('.', 'Expand alternatives', () => ctrl.expandVariations(opts.path)) : null,
    action('q', ctrl.trans.noarg('deleteFromHere'), () => ctrl.deleteNode(opts.path))
  ].concat(
    ctrl.study ? studyView.contextMenu(ctrl.study, opts.path, node) : []
  ));
}

export default function (e: MouseEvent, opts: Opts): void {
  const el = $('#' + elementId)[0] || $('<div id="' + elementId + '">').appendTo($('body'))[0];
  opts.root.contextMenuPath = opts.path;
  function close(e: MouseEvent) {
    if (e.button === 2) return; // right click
    opts.root.contextMenuPath = undefined;
    document.removeEventListener('click', close, false);
    $('#' + elementId).removeClass('visible');
    opts.root.redraw();
  };
  document.addEventListener('click', close, false);
  el.innerHTML = '';
  patch(el, view(opts, getPosition(e)));
}
