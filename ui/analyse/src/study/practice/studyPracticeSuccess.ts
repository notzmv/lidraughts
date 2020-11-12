import AnalyseCtrl from '../../ctrl';
import { Goal } from './interfaces';
import { Comment } from '../../practice/practiceCtrl';

// returns null if not deep enough to know
function isDrawish(node: Tree.Node, v: VariantKey): boolean | null {
  if (!hasSolidEval(node, v)) return null;
  return !node.ceval!.win && Math.abs(node.ceval!.cp!) < 150;
}
// returns null if not deep enough to know
function isWinning(node: Tree.Node, goalCp: number, color: Color, v: VariantKey): boolean | null {
  if (!hasSolidEval(node, v)) return null;
  const cp = node.ceval!.win! > 0 ? 99999 : (node.ceval!.win! < 0 ? -99999 : node.ceval!.cp);
  return color === 'white' ? cp! >= goalCp : cp! <= goalCp;
}
// returns null if not deep enough to know
function myWinIn(node: Tree.Node, color: Color, v: VariantKey): number | boolean | null {
  if (!hasSolidEval(node, v)) return null;
  if (!node.ceval!.win) return false;
  var winIn = node.ceval!.win! * (color === 'white' ? 1 : -1);
  return winIn > 0 ? winIn : false;
}

function hasSolidEval(node: Tree.Node, v: VariantKey) {
  return node.ceval && node.ceval.depth >= (v === 'antidraughts' ? 7 : 17);
}

function isWin(root: AnalyseCtrl) {
  return root.gameOver() === 'checkmate';
}

function isMyWin(root: AnalyseCtrl) {
  return isWin(root) && root.turnColor() !== root.bottomColor();
}

function isTheirWin(root: AnalyseCtrl) {
  return isWin(root) && root.turnColor() === root.bottomColor();
}

function hasBlundered(comment: Comment | null) {
  return comment && (comment.verdict === 'mistake' || comment.verdict === 'blunder');
}

// returns null = ongoing, true = win, false = fail
export default function(root: AnalyseCtrl, goal: Goal, nbMoves: number): boolean | null {
  const node = root.node;
  if (!node.uci) return null;
  if (isTheirWin(root)) return false;
  if (isMyWin(root)) return true;
  if (hasBlundered(root.practice!.comment())) return false;
  const v = root.data.game.variant.key;
  switch (goal.result) {
    case 'drawIn':
    case 'equalIn':
      if (node.threefold) return true;
      if (isDrawish(node, v) === false) return false;
      if (nbMoves > goal.moves!) return false;
      if (root.gameOver() === 'draw') return true;
      if (nbMoves >= goal.moves!) return isDrawish(node, v);
      break;
    case 'evalIn':
      if (nbMoves >= goal.moves!) return isWinning(node, goal.cp!, root.bottomColor(), v);
      break;
    case 'winIn':
      if (nbMoves > goal.moves!) return false;
      const winIn = myWinIn(node, root.bottomColor(), v);
      if (winIn === null) return null;
      if (!winIn || (winIn as number) + nbMoves > goal.moves!) return false;
      break;
    case 'win':
  }
  return null;
};
