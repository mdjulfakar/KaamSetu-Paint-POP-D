
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

echo "===== KAAMSETU SOURCE REPAIR START ====="

test -f MainActivity_old.kt
test -f app/build.gradle.kts
test -f app/AndroidManifest.xml
test -f app/google-services.json
test -f .github/workflows/build.yml

mkdir -p app/src/main/java/com/kaamsetu/app

python3 - <<'PY'
from pathlib import Path
import sys

source = Path("MainActivity_old.kt")
target = Path("app/MainActivity_KaamSetu_Fresh.kt")
s = source.read_text(encoding="utf-8")

if not s.startswith("package com.kaamsetu.app"):
    sys.exit("ERROR: MainActivity_old.kt is not the expected Kotlin source.")

def patch(old, new, name):
    global s
    count = s.count(old)
    if count != 1:
        sys.exit(f"ERROR: {name}: expected 1 match, found {count}.")
    s = s.replace(old, new, 1)
    print("PATCH OK:", name)

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

# 2. Registration के बाद automatic login बंद और success dialog
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

# 3. Painter profile update callback
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
"Painter profile update"
)

# 4. Painter profile create callback
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
"Painter profile create"
)

# 5. Booking immediately appears in My Bookings
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
"Immediate booking list update"
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

# Photos must remain removed
for word in ["FirebaseStorage", "photoPicker", "bookingPhotos"]:
    if word in s:
        sys.exit("ERROR: Photo-upload code unexpectedly found: " + word)

checks = {
    "Package": "package com.kaamsetu.app",
    "Email/password registration": "createUserWithEmailAndPassword",
    "Email/password login": "signInWithEmailAndPassword",
    "Admin permission": 'token.claims["admin"]',
    "Customer booking": "fun createBooking",
    "My Bookings": "My Bookings",
    "Painter assignment": "fun assignWorker",
    "Registration dialog": "showRegistrationSuccessDialog",
    "Instant booking refresh": "BookingRow(document.id, booking)"
}

for name, value in checks.items():
    if value not in s:
        sys.exit("ERROR: Missing required feature: " + name)
    print("VERIFIED:", name)

if s.count("painterExperience = painterExperience.trim()") != 2:
    sys.exit("ERROR: Both painter profile callbacks were not fixed.")

target.write_text(s, encoding="utf-8")

# Workflow guard: reject shell commands accidentally saved as Kotlin.
workflow = Path(".github/workflows/build.yml")
w = workflow.read_text(encoding="utf-8")
old = "          cp app/MainActivity_KaamSetu_Fresh.kt app/src/main/java/com/kaamsetu/app/MainActivity.kt\n"
new = """          grep -q '^package com.kaamsetu.app$' app/MainActivity_KaamSetu_Fresh.kt
          ! grep -q '^set -euo pipefail$' app/MainActivity_KaamSetu_Fresh.kt

          cp app/MainActivity_KaamSetu_Fresh.kt app/src/main/java/com/kaamsetu/app/MainActivity.kt

          grep -q '^package com.kaamsetu.app$' app/src/main/java/com/kaamsetu/app/MainActivity.kt
"""
if w.count(old) != 1:
    sys.exit("ERROR: Workflow copy command did not match exactly once.")
workflow.write_text(w.replace(old, new, 1), encoding="utf-8")

print("KOTLIN SOURCE CREATED:", len(s.splitlines()), "lines")
print("WORKFLOW SOURCE CHECK ADDED")
PY

# Verify both source files are real Kotlin, not shell scripts.
cp app/MainActivity_KaamSetu_Fresh.kt \
   app/src/main/java/com/kaamsetu/app/MainActivity.kt

grep -q '^package com.kaamsetu.app$' app/MainActivity_KaamSetu_Fresh.kt
grep -q '^package com.kaamsetu.app$' app/src/main/java/com/kaamsetu/app/MainActivity.kt
grep -q 'showRegistrationSuccessDialog' app/MainActivity_KaamSetu_Fresh.kt
grep -q 'BookingRow(document.id, booking)' app/MainActivity_KaamSetu_Fresh.kt
! grep -q '^set -euo pipefail$' app/MainActivity_KaamSetu_Fresh.kt

echo "===== BOTH KOTLIN FILE CHECKS PASSED ====="

git add app/MainActivity_KaamSetu_Fresh.kt .github/workflows/build.yml
git commit -m "Repair KaamSetu Kotlin source and fix registration booking profile UI"
git push origin HEAD:main

echo "===== PUSH COMPLETED ====="
echo "अब GitHub Actions में नया APK build देखें:"
echo "https://github.com/mdjulfakar/KaamSetu-Paint-POP-D/actions"


