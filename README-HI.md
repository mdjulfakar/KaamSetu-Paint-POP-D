# KaamSetu – Paint & P.O.P (Android source)

यह Android Studio/Gradle प्रोजेक्ट है। यह इंस्टॉल करने योग्य APK नहीं है।

## इस संस्करण में
- हिंदी और English भाषा स्विच।
- ग्राहक की बुकिंग और कारीगर रजिस्ट्रेशन फॉर्म।
- फोटो चुनने का विकल्प (फोटो URI इसी फोन पर रहता है)।
- Admin का स्थानीय Dashboard: रिकॉर्ड देखना, स्थिति बदलना, WhatsApp संपर्क और फोटो खोलना।
- मंजूर कारीगर को फोन नंबर से स्थानीय बुकिंग असाइन करना।
- Worker Portal: मंजूर कारीगर अपना असाइन किया काम देखकर Accepted/Rejected/In Progress/Completed कर सकता है।
- पुराने लोकल रिकॉर्ड भी लोड होते हैं; नया assignment field खाली माना जाएगा।

## महत्वपूर्ण सीमाएँ
- रिकॉर्ड अभी केवल उसी फोन के SharedPreferences में सेव हैं।
- Worker login फोन नंबर आधारित स्थानीय डेमो है; OTP/सुरक्षित login नहीं।
- Admin demo PIN `123456` है। इसे सुरक्षित login न समझें और इस रूप में सार्वजनिक रिलीज़ न करें।
- Firebase config file मौजूद है, पर Firebase SDK/Authentication/Firestore/Storage integration पूरा नहीं है। अलग-अलग फोन के बीच डेटा सिंक नहीं होगा।
- APK अभी इस कार्य वातावरण में build/install test नहीं हुई।

## Android Studio से Debug APK
1. Android Studio में यह प्रोजेक्ट खोलें।
2. Gradle sync पूरा होने दें (JDK 17 और Android SDK 35 आवश्यक)।
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)` चुनें।
4. सामान्य debug APK path: `app/build/outputs/apk/debug/app-debug.apk`।

GitHub Actions workflow उपलब्ध होने पर `:app:assembleDebug` चलाकर APK artifact अपलोड किया जा सकता है।

## Production release से पहले
`REMAINING-WORK-HI.md` और `FIREBASE-STATUS-HI.md` पढ़ें। सुरक्षित Firebase Auth/Firestore/Storage rules, online sync, photo upload, secure role checks और multi-device tests के बिना इसे live service के रूप में प्रकाशित न करें।

कोई paid service या automatic payment इस सोर्स में जोड़ा नहीं गया है।
