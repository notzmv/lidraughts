/** based on https://github.com/hustcc/timeago.js Copyright (c) 2016 hustcc License: MIT **/
lidraughts.timeago = (function() {

  // second, minute, hour, day, week, month, year(365 days)
  var SEC_ARRAY = [60, 60, 24, 7, 365/7/12, 12],
    SEC_ARRAY_LEN = 6;

  var intR = /^\d+$/;

  // format Date / string / timestamp to Date instance.
  function toDate(input) {
    if (input instanceof Date) return input;
    if (!isNaN(input)) return new Date(parseInt(input));
    if (intR.test(input)) return new Date(parseInt(input));
    input = (input || '').trim().replace(/\.\d+/, '') // remove milliseconds
      .replace(/-/, '/').replace(/-/, '/')
      .replace(/(\d)T(\d)/, '$1 $2').replace(/Z/, ' UTC') // 2017-2-5T3:57:52Z -> 2017-2-5 3:57:52UTC
      .replace(/([\+\-]\d\d)\:?(\d\d)/, ' $1$2'); // -04:00 -> -0400
    return new Date(input);
  }

  // format the diff second to *** time ago
  function formatDiff(diff) {
    var i = 0,
      agoin = diff < 0 ? 1 : 0, // timein or timeago
        total_sec = diff = Math.abs(diff);

      for (; diff >= SEC_ARRAY[i] && i < SEC_ARRAY_LEN; i++) {
        diff /= SEC_ARRAY[i];
      }
      diff = parseInt(diff);
    i *= 2;

    if (diff > (i === 0 ? 9 : 1)) i += 1;
    return lidraughts.timeagoLocale(diff, i, total_sec)[agoin].replace('%s', diff);
  }

  // calculate the diff second between date to be formatted now
  function diffSec(date) {
    return (Date.now() - toDate(date)) / 1000;
  }

  var formatterInst;

  function formatter() {
    return formatterInst = formatterInst || (window.Intl && Intl.DateTimeFormat ?
      new Intl.DateTimeFormat(document.documentElement.lang, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: 'numeric',
        minute: 'numeric'
      }).format : function(d) { return d.toLocaleString(); })
  }

  /**
   * timeago: the function to get `timeago` instance.
   *
   * How to use it?
   * var timeagoFactory = require('timeago.js');
   * var timeago = timeagoFactory(); // all use default.
   * var timeago = timeagoFactory('2016-09-10'); // the relative date is 2016-09-10, so the 2016-09-11 will be 1 day ago.
   **/
   return {
     render: function(nodes) {
       var node, cl, abs, set, date, str;
       for (var i = 0; i < nodes.length; i++) {
         node = nodes[i],
         cl = node.classList,
         abs = cl.contains('abs'),
         set = cl.contains('set');
         if (set && abs) continue;
         node.date = node.date || toDate(node.getAttribute('datetime'));
         if (!set) {
           str = formatter()(node.date);
           if (abs) node.textContent = str;
           else node.setAttribute('title', str);
           node.classList.add('set');
         }
         if (!abs) node.textContent = formatDiff(diffSec(node.date));
       }
     },
     // relative
     format: function(date) {
       return formatDiff(diffSec(date));
     },
     absolute: function(d) {
       return formatter()(toDate(d));
     }
   };
})();
