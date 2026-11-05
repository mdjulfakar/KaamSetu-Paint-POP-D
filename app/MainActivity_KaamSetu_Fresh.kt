
set -euo pipefail

# ==========================================
# KAAMSETU – PAINT & P.O.P
# COMPLETE FRESH SOURCE RESTORE + REPAIR
# ==========================================

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

echo "KaamSetu project folder:"
pwd

# जरूरी फाइलों की जाँच
test -f "MainActivity_old.kt" || {
  echo "ERROR: MainActivity_old.kt नहीं मिली।"
  exit 1
}

test -f "app/build.gradle.kts" || {
  echo "ERROR: app/build.gradle.kts नहीं मिली।"
  exit 1
}

test -f "app/AndroidManifest.xml" || {
  echo "ERROR: AndroidManifest.xml नहीं मिली।"
  exit 1
}

test -f "app/google-services.json" || {
  echo "ERROR: Firebase google-services.json नहीं मिली।"
  exit 1
}

# STEP 1: असली Kotlin फाइल वापस रखें
mkdir -p app
cp MainActivity_old.kt app/MainActivity_KaamSetu_Fresh.kt

echo "STEP 1 OK: असली Kotlin source वापस रख दिया गया।"

# STEP 2: तीनों सुधार लगाएँ
python3 - <<'PY'
from pathlib import Path
import sys

p = Path("app/MainActivity_KaamSetu_Fresh.kt")
s = p.read_text(encoding="utf-8")

def replace_once(old, new, name):
    global s
    count = s.count(old)
    if count != 1:
        sys.exit(
            f"ERROR: {name} का सही हिस्सा नहीं मिला। "
            f"मिले: {count}. फाइल सुरक्षित रखी गई।"
        )
    s = s.replace(old, new, 1)

# सुधार 1: Registration success dialog की state
replace_once(
'''    var registering by remember {
        mutableStateOf(false)
    }

    var currentUser by remember {''',
'''    var registering by remember {
        mutableStateOf(false)
    }

    var showRegistrationSuccessDialog by remember {
        mutableStateOf(false)
    }

    var currentUser by remember {''',
"Registration dialog state"
)

# सुधार 2: Registration के बाद अपने आप login नहीं होगा।
# Firebase में अकाउंट सेव होगा, logout होगा और OK वाला dialog दिखेगा।
replace_once(
'''                        loading = false

                        message = tr(
                            "Successfully Submitted! Please log in.",
                            "सफलतापूर्वक जमा हुआ! अब लॉगिन करें।"
                        )

                        registering = false
                        password = ""

                        auth.signOut()
                        currentUser = null''',
'''                        loading = false
                        registering = false
                        password = ""

                        auth.signOut()
                        currentUser = null
                        userRole = ""
                        userName = ""
                        bookings = emptyList()
                        screen = "auth"
                        message = ""
                        showRegistrationSuccessDialog = true''',
"Registration success callback"
)

# सुधार 3A: Painter profile update के बाद
# सभी बदली हुई जानकारी स्क्रीन पर भी अपडेट होगी।
replace_once(
'''                loading = false
                userName = fullName.trim()

                message = tr(
                    "Painter profile saved successfully!",''',
'''                loading = false
                userName = fullName.trim()
                phone = phone.trim()
                address = address.trim()
                painterSkills = painterSkills.trim()
                painterExperience = painterExperience.trim()

                message = tr(
                    "Painter profile saved successfully!",''',
"Painter profile update callback"
)

# सुधार 3B: अगर profile document मौजूद नहीं है,
# तब नया document सेव होने पर भी वही state update होगी।
replace_once(
'''                        loading = false
                        userName = fullName.trim()

                        message = tr(
                            "Painter profile saved successfully!",''',
'''                        loading = false
                        userName = fullName.trim()
                        phone = phone.trim()
                        address = address.trim()
                        painterSkills = painterSkills.trim()
                        painterExperience = painterExperience.trim()

                        message = tr(
                            "Painter profile saved successfully!",''',
"Painter profile create callback"
)

