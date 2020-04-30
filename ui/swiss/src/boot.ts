import { SwissOpts, SwissData } from './interfaces';
import { ChatCtrl } from 'chat';

export default function(opts: SwissOpts): void {
  const li = window.lidraughts;
  const element = document.querySelector('main.swiss') as HTMLElement,
    data: SwissData = opts.data;
  li.socket = li.StrongSocket(
    '/swiss/' + cfg.data.id, cfg.data.socketVersion, {
      receive: function(t, d) {
        return swiss.socketReceive(t, d);
      }
    });
  cfg.socketSend = lidraughts.socket.send;
  cfg.element = element;
  cfg.$side = $('.swiss__side').clone();
  LidraughtsSwiss.start(cfg);
}
