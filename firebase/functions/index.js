/**
 * AURA — optional Cloud Functions.
 *
 * Deploying functions requires the Blaze (pay-as-you-go) plan. Nothing in the app depends on
 * them; they exist as the server-side extension point for future push delivery and share
 * handling. Keep it minimal: one authenticated health call, nothing that stores user data.
 */

const { onCall, HttpsError } = require('firebase-functions/v2/https');
const admin = require('firebase-admin');

admin.initializeApp();

/**
 * Authenticated health check. Returns server time and the caller's uid; useful to verify
 * the client → functions path, App Check enforcement and auth token propagation.
 */
exports.auraHealthcheck = onCall({ region: 'us-central1', cors: false }, (request) => {
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'AURA Cloud Functions require a signed-in user');
  }
  return {
    ok: true,
    uid: request.auth.uid,
    now: Date.now(),
  };
});