# सुधार 4: Booking सेव होते ही My Bookings में दिखेगी।
replace_once(
'''        db.collection("bookings")
            .add(booking)
            .addOnSuccessListener {

                loading = false''',
'''        db.collection("bookings")
            .add(booking)
            .addOnSuccessListener { document ->

                loading = false
                bookings = listOf(BookingRow(document.id, booking)) +
                    bookings.filterNot { it.id == document.id }''',
"Immediate My Bookings update"
)

# Registration success dialog को स्क्रीन में जोड़ें।
replace_once(
'''        }
    }
}

// District selection''',
'''        }
    }

    if (showRegistrationSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showRegistrationSuccessDialog = false
            },
            title = {
                Text(
                    tr(
                        "Registration Successful",
                        "रजिस्ट्रेशन सफल"
                    )
                )
            },
            text = {
                Text(
                    tr(
                        "Your account has been created successfully. Please press OK and log in when you are ready.",
                        "आपका अकाउंट सफलतापूर्वक बन गया है। OK दबाएँ और जब चाहें खुद लॉगिन करें।"
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRegistrationSuccessDialog = false
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}

// District selection''',
"Registration success dialog UI"
)

# सुरक्षा जाँच: फोटो अपलोड दोबारा नहीं आना चाहिए।
for forbidden in (
    "FirebaseStorage",
    "photoPicker",
    "bookingPhotos"
):
    if forbidden in s:
        sys.exit(
            f"ERROR: हटाया गया फोटो कोड वापस मिला: {forbidden}"
        )

# सभी सुधारों की जाँच
if "BookingRow(document.id, booking)" not in s:
    sys.exit("ERROR: My Bookings update नहीं मिला।")

if s.count("painterExperience = painterExperience.trim()") != 2:
    sys.exit("ERROR: Painter profile के दोनों callbacks verify नहीं हुए।")

if "showRegistrationSuccessDialog = true" not in s:
    sys.exit("ERROR: Registration dialog callback verify नहीं हुआ।")

if "Your account has been created successfully." not in s:
    sys.exit("ERROR: Registration dialog का message नहीं मिला।")

p.write_text(s, encoding="utf-8")

print("STEP 2 OK: सभी सुधार सेव हो गए।")
print("नई Kotlin file lines:", len(s.splitlines()))
print("Photo upload: REMOVED")
print("Registration dialog: OK")
print("Painter profile callbacks: OK")
print("Immediate My Bookings update: OK")
PY

# STEP 3: Kotlin source की बुनियादी जाँच
grep -q "package com.kaamsetu.app" \
  app/MainActivity_KaamSetu_Fresh.kt

grep -q "createUserWithEmailAndPassword" \
  app/MainActivity_KaamSetu_Fresh.kt

grep -q "signInWithEmailAndPassword" \
  app/MainActivity_KaamSetu_Fresh.kt

grep -q "My Bookings" \
  app/MainActivity_KaamSetu_Fresh.kt

grep -q "showRegistrationSuccessDialog" \
  app/MainActivity_KaamSetu_Fresh.kt

echo "STEP 3 OK: Source verification complete."

# STEP 4: Git changes की जाँच
git diff --check

# STEP 5: GitHub पर नया source भेजें
git add app/MainActivity_KaamSetu_Fresh.kt

git commit -m "Restore fresh KaamSetu Kotlin source and fix registration, profile and bookings"

git push origin HEAD:main

echo ""
echo "=========================================="
echo "KAAMSETU SOURCE REPAIR PUSHED"
echo "=========================================="
echo "अब GitHub Actions में APK build शुरू होना चाहिए।"
echo "यह APK तैयार होने की पुष्टि नहीं है।"
echo "Build सफल होने के बाद ही APK डाउनलोड करें।"
echo ""
echo "Actions खोलें:"
echo "https://github.com/mdjulfakar/KaamSetu-Paint-POP-D/actions"



