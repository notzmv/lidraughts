
import { san2alg } from 'draughts'

function renderSan(san: San, algebraic?: boolean) {
  if (!san) return '';
  const lowerSan = san.toLowerCase(),
    isCapture = lowerSan.includes('x');
  let fields = algebraic ? (san2alg(lowerSan) || lowerSan).split(isCapture ? ':' : '-') : lowerSan.split(isCapture ? 'x' : '-');
  if (fields.length <= 1) return san;
  if (algebraic) {
    fields = fields.map(s => 
      s.split('').map(c => {
        const code = c.charCodeAt(0);
        if (code > 48 && code < 58) return c; // 1-8
        if (code > 96 && code < 105) return c.toUpperCase();
        return c;
      }).join(' ')
    );
  }
  if (isCapture) return [fields[0], 'takes', ...fields.slice(1)].join(' ');
  else return fields.join(' ');
}

export function say(text: string, cut: boolean) {
  const msg = new SpeechSynthesisUtterance(text);
  if (cut) speechSynthesis.cancel();
  window.lidraughts.sound.say(msg);
}

function trimField(f: string) {
  return f.startsWith('0') ? f.slice(1) : f;
}

export function step(s: { san?: San, uci?: Uci }, cut: boolean, captureFrom?: Key, algebraic?: boolean) {
  if (captureFrom && s.uci && s.uci.length >= 4) {
    const san = trimField(captureFrom) + 'x' + trimField(s.uci.slice(-2));
    say(renderSan(san, algebraic), cut);
  } else {
    say(s.san ? renderSan(s.san, algebraic) : 'Game start', cut);
  }
}
