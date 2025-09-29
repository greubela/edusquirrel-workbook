# Persistent State Platform Design

## Goal
Enable exercises to persist their full interaction state and history across sessions with pluggable storage backends. Support offline-first behavior so previously loaded exercises remain usable and synchronize once connectivity is restored.

## Scope
- Provide a unified persistence interface consumed by interaction plugins.
- Implement adapters for browser storage (IndexedDB/localStorage), REST APIs, and SQL databases.
- Capture chronological state snapshots and relevant metadata, not just the latest value.
- Support manual and automatic synchronization strategies (e.g., user-triggered save, startup restore, scheduled sync).
- Deliver conflict resolution, versioning, and audit trails consistent with Interaction History.

## Guiding Principles
1. **Pluggability**: Frontend and backend should interact through well-defined interfaces allowing new storage backends without touching exercise code.
2. **Offline-first**: Exercises should function without connectivity after initial load, queueing mutations locally.
3. **Resilience**: Handle network failures gracefully with retry policies and transparent user feedback.
4. **Security**: Encrypt sensitive payloads, respect user privacy, and enforce access control per course/enrollment.

## Architecture Overview
```
Interaction Plugin --> Persistence SDK --> Sync Orchestrator --> Backend Adapter(s)
                                       |                      |
                                       |--> Local Cache ------
```
- **Persistence SDK**: API surface for exercises (`saveSnapshot`, `loadHistory`, `subscribe`, `resolveConflicts`).
- **Local Cache**: IndexedDB store holding pending writes, cached snapshots, and metadata.
- **Sync Orchestrator**: Manages queueing, batching, retries, and merge policies; emits lifecycle events.
- **Backend Adapters**:
  - *Browser Storage*: for standalone/offline use.
  - *REST API*: interacts with existing LMS services.
  - *SQL Adapter*: server-side service connecting to relational databases.

## Data Model
- `record_id` (UUID)
- `element_instance_id`
- `user_id`
- `timestamp`
- `state_version` (monotonic per element)
- `state_payload` (JSON diff or snapshot)
- `metadata` (device info, offline flag, checksum)
- `sync_status` (LOCAL_ONLY, SYNCED, CONFLICT)

## Client API Sketch
```ts
interface PersistenceClient {
  saveSnapshot(elementId: string, state: ExerciseState, options?: SaveOptions): Promise<SaveResult>;
  loadHistory(elementId: string, range?: TimeRange): Promise<Snapshot[]>;
  loadLatest(elementId: string): Promise<Snapshot | null>;
  subscribe(elementId: string, listener: (event: PersistenceEvent) => void): Unsubscribe;
  triggerSync(elementId?: string): Promise<SyncReport>;
}
```

## Sync Workflow
1. Exercise invokes `saveSnapshot`.
2. Persistence SDK writes to local cache and emits `LOCAL_SAVED` event.
3. Sync Orchestrator schedules backend sync (immediate if online, deferred otherwise).
4. Backend adapter persists data and returns canonical version/ack.
5. SDK updates local cache status; if conflict detected, merges using policy (last-writer-wins, OT/CRDT, or manual resolution).
6. `PersistenceEvent`s inform UI to update status badges (e.g., "Saved", "Offline - changes pending").

## Offline Behavior
- Cache bootstrapping: on initial load, download latest snapshots and queue for offline use.
- Background sync: attempt sync when connectivity changes to online.
- Manual control: provide UI to trigger sync/resolve conflicts.
- Storage quotas: monitor available space, prompt user if nearing limits.

## Security & Compliance
- Encrypt at rest (local) using WebCrypto; secure transport via HTTPS.
- Token-based auth for REST/SQL adapters; rotate credentials.
- Respect data residency policies per institution.

## Observability
- Instrument metrics (latency, failure rate, pending queue size).
- Emit logs/traces correlated with user/session IDs.
- Provide admin dashboards for sync status across cohorts.

## Dependencies & Integration
- Align event schema with Interaction History to avoid duplication.
- Reuse existing authentication context and course roster services.
- Provide migration path from current ephemeral state storage.

## Open Questions
- Which conflict resolution strategy is acceptable pedagogically?
- Do we need differential sync (state diffs) to reduce payload size?
- How to expose history export for compliance requests?
- Should offline cache be cleared per course or global?

## Milestones
1. Design persistence interfaces and shared TypeScript types.
2. Implement local cache and browser storage adapter.
3. Deliver REST adapter & reference backend service.
4. Add SQL adapter for institutional deployments.
5. Integrate with two pilot interaction plugins and iterate.
