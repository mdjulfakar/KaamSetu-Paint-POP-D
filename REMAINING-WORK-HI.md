# KaamSetu – बाकी काम और ईमानदार स्थिति

## इस सोर्स अपडेट में जोड़ा गया
- हिंदी/English स्विच और मुख्य स्क्रीन/फॉर्म/एडमिन लेबल का द्विभाषी रूप।
- ग्राहक बुकिंग और कारीगर रजिस्ट्रेशन; नाम, 10-अंकीय फोन, जिला, इलाका, काम का प्रकार, विवरण और चुनी गई फोटो URI सेव।
- Admin के लिए स्थानीय बुकिंग/कारीगर रिकॉर्ड, स्थिति बदलना, WhatsApp संपर्क और फोटो खोलना।
- Admin द्वारा मंजूर कारीगर के फोन पर बुकिंग असाइन करने का स्थानीय विकल्प।
- Worker Login स्क्रीन (रजिस्ट्रेशन में दर्ज फोन और Admin की मंजूरी जाँचती है) तथा सौंपे गए काम को Accepted/Rejected/In Progress/Completed करने का स्थानीय विकल्प।
- पुराने रिकॉर्ड में `assignedWorkerPhone` न होने पर खाली मान से पढ़ने की backward compatibility।

## अभी उत्पादन के लिए अधूरा / रिलीज़ न करें
1. सभी डेटा SharedPreferences में उसी फोन पर है; किसी दूसरे फोन पर अपने-आप नहीं जाता।
2. Worker Login सिर्फ उसी डिवाइस पर सेव रिकॉर्ड और फोन नंबर मिलाता है। यह OTP या सुरक्षित पहचान नहीं है।
3. Admin PIN अभी demo PIN `123456` है; यह सुरक्षित प्रमाणीकरण नहीं है। सार्वजनिक APK में उपयोग न करें।
4. फोटो का URI लोकल फोन का URI है; क्लाउड पर अपलोड/साझा नहीं होता।
5. Firebase SDK/Authentication/Firestore/Storage अभी ऐप में integrate नहीं हैं। `google-services.json` अकेले Firebase integration नहीं करता।
6. Android SDK/Gradle उपलब्ध न होने से इस वातावरण में APK build और असली डिवाइस टेस्ट नहीं हुआ।

## रिलीज़ से पहले आवश्यक
- Firebase Console में Authentication provider और Firestore/Storage enable करना।
- Admin/Worker role आधारित सुरक्षित access rules; सार्वजनिक read/write rules नहीं।
- Firebase SDK जोड़ना, सभी create/read/update flows को cloud repository से जोड़ना और photo upload करना।
- OTP या सुरक्षित account login; demo PIN हटाना और admin role server-side verify करना।
- GitHub Actions/Android SDK से APK build, install और कम-से-कम दो अलग फोन पर end-to-end परीक्षण।

## लागत नीति
इस सोर्स में कोई paid API, payment gateway, subscription या paid hosting नहीं जोड़ी गई। Firebase के free tier की सीमाएँ/उपलब्धता बदल सकती हैं; उपयोग बढ़ने पर कोई paid billing चालू करने से पहले मालिक की स्पष्ट मंजूरी जरूरी है।
