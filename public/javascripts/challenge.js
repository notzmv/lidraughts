window.onload = function() {
  var opts = lidraughts_challenge;
  var selector = '.challenge-page';
  var element = document.querySelector(selector);
  var challenge = opts.data.challenge;
  var accepting;

  lidraughts.socket = new lidraughts.StrongSocket(
    opts.socketUrl,
    opts.data.socketVersion, {
      options: {
        name: "challenge"
      },
      events: {
        reload: xhrReload
      }
    });

  function xhrReload() {
    $.ajax({
      url: opts.xhrUrl,
      success: function(html) {
        $(selector).replaceWith($(html).find(selector));
        init();
      }
    });
  }

  function init() {
    if (!accepting) $('#challenge-redirect').each(function() {
      location.href = $(this).attr('href');
    });
    $(selector).find('form.accept').submit(function() {
      accepting = true;
      $(this).html('<span class="ddloader"></span>');
    });
    $(selector).find('form.xhr').submit(function(e) {
      e.preventDefault();
      $.ajax(lidraughts.formAjax($(this)));
      $(this).html('<span class="ddloader"></span>');
    });
    $(selector).find('input.friend-autocomplete').each(function() {
      var $input = $(this);
      lidraughts.userAutocomplete($input, {
        focus: 1,
        friend: 1,
        tag: 'span',
        onSelect: function() {
          $input.parents('form').submit();
        }
      });
    });
    if (challenge.external && challenge.startsAt) {
      $('.challenge-external .countdown').each(function() {

        var trans = window.lidraughts.trans(opts.i18n);
        var $el = $(this);
        var target = new Date(challenge.startsAt);

        var second = 1000,
          minute = second * 60,
          hour = minute * 60,
          day = hour * 24;

        var redraw = function() {

          var distance = target - new Date().getTime();

          if (distance > 0) {
            var days = Math.floor(distance / day),
              hours = Math.floor((distance % day) / hour),
              minutes = Math.floor((distance % hour) / minute),
              seconds = Math.floor((distance % minute) / second);
            if (days) $el.find('.days').html(trans.plural('nbDays', days).replace(days, '<span>' + days + '</span>'));
            else $el.find('.days').hide();
            if (days || hours) $el.find('.hours').html(trans.plural('nbHours', hours).replace(hours, '<span>' + hours + '</span>'));
            else $el.find('.hours').hide();
            $el.find('.minutes').html(trans.plural('nbMinutes', minutes).replace(minutes, '<span>' + minutes + '</span>'));
            $el.find('.seconds').html(trans('nbSeconds', seconds).replace(seconds, '<span>' + seconds + '</span>'));
          } else {
            clearInterval(interval);
            xhrReload();
          }

        };
        var interval = setInterval(redraw, second);

        redraw();
      });
    }
  }

  init();

  function pingNow() {
    if (document.getElementById('ping-challenge')) {
      lidraughts.socket.send('ping');
      setTimeout(pingNow, 2000);
    }
  }

  pingNow();
}
