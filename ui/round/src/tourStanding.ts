import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode'
import { onInsert } from './util'
import { ChatPlugin } from 'chat'

export interface TourStandingCtrl extends ChatPlugin {
  set(data: TourPlayer[]): void;
}

export interface TourPlayer {
  n: string; // name
  s: number; // score
  t?: string; // title
  f: boolean; // fire
  w: boolean; // withdraw
}

export function tourStandingCtrl(data: TourPlayer[], name: string): TourStandingCtrl {
  return {
    set(d: TourPlayer[]) { data = d },
    tab: {
      key: 'tourStanding',
      name: name
    },
    view(): VNode {
      return h('table.slist', {
        hook: onInsert(_ => {
          window.lidraughts.loadCssPath('round.tour-standing');
        })
      }, [
        h('tbody', data.map((p: TourPlayer, i: number) => {
          const title64 = p.t && p.t.endsWith('-64');
          return h('tr.' + p.n, [
            h('td.name', [
              h('span.rank', '' + (i + 1)),
              h('a.user-link.ulpt', 
                { attrs: { href: `/@/${p.n}` } },
                [
                  p.t ? h(
                    'em.title',
                    title64 ? { attrs: {'data-title64': true } } : (p.t == 'BOT' ? { attrs: {'data-bot': true } } : {}),
                    title64 ? p.t.slice(0, p.t.length - 3) : p.t
                  ) : null,
                  p.t ? ' ' + p.n : p.n
                ])
            ]),
            h('td.total', p.f ? {
              class: { 'is-gold': true },
              attrs: { 'data-icon': 'Q' }
            } : {}, '' + p.s)
          ])
        }))
      ]);
    }
  };
}
