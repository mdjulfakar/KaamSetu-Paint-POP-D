// Run only on a trusted administrator's computer/server with Firebase Admin SDK
// and Application Default Credentials configured. Never ship credentials in the APK.
const admin = require('firebase-admin');
const phone = process.argv[2];
if (!phone) throw new Error('Usage: node scripts/set-admin-claim.js +91XXXXXXXXXX');
admin.initializeApp({ credential: admin.credential.applicationDefault() });
(async () => {
  const user = await admin.auth().getUserByPhoneNumber(phone);
  await admin.auth().setCustomUserClaims(user.uid, { admin: true });
  console.log(`Admin claim set for UID ${user.uid}. Ask the user to sign out and sign in again.`);
  process.exit(0);
})().catch(err => { console.error(err); process.exit(1); });
