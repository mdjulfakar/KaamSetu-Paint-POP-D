set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

echo "===== KAAMSETU COMPLETE SOURCE REPAIR ====="

# जरूरी फाइलें जाँचें
test -f MainActivity_old.kt
test -f app/build.gradle.kts
test -f app/AndroidManifest.xml
test -f app/google-services.json
test -f .github/workflows/build.yml

# असली Kotlin ऐप को सही जगह रखें
mkdir -p app/src/main/java/com/kaamsetu/app
cp MainActivity_old.kt app/MainActivity_KaamSetu_Fresh.kt

python3 - <<'PY'
from pathlib import Path
import sys

p = Path("app/MainActivity_KaamSetu_Fresh.kt")
s = p.read_text(encoding="utf-8")

def patch(old, new, name):
    global s
    count = s.count(old)
    if count != 1:
        sys.exit(f"ERROR: {name}: expected 1 match, found {count}. No patched file saved.")
    s = s.replace(old, new, 1)
    print("OK:", name)

# 1. Registration success dialog state
patch(
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

# 2. Registration के बाद automatic login बंद
patch(
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
"Registration callback"
)

# 3. Painter profile update पर UI state भी अपडेट
patch(
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

# 4. Painter profile create callback पर भी UI state अपडेट
patch(
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

# 5. Booking save होते ही My Bookings में तुरंत दिखे
patch(
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

# 6. Registration success dialog UI
patch(
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
                Text(tr("Registration Successful", "रजिस्ट्रेशन सफल"))
            },
            text = {
                Text(tr(
                    "Your account has been created successfully. Please press OK and log in when you are ready.",
                    "आपका अकाउंट सफलतापूर्वक बन गया है। OK दबाएँ और जब चाहें खुद लॉगिन करें।"
                ))
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

# फोटो अपलोड हटाया हुआ ही रहे
for word in ["FirebaseStorage", "photoPicker", "bookingPhotos"]:
    if word in s:
        sys.exit("ERROR: Removed photo feature found: " + word)

# जरूरी फीचर सत्यापन
checks = {
    "Email/password registration": "createUserWithEmailAndPassword",
    "Email/password login": "signInWithEmailAndPassword",
    "Admin permission claim": 'token.claims["admin"]',
    "Painter assignment": "fun assignWorker",
    "Customer booking": "fun createBooking",
    "Customer booking list": "My Bookings",
    "Registration dialog": "showRegistrationSuccessDialog",
    "Instant booking refresh": "BookingRow(document.id, booking)"
}

for name, needle in checks.items():
    if needle not in s:
        sys.exit("ERROR: Missing required feature: " + name)
    print("VERIFIED:", name)

if s.count("painterExperience = painterExperience.trim()") != 2:
    sys.exit("ERROR: Both painter profile callbacks were not patched.")

p.write_text(s, encoding="utf-8")
print("SUCCESS: Kotlin source repaired.")
print("Kotlin lines:", len(s.splitlines()))
PY

# Workflow द्वारा इस्तेमाल होने वाली Kotlin फाइल को सही जगह भी रखें
cp app/MainActivity_KaamSetu_Fresh.kt \
  app/src/main/java/com/kaamsetu/app/MainActivity.kt

# गलत Bash-script वाली Kotlin फाइल अब असली Kotlin source है
grep -q '^package com.kaamsetu.app' \
  app/MainActivity_KaamSetu_Fresh.kt

grep -q 'fun assignWorker' \
  app/MainActivity_KaamSetu_Fresh.kt

grep -q 'fun createBooking' \
  app/MainActivity_KaamSetu_Fresh.kt

grep -q 'showRegistrationSuccessDialog' \
  app/MainActivity_KaamSetu_Fresh.kt

git diff --check

echo "===== SOURCE CHECK PASSED ====="

# दोनों source files GitHub पर भेजें
git add app/MainActivity_KaamSetu_Fresh.kt

git commit -m "Restore KaamSetu Kotlin source and apply registration profile booking fixes"

git push origin HEAD:main

echo "===== PUSH COMPLETED ====="
echo "अब GitHub Actions में नया APK build देखें:"
echo "https://github.com/mdjulfakar/KaamSetu-Paint-POP-D/actions"

