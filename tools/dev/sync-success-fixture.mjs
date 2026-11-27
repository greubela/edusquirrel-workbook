// Match SyncSuccess[T]: committed writes/deletions name their elements, rather than returning counts.
export function syncSuccess({elementsAdded = [], elementsChanged = [], elementsUnchanged = [], elementsRemoved = []} = {}) {
  return {elementsAdded, elementsChanged, elementsUnchanged, elementsRemoved,
    timestampCommitted: new Date().toISOString().slice(0, -1)};
}
