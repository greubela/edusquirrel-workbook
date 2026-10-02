import type { BoundaryEdge } from "../types";

export function getBoundaryEdgeId(edge: BoundaryEdge) {
  return `${edge.direction}-${edge.fixed}-${edge.start}-${edge.end}`;
}

export function getPerimeterLength(edges: BoundaryEdge[]) {
  return edges.reduce(
    (totalLength, edge) => totalLength + edge.end - edge.start,
    0,
  );
}
