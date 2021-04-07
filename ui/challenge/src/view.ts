import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode'
import { Ctrl, Challenge, ChallengeData, ChallengeDirection, ChallengeUser, TimeControl } from './interfaces'

export function loaded(ctrl: Ctrl): VNode {
  return ctrl.redirecting() ?
  h('div#challenge-app.dropdown', h('div.initiating', spinner())) :
  h('div#challenge-app.links.dropdown.rendered', renderContent(ctrl));
}

export function loading(): VNode {
  return h('div#challenge-app.links.dropdown.rendered', [
    h('div.empty.loading', '-'),
    create()
  ]);
}

function renderContent(ctrl: Ctrl): VNode[] {
  let d = ctrl.data();
  const nb = d.in.length + d.out.length;
  return nb ? [allChallenges(ctrl, d, nb)] : [
    empty(ctrl),
    create(ctrl)
  ];
}

function userPowertips(vnode: VNode) {
  window.lidraughts.powertip.manualUserIn(vnode.elm);
}

function allChallenges(ctrl: Ctrl, d: ChallengeData, nb: number): VNode {
  return h('div.challenges', {
    class: { many: nb > 3 },
    hook: {
      insert: userPowertips,
      postpatch: userPowertips
    }
  }, d.in.map(challenge(ctrl, 'in')).concat(d.out.map(challenge(ctrl, 'out'))));
}

function challenge(ctrl: Ctrl, dir: ChallengeDirection) {
  return (c: Challenge) => {
    const descItems = [
      ctrl.trans()(c.rated ? 'rated' : 'casual'),
      timeControl(ctrl, c.timeControl),
      c.variant.name
    ];
    if (c.microMatch) descItems.push(ctrl.trans()('microMatch'));
    const descStr = descItems.join(' • ');
    return h('div.challenge.' + dir + '.c-' + c.id, {
      class: {
        declined: !!c.declined
      }
    }, [
      h('div.content', [
        h('span.head', renderUser(ctrl, dir === 'in' ? c.challenger : c.destUser)),
        h('span.desc', {
          attrs: { title: descStr }
        }, descStr)
      ]),
      h('i', {
        attrs: {'data-icon': c.perf.icon}
      }),
      h('div.buttons', (dir === 'in' ? inButtons : outButtons)(ctrl, c))
    ]);
  };
}

function inButtons(ctrl: Ctrl, c: Challenge): VNode[] {
  const trans = ctrl.trans();
  return [
    h('form', {
      attrs: {
        method: 'post',
        action: `/challenge/${c.id}/accept`
      }
    }, [
      h('button.button.accept', {
        attrs: {
          'type': 'submit',
          'data-icon': 'E',
          title: trans('accept')
        },
        hook: onClick(ctrl.onRedirect)
      })]),
    h('button.button.decline', {
      attrs: {
        'type': 'submit',
        'data-icon': 'L',
        title: trans('decline')
      },
      hook: onClick(() => ctrl.decline(c.id))
    })
  ];
}

function outButtons(ctrl: Ctrl, c: Challenge) {
  const trans = ctrl.trans();
  return [
    h('div.owner', [
      h('span.waiting', ctrl.trans()('waiting')),
      h('a.view', {
        attrs: {
          'data-icon': 'v',
          href: '/' + c.id,
          title: trans('viewInFullSize')
        }
      })
    ]),
    h('button.button.decline', {
      attrs: {
        'data-icon': 'L',
        title: trans('cancel')
      },
      hook: onClick(() => ctrl.cancel(c.id))
    })
  ];
}

function timeControl(ctrl: Ctrl, c: TimeControl): string {
  switch (c.type) {
    case 'unlimited':
      return ctrl.trans()('unlimited');
    case 'correspondence':
      if (!c.daysPerTurn || c.daysPerTurn === 1) return ctrl.trans()('oneDay');
      return ctrl.trans()('nbDays', c.daysPerTurn);
    case 'clock':
      return c.show || '-';
  }
}

function renderUser(ctrl: Ctrl, u?: ChallengeUser): VNode {
  if (!u) return h('span', ctrl.trans()('openChallenge'));
  const rating = u.rating + (u.provisional ? '?' : ''),
    title64 = u.title && u.title.endsWith('-64');
  return h('a.ulpt.user-link', {
    attrs: { href: `/@/${u.name}`},
    class: { online: !!u.online }
  }, [
    h('i.line' + (u.patron ? '.patron' : '')),
    h('name', [
      u.title && h(
        'span.title', 
        title64 ? { attrs: {'data-title64': true } } : (u.title == 'BOT' ? { attrs: { 'data-bot': true } } : {}), 
        (title64 ? u.title.slice(0, u.title.length - 3) : u.title) + ' '
      ), 
      u.name + ' (' + rating + ') '
    ]),
      h('signal', u.lag === undefined ? [] : [1, 2, 3, 4].map((i) => h('i', {
        class: { off: u.lag! < i}
      })))
  ]);
}

function create(ctrl?: Ctrl): VNode {
  return h('a.create', {
    attrs: {
      href: '/?any#friend',
      'data-icon': 'O',
      title: ctrl ? ctrl.trans()('challengeSomeone') : 'Challenge someone'
    }
  });
}

function empty(ctrl: Ctrl): VNode {
  return h('div.empty.text', {
    attrs: {
      'data-icon': '',
    }
  }, ctrl.trans()('noChallenges'));
}

function onClick(f: (e: Event) => void) {
  return {
    insert: (vnode: VNode) => {
      (vnode.elm as HTMLElement).addEventListener('click', f);
    }
  };
}

function spinner() {
  return h('div.spinner', [
    h('svg', { attrs: { viewBox: '0 0 40 40' } }, [
      h('circle', {
        attrs: { cx: 20, cy: 20, r: 18, fill: 'none' }
      })])]);
}
