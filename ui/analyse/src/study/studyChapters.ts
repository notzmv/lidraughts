import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode'
import { prop, Prop } from 'common';
import { bind, dataIcon, iconTag, scrollTo } from '../util';
import { ctrl as chapterNewForm, StudyChapterNewFormCtrl } from './chapterNewForm';
import { ctrl as chapterEditForm } from './chapterEditForm';
import AnalyseCtrl from '../ctrl';
import { StudyCtrl, StudyChapterMeta, LocalPaths, StudyChapter, TagArray } from './interfaces';

export interface StudyChaptersCtrl {
  newForm: StudyChapterNewFormCtrl;
  editForm: any;
  list: Prop<StudyChapterMeta[]>;
  get(id: string): StudyChapterMeta | undefined;
  size(): number;
  sort(ids: string[]): void;
  firstChapterId(): string;
  toggleNewForm(): void;
  localPaths: LocalPaths;
}

export function ctrl(initChapters: StudyChapterMeta[], send: SocketSend, setTab: () => void, chapterConfig, root: AnalyseCtrl): StudyChaptersCtrl {

  const list: Prop<StudyChapterMeta[]> = prop(initChapters);

  const newForm = chapterNewForm(send, list, setTab, root);
  const editForm = chapterEditForm(send, chapterConfig, root.trans, root.redraw);

  const localPaths: LocalPaths = {};

  return {
    newForm,
    editForm,
    list,
    get(id) {
      return list().find(c => c.id === id);
    },
    size() {
      return list().length;
    },
    sort(ids) {
      send("sortChapters", ids);
    },
    firstChapterId() {
      return list()[0].id;
    },
    toggleNewForm() {
      if (newForm.vm.open || list().length < 64) newForm.toggle();
      else alert("You have reached the limit of 64 chapters per study. Please create a new study.");
    },
    localPaths
  };
}

export function isFinished(c: StudyChapter) {
  const result = findTag(c.tags, 'result');
  return result && result !== '*';
}

export function findTag(tags: TagArray[], name: string): string | undefined {
  const t = tags.find(t => t[0].toLowerCase() === name);
  return t && t[1];
}

export function resultOf(tags: TagArray[], isWhite: boolean, draughtsResult: boolean): string | undefined {
  switch(findTag(tags, 'result')) {
    case '1-0':
    case '2-0':
      return isWhite ? (draughtsResult ? '2' : '1') : '0';
    case '0-1':
    case '0-2':
      return isWhite ? '0' : (draughtsResult ? '2' : '1');
    case '1-1':
    case '1/2-1/2':
    case '½-½':
      return (draughtsResult ? '1' : '½');
    default:
      return;
  }
}

export function view(ctrl: StudyCtrl): VNode {

  const canContribute = ctrl.members.canContribute(),
    current = ctrl.currentChapter();

  function update(vnode: VNode) {
    const newCount = ctrl.chapters.list().length,
      vData = vnode.data!.li!,
      el = vnode.elm as HTMLElement;
    if (vData.count !== newCount) {
      if (current.id !== ctrl.chapters.firstChapterId()) {
        scrollTo(el, el.querySelector('.active'));
      }
    } else if (ctrl.vm.loading && vData.loadingId !== ctrl.vm.nextChapterId) {
      vData.loadingId = ctrl.vm.nextChapterId;
      scrollTo(el, el.querySelector('.loading'));
    }
    vData.count = newCount;
    if (!window.lidraughts.hasTouchEvents && canContribute && newCount > 1 && !vData.sortable) {
      const makeSortable = function() {
        vData.sortable = window['Sortable'].create(el, {
          draggable: '.draggable',
          onSort() {
            ctrl.chapters.sort(vData.sortable.toArray());
          }
        });
      }
      if (window['Sortable']) makeSortable();
      else window.lidraughts.loadScript('javascripts/vendor/Sortable.min.js').done(makeSortable);
    }
  }

  const introActive = ctrl.relay && ctrl.relay.intro.active;

  return h('div.study__chapters' + (ctrl.relay ? '.relay' : ''), {
    hook: {
      insert(vnode) {
        (vnode.elm as HTMLElement).addEventListener('click', e => {
          const target = e.target as HTMLElement;
          const id = (target.parentNode as HTMLElement).getAttribute('data-id') || target.getAttribute('data-id');
          if (!id) return;
          if (target.tagName === 'ACT') ctrl.chapters.editForm.toggle(ctrl.chapters.get(id));
          else ctrl.setChapter(id);
        });
        vnode.data!.li = {};
        update(vnode);
        window.lidraughts.pubsub.emit('chat.resize');
      },
      postpatch(old, vnode) {
        vnode.data!.li = old.data!.li;
        update(vnode);
      },
      destroy: vnode => {
        const sortable = vnode.data!.li!.sortable;
        if (sortable) sortable.destroy()
      }
    }
  }, ctrl.chapters.list().map((chapter, i) => {
    const editing = ctrl.chapters.editForm.isEditing(chapter.id),
      loading = ctrl.vm.loading && chapter.id === ctrl.vm.nextChapterId,
      active = !ctrl.vm.loading && current && !introActive && current.id === chapter.id;
    return h('div', {
      key: chapter.id,
      attrs: { 'data-id': chapter.id },
      class: { active, editing, loading, draggable: canContribute }
    }, [
      h('span', loading ? h('span.ddloader') : ['' + (i + 1)]),
      h('h3', chapter.name),
      canContribute ? h('act', { attrs: dataIcon('%') }) : null
    ]);
  }).concat(
    canContribute ? [
      h('div.add', {
        hook: bind('click', ctrl.chapters.toggleNewForm, ctrl.redraw)
      }, [
        h('span', iconTag('O')),
        h('h3', ctrl.trans.noarg('addNewChapter'))
      ])
    ] : []
  ));
}
