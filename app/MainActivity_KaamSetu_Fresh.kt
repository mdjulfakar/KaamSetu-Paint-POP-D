set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

echo "===== KAAMSETU RESTORE AND REPAIR START ====="

GOOD_COMMIT="e5a522fccd91850e1b5b7d5890308047476ac449"
SOURCE="app/MainActivity_KaamSetu_Fresh.kt"
WORKFLOW=".github/workflows/build.yml"

git fetch origin main
git cat-file -e "${GOOD_COMMIT}^{commit}" 2>/dev/null || git fetch origin "$GOOD_COMMIT"

mkdir -p app
git show "${GOOD_COMMIT}:${SOURCE}" > "$SOURCE"

python3 - <<'PY'
from pathlib import Path

source = Path("app/MainActivity_KaamSetu_Fresh.kt")
code = source.read_text(encoding="utf-8")

def replace_once(old, new, name):
    global code
    count = code.count(old)
    if count != 1:
        raise SystemExit(
            f"ERROR: {name}: expected exactly 1 match, found {count}. "
            "No incomplete patch will be saved."
        )
    code = code.replace(old, new, 1)
    print("PATCH OK:", name)

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

replace_once(
'''                        message = tr(
                            "Successfully Submitted! Please log in.",
                            "सफलतापूर्वक जमा हुआ! अब लॉगिन करें।"
                        )

                        registering = false
                        password = ""

                        screen = "auth";



''',
'''                        auth.signOut()
                        currentUser = null
                        userRole = ""
                        userName = ""
                        registering = false
                        password = ""
                        screen = "auth"
                        message = ""
                        showRegistrationSuccessDialog = true

''',
"Registration must not auto-login"
)

replace_once(
'''                loading = false
                userName = fullName.trim()

                message = tr(
                    "Painter profile saved successfully!",
                    "पेंटर की जानकारी सफलतापूर्वक सेव हुई!"
                )''',
'''                loading = false
                userName = fullName.trim()
                fullName = fullName.trim()
                phone = phone.trim()
                address = address.trim()
                painterSkills = painterSkills.trim()
                painterExperience = painterExperience.trim()

                message = tr(
                    "Painter profile saved successfully!",
                    "पेंटर की जानकारी सफलतापूर्वक सेव हुई!"
                )''',
"Painter profile update callback"
)

replace_once(
'''                        loading = false
                        userName = fullName.trim()

                        message = tr(
                            "Painter profile saved successfully!",
                            "पेंटर की जानकारी सफलतापूर्वक सेव हुई!"
                        )''',
'''                        loading = false
                        userName = fullName.trim()
                        fullName = fullName.trim()
                        phone = phone.trim()
                        address = address.trim()
                        painterSkills = painterSkills.trim()
                        painterExperience = painterExperience.trim()

                        message = tr(
                            "Painter profile saved successfully!",
                            "पेंटर की जानकारी सफलतापूर्वक सेव हुई!"
                        )''',
"Painter profile create callback"
)

replace_once(
'''                details = ""
                loadBookings(user, "customer")''',
'''                bookings = listOf(BookingRow(document.id, booking)) + bookings
                details = ""''',
"Booking immediate list refresh"
)

replace_once(
'''    // Main screen
    Column(''',
'''    if (showRegistrationSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(tr("Registration Successful", "रजिस्ट्रेशन सफल"))
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
                        message = tr(
                            "Registration complete. Please log in.",
                            "रजिस्ट्रेशन पूरा हुआ। कृपया लॉगिन करें।"
                        )
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Main screen
    Column(''',
"Registration success dialog UI"
)

if not code.startswith("package com.kaamsetu.app"):
    raise SystemExit("ERROR: Kotlin package declaration missing.")

if "set -euo pipefail" in code:
    raise SystemExit("ERROR: Shell script text detected inside Kotlin file.")

source.write_text(code, encoding="utf-8")
print("Kotlin source saved:", len(code), "characters")
PY

python3 - <<'PY'
from pathlib import Path

p = Path(".github/workflows/build.yml")
text = p.read_text(encoding="utf-8")

old = "          cp app/MainActivity_KaamSetu_Fresh.kt app/src/main/java/com/kaamsetu/app/MainActivity.kt"
new = """          grep -q '^package com\\\\.kaamsetu\\\\.app$' app/MainActivity_KaamSetu_Fresh.kt
          if grep -q '^set -euo pipefail$' app/MainActivity_KaamSetu_Fresh.kt; then
            echo "ERROR: Shell script found in Kotlin source."
            exit 1
          fi

          cp app/MainActivity_KaamSetu_Fresh.kt app/src/main/java/com/kaamsetu/app/MainActivity.kt"""

if text.count(old) != 1:
    raise SystemExit("ERROR: Could not safely locate workflow copy command.")

p.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Workflow source validation added.")
PY

grep -q '^package com\.kaamsetu\.app$' "$SOURCE"
! grep -q '^set -euo pipefail$' "$SOURCE"

git diff --check
git add "$SOURCE" "$WORKFLOW"
git commit -m "Restore working KaamSetu Kotlin source and apply three UI fixes"
git push origin HEAD:main

echo "===== KAAMSETU REPAIR PUSHED ====="
echo "अब GitHub Actions में नया APK build देखें:"
echo "https://github.com/mdjulfakar/KaamSetu-Paint-POP-D/actions"

