# KaamSetu – तकनीकी जाँच और अगला चरण (v1.3)

## इस पैकेज में शामिल
- Kotlin + Jetpack Compose Android ऐप, हिंदी/English UI
- Firebase Phone OTP Authentication
- Firestore में customer/worker records save और sync
- Firebase Storage में चुनी गई फोटो upload
- Admin custom claim जाँच; डेमो PIN हटाया गया
- Firestore और Storage security rules
- GitHub Actions workflow से Debug APK build

## इस बार की जाँच
- ZIP archive सफलतापूर्वक extract हुआ और source files मौजूद हैं।
- `google-services.json` का package `com.kaamsetu.app` है।
- Gradle config में Firebase Auth, Firestore और Storage dependencies मौजूद हैं।
- Kotlin source में Firebase OTP, record save, snapshot sync और photo upload calls मौजूद हैं।
- इस runtime में Android SDK/Gradle उपलब्ध नहीं हैं; इसलिए Kotlin/Gradle compile, APK install, OTP SMS, Firestore live access और वास्तविक फोन पर फोटो upload सत्यापित नहीं हुए।

## Firebase Console में जरूरी
1. Authentication में Phone provider enable करें और SMS region policy में India allow करें।
2. Firestore Database बनाएँ और `firestore.rules` deploy करें।
3. Storage enable करें और `storage.rules` deploy करें।
4. Android app `com.kaamsetu.app` के signing certificate SHA-1 और SHA-256 जोड़ें।
5. Admin फोन खाते को trusted Firebase Admin SDK प्रक्रिया से `admin=true` custom claim दें। Admin claim script `scripts/set-admin-claim.js` में है; service-account key कभी APK/GitHub/chat में न रखें।

## अभी बाकी / सावधानी
- GitHub Actions में source push करके `Build KaamSetu APK` workflow चलाना और हरे build की पुष्टि करना।
- APK को Android फोन पर install करके OTP, booking, worker registration, admin approval/assignment, worker status update और photo flow जाँचना।
- कम-से-कम दो अलग फोन से cross-device test करना।
- Worker photo access, privacy notice, data deletion/retention और production security review की अलग जाँच करना।
- Worker को वही verified phone number इस्तेमाल करना होगा जिस पर OTP login हुआ है।

## लागत नीति
कोई paid hosting, payment gateway, subscription या automatic payment नहीं जोड़ा गया। Firebase billing/paid plan मालिक की स्पष्ट अनुमति के बिना enable न करें। Phone OTP SMS पर Firebase की लागू सीमाएँ/शुल्क हो सकते हैं; पहले Firebase test phone numbers से जाँचें।
