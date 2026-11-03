# KaamSetu – Firebase सेटअप और टेस्ट

इस अपडेट में Android कोड में Firebase Phone OTP, Firestore record save/read और Firebase Storage photo upload जोड़ा गया है। ZIP बनना या source में code होना Firebase project पर live OTP भेजे जाने अथवा असली फोन पर सफल परीक्षण का प्रमाण नहीं है।

## Firebase Console में एक बार जरूरी सेटअप

1. Firebase Console में project `kaamsetu-2c8be` खोलें। Authentication → Sign-in method → Phone को Enable करें।
2. Authentication → Settings में SMS region policy जाँचें और India को अनुमति दें। OTP SMS पर शुल्क/सीमाएँ लागू हो सकती हैं। पहले Firebase के test phone numbers इस्तेमाल करें; उनसे वास्तविक SMS नहीं जाता।
3. Project settings → Your apps → Android app `com.kaamsetu.app` में अपने signing certificate के SHA-1 और SHA-256 fingerprints जोड़ें। Debug fingerprint पाने के लिए Android build environment में `./gradlew signingReport` या `gradle signingReport` चलाएँ। बदलाव के बाद ऐप फिर डाउनलोड/build करें।
4. Firestore Database बनाएं और इस प्रोजेक्ट की `firestore.rules` प्रकाशित करें। शुरुआत में Production mode चुनें; rules के बिना डेटाबेस सार्वजनिक न छोड़ें।
5. Storage bucket चालू करें और `storage.rules` प्रकाशित करें। तस्वीरें प्रति फाइल 5 MB से कम होनी चाहिए।

## Admin सुरक्षा

अब पुराना `123456` डेमो PIN हटाया गया है। Admin स्क्रीन Firebase ID token में `admin=true` custom claim जाँचती है। सामान्य मोबाइल OTP खाते को खुद Admin अधिकार नहीं मिल सकता। यह claim केवल विश्वसनीय server/Admin SDK से प्रोजेक्ट मालिक दे सकता है। किसी service-account JSON/private key को APK, ZIP, GitHub repository या चैट में कभी न डालें। जब तक claim न दिया जाए, Admin Dashboard बंद रहेगा।

## इस source में अभी क्या जुड़ा है

- ऐप खोलने से पहले Firebase Phone OTP लॉगिन।
- लॉगिन खाते के UID के साथ Firestore `records` collection में ग्राहक/कारीगर रिकॉर्ड सेव करना।
- चुनी गई फोटो को `record_photos/{uid}/{recordId}.jpg` में अपलोड करके URL Firestore में रखना।
- उपयोगकर्ता को अपने रिकॉर्ड, और कारीगर को उसके मोबाइल नंबर पर सौंपे गए काम पढ़ने की अनुमति देने वाला नियम।
- Admin claim वाले खाते को सभी रिकॉर्ड पढ़ने/अपडेट करने और delete करने की अनुमति।
- कारीगर केवल अपने assigned job का status बदल सकता है।

## अभी सत्यापित नहीं हुआ / अगला टेस्ट

इस रनटाइम में Android SDK और Gradle उपलब्ध नहीं हैं, इसलिए APK compile, emulator/device install, OTP SMS delivery, Firestore live read/write और Storage upload का परीक्षण नहीं किया जा सका। GitHub Actions workflow `Build KaamSetu APK` इस source को build करके debug APK artifact बना सकता है; GitHub Actions में हरा build सफल compile का प्रमाण होगा, लेकिन फोन पर OTP/फोटो परीक्षण अलग करना होगा।

Worker UID की जगह अभी Firebase के सत्यापित फोन नंबर (E.164) से assigned job access सीमित किया गया है। वास्तविक फोन पर यह जाँचना बाकी है कि नंबर का format, queries और rules साथ में सही चलें। इसे production-ready मानकर जारी न करें।
