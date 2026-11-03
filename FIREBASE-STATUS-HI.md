# Firebase स्थिति

`app/google-services.json` में Firebase project configuration मौजूद है। लेकिन वर्तमान Gradle dependencies और Kotlin code में Firebase SDK का उपयोग नहीं है। इसलिए Firebase से कोई sign-in, database read/write या Storage upload अभी नहीं होता।

इसे सुरक्षित रूप से जोड़ने के लिए Firebase Console में मालिक को Authentication provider चालू करना, Firestore और Storage बनाना, और role-आधारित नियम लगाना होगा। ऐप को केवल authenticated UID के आधार पर सही customer/worker/admin record पढ़ने/बदलने देना होगा। कभी भी test के लिए `allow read, write: if true` जैसे सार्वजनिक नियम इस्तेमाल न करें।

अगला तकनीकी चरण: Firebase BoM + Auth + Firestore + Storage dependencies, email/phone OTP login, user role provisioning, Firestore repository, Storage upload और security rules। Admin role को client-side PIN से तय नहीं करना चाहिए; उसे विश्वसनीय admin-only प्रक्रिया से provision करना होगा।

इस समय कोई paid Firebase billing enable नहीं की गई है।
