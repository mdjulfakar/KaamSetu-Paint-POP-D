# KaamSetu – Paint & P.O.P: APK बनाने के निर्देश

## इस पैकेज में क्या है
- Android Kotlin + Jetpack Compose source
- हिंदी/English UI
- ग्राहक बुकिंग और gallery से फोटो चुनना
- कारीगर registration
- जम्मू-कश्मीर के 20 जिले + अन्य
- Admin demo dashboard और worker assignment
- Worker login और job status
- Firebase project configuration file
- GitHub Actions workflow: `.github/workflows/build-android.yml` (Java 17, Android SDK और Gradle 8.9 setup सहित)

## GitHub Actions से Debug APK
इस अपडेट में workflow को Android SDK setup के साथ मजबूत किया गया है और build से पहले clean build चलाया जाएगा।
1. GitHub में एक **Private** repository खोलें/बनाएँ, उदाहरण: `KaamSetuAndroid`.
2. इस ZIP को अपने फोन/कंप्यूटर में extract करें। ZIP के अंदर की सभी फाइलें और `.github` folder repository की root में upload करें (अंदर एक अतिरिक्त `KaamSetu-Android` folder न बनाएँ)।
3. GitHub repository में **Actions** खोलें। यदि workflow enable करने का संदेश आए तो enable करें।
4. `Build KaamSetu APK` workflow चुनें और **Run workflow** दबाएँ।
5. Run सफल होने पर नीचे **Artifacts** में `KaamSetu-debug-apk` डाउनलोड करें। ZIP artifact खोलने पर `app-debug.apk` मिलेगा।
6. पहले एक test Android phone पर install करके सभी screens जाँचें।

## अभी की ईमानदार तकनीकी स्थिति
- यह अभी local-storage MVP है। Bookings और worker records उसी फोन के SharedPreferences में सेव होते हैं; दूसरे फोन पर share नहीं होते।
- Gallery photo का URI स्थानीय है; Firebase Storage पर upload नहीं होता।
- Firebase Gradle dependencies और `google-services.json` मौजूद हैं, लेकिन app code में Authentication, Firestore और Storage का live integration तथा security rules अभी लागू नहीं हैं।
- Admin PIN `123456` demo-only है। इसे public/production use में न रखें।
- इस package को तैयार करते समय build environment में Android SDK/Gradle उपलब्ध नहीं थे और internet access से Gradle download भी संभव नहीं हुआ; इसलिए APK compilation या device test का दावा नहीं किया जा रहा। GitHub Actions workflow इसका build step है, लेकिन successful run के बाद ही APK build verified माना जाएगा।

## लागत और सुरक्षा
- कोई paid hosting, paid API, payment gateway, subscription या automatic payment इस package में नहीं जोड़ा गया।
- Firebase billing/paid plan कभी enable न करें जब तक मालिक स्पष्ट अनुमति न दे।
- Production से पहले secure admin/worker authentication, Firestore/Storage rules, privacy policy, data-safety review और कम-से-कम दो अलग phones पर end-to-end testing जरूरी है।
