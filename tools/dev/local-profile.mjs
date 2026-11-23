// A named local profile is restored on every load, unlike the anonymous "Start locally" user, which lasts one visit.
// Seed it before navigating so tests can reload and continue with the same user and locally stored answers.
export async function seedLocalProfile(page, {name = 'Local Student', id = 'local-student', mail = 'local.student@example.org'} = {}) {
  const profile = JSON.stringify({user: {name, id, mail}, config: {syncDestinations: [{
    syncDestination: {$type: 'LocalIndexedDbSyncDefinition', dbName: 'EvaDidInteractionDB', storeName: 'variableHistoryStore', dbVersion: 1},
    syncStrategy: 'SYNC_LAST'}], isOnlineAccount: false}});
  await page.addInitScript(saved => localStorage.setItem('edusquirrel-alluserinfo', saved), profile);
}
