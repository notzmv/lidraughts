import { ops as treeOps } from 'tree';
import { san2alg } from 'draughts';

export default function (initialNode: Tree.Node, solution, color: Color, algebraic: boolean): Tree.Node | undefined {

  const markNode = function (node) {
    if ((color === 'white') === ((node.displayPly ? node.displayPly : node.ply) % 2 === 1)) node.puzzle = 'good';
  }

  var mergedSolution = treeOps.mergeExpandedNodes(solution);
  treeOps.updateAll(mergedSolution, markNode);

  const solutionNode = treeOps.childById(initialNode, mergedSolution.id);

  var merged: Tree.Node | undefined;
  if (solutionNode) {
    merged = treeOps.merge(solutionNode, mergedSolution, solution);
    if (merged) treeOps.updateAll(merged, markNode);
  }
  else initialNode.children.push(mergedSolution);
  
  if (algebraic) {
    treeOps.updateAll(initialNode, function(node) {
      node.alg = san2alg(node.san)
    })
  }

  return merged;
};
