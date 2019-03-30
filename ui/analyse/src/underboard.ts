import AnalyseCtrl from './ctrl';
import { defined } from 'common';
import * as tree from 'tree'

export default function(element: HTMLElement, ctrl: AnalyseCtrl) {

  const li = window.lidraughts;

  $(element).replaceWith($('.analyse__underboard.none').removeClass('none'));

  const data = ctrl.data,
    maxNodes = 200, // no analysis beyond ply 200
    $panels = $('.analyse__underboard__panels > div'),
    $menu = $('.analyse__underboard__menu'),
    $timeChart = $("#movetimes-chart"),
    inputFen = document.querySelector('.analyse__underboard__fen') as HTMLInputElement,
    unselect = chart => {
      chart.getSelectedPoints().forEach(function(point) {
        point.select(false);
      });
    };
  let lastFen: string;

  if (!li.AnalyseNVUI) {
    li.pubsub.on('analysis.comp.toggle', (v: boolean) => {
      setTimeout(function() {
        if (v) $menu.find('a.computer-analysis').mousedown();
        else $menu.find('a:eq(1)').mousedown();
      }, 50);
    });
    li.pubsub.on('analysis.change', (fen: Fen, _, mainlinePly: Ply | false) => {
      let chart, point, $chart = $("#adv-chart");
      if (fen && fen !== lastFen) {
        inputFen.value = fen;
        lastFen = fen;
      }
      if ($chart.length) {
        chart = window.Highcharts && $chart.highcharts();
        if (chart) {
          if (mainlinePly != chart.lastPly) {
            if (mainlinePly === false) unselect(chart);
            else {
              point = chart.series[0].data[mainlinePly - 1 - data.game.startedAtTurn];
              if (defined(point)) point.select();
              else unselect(chart);
            }
          }
          chart.lastPly = mainlinePly;
        }
      }
      if ($timeChart.length) {
        chart = window.Highcharts && $timeChart.highcharts();
        if (chart) {
          if (mainlinePly != chart.lastPly) {
            if (mainlinePly === false) unselect(chart);
            else {
              const white = mainlinePly % 2 !== 0;
              const serie = white ? 0 : 1;
              const turn = Math.floor((mainlinePly - 1 - data.game.startedAtTurn) / 2);
              point = chart.series[serie].data[turn];
              if (defined(point)) point.select();
              else unselect(chart);
            }
          }
          chart.lastPly = mainlinePly;
        }
      }
    });
    li.pubsub.on('socket.in.analysisProgress', d => {
      const partial = partialTree(d.tree);
      if (!li.advantageChart) startAdvantageChart();
      else if (li.advantageChart.update) li.advantageChart.update({ game: data.game, treeParts: tree.ops.mainlineNodeList(tree.build(d.tree).root) }, partial);
      if (!partial) {
        li.pubsub.emit('analysis.server.complete')();
        $("#adv-chart-loader").remove();
      }
    });
  }

  var partialTree = function(n, c?) {
    if (c === undefined) c = 0;
    if (c > maxNodes) return false;
    return n.children.length && (!n.eval || partialTree(n.children[0], c + 1));
  }
  var partialList = function(n) {
    var count = 0;
    for (let i = 0; i < n.length - 2; i++) {
      var skip = i > 0 && n[i].ply === n[i - 1].ply;
      if (!skip) {
        count++;
        if (count > maxNodes) return false;
        if (!n[i].eval || !Object.keys(n[i].eval).length)
          return true;
      }
    }
    return false;
  }
  function chartLoader() {
    return '<div id="adv-chart-loader">' +
      '<span>' + li.engineName + '<br>server analysis</span>' +
      li.spinnerHtml +
      '</div>'
  };
  function startAdvantageChart() {
    if (li.advantageChart || li.AnalyseNVUI) return;
    const loading = partialList(data.treeParts);
    const $panel = $panels.filter('.computer-analysis');
    if (!$("#adv-chart").length) $panel.html('<div id="adv-chart"></div>' + (loading ? chartLoader() : ''));
    else if (loading && !$("#adv-chart-loader").length) $panel.append(chartLoader());
    li.loadScript('javascripts/chart/acpl.js').then(function() {
      li.advantageChart(data, ctrl.trans, $("#adv-chart")[0] as HTMLElement);
    });
  };

  const storage = li.storage.make('analysis.panel');
  const setPanel = function(panel) {
    $menu.children('.active').removeClass('active').end().find(`[data-panel=${panel}`).addClass('active');
    $panels.removeClass('active').filter('.' + panel).addClass('active');
    if (panel == 'move-times' && !li.movetimeChart) try {
      li.loadScript('javascripts/chart/movetime.js').then(function() {
        li.movetimeChart(data, ctrl.trans);
      });
    } catch (e) {}
    if (panel == 'computer-analysis' && $("#adv-chart").length)
      setTimeout(startAdvantageChart, 200);
  };
  $menu.on('mousedown', 'span', function(this: HTMLElement) {
    const panel = $(this).data('panel');
    storage.set(panel);
    setPanel(panel);
  });
  const stored = storage.get();
  const $menuCt = $menu.children('[data-panel=ctable]');
  if (stored && $menuCt.length) setPanel(stored);
  else ($menuCt.length ? $menuCt : $menu.children(':first-child')).trigger('mousedown');
  if (!data.analysis) {
    $panels.find('form.future-game-analysis').submit(function(this: HTMLElement) {
      if ($(this).hasClass('must-login')) {
        if (confirm(ctrl.trans('youNeedAnAccountToDoThat'))) location.href = '/signup';
        return false;
      }
      $.ajax({
        method: 'post',
        url: $(this).attr('action'),
        success: startAdvantageChart,
        error: li.reload
      });
      return false;
    });
  }

  $panels.on('click', '.pdn', function(this: HTMLElement) {
    const selection = window.getSelection(),
      range = document.createRange();
    range.selectNodeContents(this);
    if (selection) {
      selection.removeAllRanges();
      selection.addRange(range);
    }
  });
  $panels.on('click', '.embed-howto', function(this: HTMLElement) {
    const url = 'https://lidraughts.org/embed/' + data.game.id + location.hash;
    const iframe = '<iframe src="' + url + '?theme=auto&bg=auto"\nwidth=600 height=397 frameborder=0></iframe>';
    $.modal($(
      '<strong style="font-size:1.5em">' + $(this).html() + '</strong><br /><br />' +
      '<pre>' + li.escapeHtml(iframe) + '</pre><br />' +
      iframe + '<br /><br />' +
      '<a class="text" data-icon="" href="/developers#embed-game">Read more about embedding games</a>'
    ));
  });
  $('button.cheat_list').on('click', function(this: HTMLElement) {
    $.post({
      url: $(this).data('src') + '?v=' + !$(this).hasClass('active')
    });
    $(this).toggleClass('active');
  });
}