

set -e

# KaamSetu – complete fresh repair and build
ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path
import re, sys

p = Path("app/MainActivity_KaamSetu_Fresh.kt")
if not p.exists():
    sys.exit("ERROR: MainActivity_KaamSetu_Fresh.kt नहीं मिली। सही project folder खोलें।")

s = p.read_text(encoding="utf-8")
original_lines = len(s.splitlines())
print("पुरानी Kotlin lines:", original_lines)

# 1. Registration success dialog state: remove broken extra braces
old = '''var showRegistrationSuccessDialog by remember {
    mutableStateOf(false)
}
    mutableStateOf(false)
}'''
new = '''var showRegistrationSuccessDialog by remember {
    mutableStateOf(false)
}'''
if old in s:
    s = s.replace(old, new, 1)
else:
    print("NOTE: Registration state पहले से बदली हुई है या pattern अलग है।")

# 2. Repair the malformed Firestore registration success callback
pattern = r'(\\.set\\(profile\\)\\s*\\.addOnSuccessListener\\s*\\{).*?(\\s*\\.addOnFailureListener \\{ e ->)'
replacement = r'''\1
                        auth.signOut()
                        currentUser = null
                        userRole = ""
                        userName = ""
                        bookings = emptyList()

                        loading = false
                        registering = false
                        password = ""
                        screen = "auth"
                        message = ""

                        showRegistrationSuccessDialog = true
                    }
\2'''
s, count = re.subn(pattern, replacement, s, count=1, flags=re.S)
if count != 1:
    sys.exit("ERROR: Registration callback सुरक्षित तरीके से ठीक नहीं हुआ। फाइल नहीं बदली गई।")

# 3. Painter profile: update all visible profile fields after save
old_profile = '''loading = false
                        userName = fullName.trim()'''
new_profile = '''loading = false
                        userName = fullName.trim()
                        fullName = fullName.trim()
                        phone = phone.trim()
                        address = address.trim()
                        painterSkills = painterSkills.trim()
                        painterExperience = painterExperience.trim()'''
s, profile_count = s.replace(old_profile, new_profile), s.count(old_profile)
# Apply to both update() and fallback set() success callbacks
# (The replacement above is idempotent; verify both are present.)
if s.count("painterExperience = painterExperience.trim()") < 2:
    # Try the original indentation variant for the second callback
    old_profile2 = '''loading = false
                userName = fullName.trim()'''
    new_profile2 = '''loading = false
                userName = fullName.trim()
                fullName = fullName.trim()
                phone = phone.trim()
                address = address.trim()
                painterSkills = painterSkills.trim()
                painterExperience = painterExperience.trim()'''
    s = s.replace(old_profile2, new_profile2, 1)

# 4. Immediately show the newly created booking in My Bookings
old_booking = '''.addOnSuccessListener { document ->
                loading = false
                message = tr('''
new_booking = '''.addOnSuccessListener { document ->
                loading = false
                bookings = listOf(BookingRow(document.id, booking)) +
                    bookings.filterNot { it.id == document.id }
                message = tr('''
if old_booking in s:
    s = s.replace(old_booking, new_booking, 1)
elif "BookingRow(document.id, booking)" not in s:
    sys.exit("ERROR: Booking success block नहीं मिला।")

# 5. Fix the extra opening brace in registration success dialog
s = s.replace(
    "if (showRegistrationSuccessDialog) { {",
    "if (showRegistrationSuccessDialog) {",
    1
)

# Do not write a partial/failed patch
if "mutableStateOf(false)\n}\n    mutableStateOf(false)" in s:
    sys.exit("ERROR: Extra state braces अभी भी मौजूद हैं।")
if "if (showRegistrationSuccessDialog) { {" in s:
    sys.exit("ERROR: Dialog extra brace अभी भी मौजूद है।")

p.write_text(s, encoding="utf-8")
print("सुधार सेव हुए। नई Kotlin lines:", len(s.splitlines()))
print("पेंटर प्रोफाइल अपडेट:", s.count("painterExperience = painterExperience.trim()"))
print("नई बुकिंग तुरंत सूची में:", "BookingRow(document.id, booking)" in s)
PY

# Build the APK
echo "अब APK build शुरू हो रहा है..."
if [ -f "./gradlew" ]; then
  chmod +x ./gradlew
  ./gradlew :app:assembleDebug
else
  echo "ERROR: Gradle wrapper ./gradlew नहीं मिला।"
  exit 1
fi

# Prepare complete fresh project ZIP and APK output
mkdir -p KaamSetu-Fresh-Output

python3 - <<'PY'
from pathlib import Path
import zipfile, shutil, sys

root = Path.cwd()
out = root / "KaamSetu-Fresh-Output"
zip_path = out / "KaamSetu-Fresh-Complete-Project.zip"

skip = {".git", ".gradle", "build", "KaamSetu-Fresh-Output"}
with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
    for f in root.rglob("*"):
        if not f.is_file():
            continue
        rel = f.relative_to(root)
        if any(part in skip for part in rel.parts):
            continue
        z.write(f, rel)

apk = root / "app/build/outputs/apk/debug/app-debug.apk"
if not apk.exists():
    sys.exit("ERROR: APK file नहीं मिली। ZIP बनी है, लेकिन APK build verify नहीं हुआ।")

shutil.copy2(apk, out / "KaamSetu-Fresh-Debug.apk")
print("SUCCESS: पूरा Project ZIP:", zip_path)
print("SUCCESS: APK:", out / "KaamSetu-Fresh-Debug.apk")
PY

echo "काम पूरा। KaamSetu-Fresh-Output फोल्डर खोलें।"




