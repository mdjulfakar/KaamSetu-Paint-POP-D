# KaamSetu – Firebase SDK scaffold v1.3

यह पैकेज v1.2 के नवीनतम ऐप सोर्स पर आधारित है।

## इस अपडेट में तकनीकी तैयारी
- Google Services Gradle plugin जोड़ा गया।
- Firebase BoM तथा Authentication, Cloud Firestore और Cloud Storage SDK dependencies जोड़ी गईं।
- Android INTERNET permission जोड़ी गई।
- मौजूदा `app/google-services.json` बरकरार है।
- GitHub Actions workflow बरकरार है; इससे debug APK build किया जा सकता है।

## बहुत जरूरी: अभी क्या नहीं हुआ
यह SDK scaffold है, पूर्ण ऑनलाइन Firebase ऐप नहीं। मौजूदा Kotlin ऐप का booking/worker/admin डेटा अभी स्थानीय SharedPreferences में रहता है। अभी Firebase Auth login, Firestore read/write, Storage photo upload और सुरक्षित role-based rules implement/verify नहीं हुए हैं। Firebase Console में Authentication provider, Firestore database और Storage setup भी मालिक के खाते से करना बाकी है।

इसलिए इसे production में प्रकाशित न करें और वास्तविक ग्राहक डेटा न डालें। Firebase Console में public read/write rules (`allow read, write: if true`) कभी न लगाएँ। Admin PIN demo है; इसे production authentication न मानें।

## लागत
इस पैकेज में कोई payment gateway, paid hosting, subscription या automatic payment नहीं है। Firebase billing upgrade/paid plan मालिक की स्पष्ट अनुमति के बिना न करें।

## अगला चरण
Firebase Console में Authentication provider enable करना, Firestore/Storage बनाना, फिर authenticated UID और सुरक्षित rules के साथ ऐप के booking/worker/admin flows को cloud repository से जोड़ना और दो अलग फोन पर जाँचना।
