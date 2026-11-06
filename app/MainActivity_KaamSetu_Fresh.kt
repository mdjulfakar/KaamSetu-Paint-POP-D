package com.kaamsetu.app
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                KaamSetuApp()
            }
        }
    }
}

private val auth: FirebaseAuth by lazy {
    FirebaseAuth.getInstance()
}

private val db: FirebaseFirestore by lazy {
    FirebaseFirestore.getInstance()
}

private val districts = listOf(
    "Srinagar", "Jammu", "Anantnag", "Baramulla", "Budgam",
    "Kupwara", "Pulwama", "Shopian", "Ganderbal", "Bandipora",
    "Kulgam", "Doda", "Kishtwar", "Ramban", "Kathua",
    "Samba", "Udhampur", "Rajouri", "Poonch", "Reasi"
)

private data class AppBooking(
    val ownerUid: String = "",
    val customerName: String = "",
    val phone: String = "",
    val district: String = "",
    val address: String = "",
    val category: String = "",
    val details: String = "",
    val status: String = "New",
    val assignedWorkerUid: String = "",
    val assignedWorkerEmail: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

private data class BookingRow(
    val id: String,
    val booking: AppBooking
)

@Composable
fun KaamSetuApp() {

    var hindi by remember { mutableStateOf(true) }

    fun tr(en: String, hi: String): String {
        return if (hindi) hi else en
    }

    // Login and registration details
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var district by remember { mutableStateOf(districts.first()) }
    var address by remember { mutableStateOf("") }

    // Painter profile details
    var painterSkills by remember { mutableStateOf("") }
    var painterExperience by remember { mutableStateOf("") }

    // Customer booking details
    var category by remember { mutableStateOf("Painting") }
    var details by remember { mutableStateOf("") }

    // Admin assignment
    var workerEmail by remember { mutableStateOf("") }

    var selectedRole by remember {
        mutableStateOf("customer")
    }

    var registering by remember {
        mutableStateOf(false)
    }

    var currentUser by remember {
        mutableStateOf(auth.currentUser)
    }

    var userRole by remember {
        mutableStateOf("")
    }

    var userName by remember {
        mutableStateOf("")
    }

    var screen by remember {
        mutableStateOf("auth")
    }

    var message by remember {
        mutableStateOf("")
    }

    var loading by remember {
        mutableStateOf(false)
    }

    // Every network action gets a 30-second watchdog. If Firebase/network
    // never responds, the UI is released instead of leaving buttons disabled.
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var operationId by remember { mutableStateOf(0L) }

    fun beginOperation(timeoutMessage: String): Long {
        operationId += 1L
        val id = operationId
        loading = true
        message = tr("Please wait...", "कृपया इंतज़ार करें...")
        mainHandler.postDelayed({
            if (operationId == id && loading) {
                loading = false
                operationId += 1L
                message = timeoutMessage
            }
        }, 30_000L)
        return id
    }

    fun isActiveOperation(id: Long): Boolean = operationId == id && loading

    fun finishOperation(id: Long) {
        if (operationId == id) {
            loading = false
            operationId += 1L
        }
    }

    var bookings by remember {
        mutableStateOf(listOf<BookingRow>())
    }

    // Load bookings for customer, painter or admin
    fun loadBookings(user: FirebaseUser, role: String) {

        val query = when (role) {
            "customer" -> db.collection("bookings")
                .whereEqualTo("ownerUid", user.uid)

            "worker" -> db.collection("bookings")
                .whereEqualTo("assignedWorkerUid", user.uid)

            else -> db.collection("bookings")
        }

        query.get()
            .addOnSuccessListener { result ->

                bookings = result.documents.mapNotNull { doc ->
                    doc.toObject(AppBooking::class.java)
                        ?.let { BookingRow(doc.id, it) }
                }

            }
            .addOnFailureListener { e ->
                message = "${tr("Could not load bookings", "बुकिंग लोड नहीं हुई")}: ${e.message}"
            }
    }

    // Finish login only after token and Firestore profile checks complete.
    fun enterAccount(user: FirebaseUser, id: Long, expectedRole: String) {
        user.getIdToken(true)
            .addOnSuccessListener { token ->
                if (!isActiveOperation(id)) return@addOnSuccessListener

                val hasAdminClaim = token.claims["admin"] == true
                db.collection("users").document(user.uid).get()
                    .addOnSuccessListener { doc ->
                        if (!isActiveOperation(id)) return@addOnSuccessListener

                        val savedRole = doc.getString("role") ?: ""
                        val actualRole = if (hasAdminClaim) "admin" else savedRole
                        if (actualRole !in listOf("customer", "worker", "admin") ||
                            (expectedRole != "admin" && actualRole != expectedRole) ||
                            (expectedRole == "admin" && actualRole != "admin")) {
                            auth.signOut()
                            currentUser = null
                            finishOperation(id)
                            message = if (expectedRole == "admin") {
                                tr("This account does not have Admin permission.", "इस अकाउंट को एडमिन की अनुमति नहीं मिली है।")
                            } else {
                                tr("This account belongs to a different login type or has no profile.", "यह अकाउंट दूसरे लॉगिन प्रकार का है या इसकी प्रोफाइल नहीं मिली।")
                            }
                            return@addOnSuccessListener
                        }

                        currentUser = user
                        userRole = actualRole
                        userName = doc.getString("name") ?: user.email.orEmpty()
                        fullName = userName
                        phone = doc.getString("phone") ?: ""
                        district = doc.getString("district") ?: districts.first()
                        address = doc.getString("address") ?: ""
                        painterSkills = doc.getString("skills") ?: ""
                        painterExperience = doc.getString("experience") ?: ""
                        screen = "dashboard"
                        message = if (actualRole == "admin") tr("Admin login successful", "एडमिन लॉगिन सफल") else tr("Login successful", "लॉगिन सफल")
                        finishOperation(id)
                        loadBookings(user, actualRole)
                    }
                    .addOnFailureListener { e ->
                        if (!isActiveOperation(id)) return@addOnFailureListener
                        auth.signOut()
                        currentUser = null
                        finishOperation(id)
                        message = "${tr("Profile could not be loaded", "प्रोफाइल लोड नहीं हुई")}: ${e.message ?: "Unknown error"}"
                    }
            }
            .addOnFailureListener { e ->
                if (!isActiveOperation(id)) return@addOnFailureListener
                auth.signOut()
                currentUser = null
                finishOperation(id)
                message = "${tr("Login verification failed", "लॉगिन सत्यापन विफल")}: ${e.message ?: "Unknown error"}"
            }
    }

    // Email and password login
    fun signIn(role: String) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || password.isBlank()) {
            message = tr("Enter email and password", "ईमेल और पासवर्ड भरें")
            return
        }

        val id = beginOperation(tr("Login timed out. Check internet and try again.", "लॉगिन में समय अधिक लगा। इंटरनेट जाँचकर दोबारा कोशिश करें।"))
        auth.signInWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { result ->
                if (!isActiveOperation(id)) return@addOnSuccessListener
                val user = result.user
                if (user == null) {
                    finishOperation(id)
                    message = tr("Login failed: user account was not returned.", "लॉगिन असफल: अकाउंट की जानकारी नहीं मिली।")
                } else {
                    enterAccount(user, id, role)
                }
            }
            .addOnFailureListener { e ->
                if (!isActiveOperation(id)) return@addOnFailureListener
                finishOperation(id)
                message = "${tr("Login failed", "लॉगिन असफल")}: ${e.message ?: "Unknown error"}"
            }
    }

    // Customer and painter registration
    fun register() {
        val cleanEmail = email.trim().lowercase()
        if (fullName.isBlank() || phone.isBlank() || cleanEmail.isBlank() || password.length < 6 || address.isBlank()) {
            message = tr("Fill name, phone, email, password (at least 6 characters), and address.", "नाम, मोबाइल, ईमेल, कम से कम 6 अक्षर का पासवर्ड और पता भरें।")
            return
        }
        if (selectedRole == "worker" && (painterSkills.isBlank() || painterExperience.isBlank())) {
            message = tr("Enter your work skills and experience.", "अपने काम की जानकारी और अनुभव भरें।")
            return
        }

        val id = beginOperation(tr("Registration timed out. Check internet and try again.", "रजिस्ट्रेशन में समय अधिक लगा। इंटरनेट जाँचकर दोबारा कोशिश करें।"))
        auth.createUserWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { result ->
                if (!isActiveOperation(id)) return@addOnSuccessListener
                val user = result.user
                if (user == null) {
                    finishOperation(id)
                    message = tr("Registration failed: account was not returned.", "रजिस्ट्रेशन असफल: अकाउंट की जानकारी नहीं मिली।")
                    return@addOnSuccessListener
                }

                val profile = hashMapOf(
                    "uid" to user.uid, "name" to fullName.trim(), "phone" to phone.trim(),
                    "email" to cleanEmail, "district" to district, "address" to address.trim(),
                    "role" to selectedRole, "skills" to painterSkills.trim(),
                    "experience" to painterExperience.trim(), "createdAt" to System.currentTimeMillis()
                )
                db.collection("users").document(user.uid).set(profile)
                    .addOnSuccessListener {
                        if (!isActiveOperation(id)) return@addOnSuccessListener
                        auth.signOut()
                        currentUser = null
                        registering = false
                        password = ""
                        finishOperation(id)
                        message = tr("Successfully Submitted! Please log in.", "सफलतापूर्वक जमा हुआ! अब लॉगिन करें।")
                    }
                    .addOnFailureListener { e ->
                        if (!isActiveOperation(id)) return@addOnFailureListener
                        auth.signOut()
                        currentUser = null
                        finishOperation(id)
                        message = "${tr("Profile save failed", "प्रोफाइल सेव नहीं हुई")}: ${e.message ?: "Unknown error"}. ${tr("The email account may already exist; check Firebase Authentication.", "ईमेल अकाउंट बन चुका हो सकता है; Firebase Authentication जाँचें।")}"
                    }
            }
            .addOnFailureListener { e ->
                if (!isActiveOperation(id)) return@addOnFailureListener
                finishOperation(id)
                message = "${tr("Registration failed", "रजिस्ट्रेशन असफल")}: ${e.message ?: "Unknown error"}"
            }
    }

    // Save or update painter profile from painter dashboard
    fun savePainterProfile() {
        val user = currentUser
        if (user == null) { message = tr("Please log in again.", "कृपया दोबारा लॉगिन करें।"); return }
        if (fullName.isBlank() || phone.isBlank() || address.isBlank() || painterSkills.isBlank() || painterExperience.isBlank()) {
            message = tr("Fill all painter profile details.", "पेंटर की सभी जानकारी भरें।"); return
        }
        val id = beginOperation(tr("Profile save timed out. Check internet and try again.", "प्रोफाइल सेव होने में समय अधिक लगा। इंटरनेट जाँचकर दोबारा कोशिश करें।"))
        val updates = hashMapOf<String, Any>(
            "uid" to user.uid, "name" to fullName.trim(), "phone" to phone.trim(),
            "email" to (user.email ?: ""), "district" to district, "address" to address.trim(),
            "role" to "worker", "skills" to painterSkills.trim(), "experience" to painterExperience.trim()
        )
        val profileRef = db.collection("users").document(user.uid)
        profileRef.set(updates, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                if (!isActiveOperation(id)) return@addOnSuccessListener
                userName = fullName.trim()
                finishOperation(id)
                message = tr("Painter profile saved successfully!", "पेंटर की जानकारी सफलतापूर्वक सेव हुई!")
            }
            .addOnFailureListener { e ->
                if (!isActiveOperation(id)) return@addOnFailureListener
                finishOperation(id)
                message = "${tr("Profile save failed", "प्रोफाइल सेव नहीं हुई")}: ${e.message ?: "Unknown error"}"
            }
    }

    // Customer creates a booking
    fun createBooking() {
        val user = currentUser
        if (user == null) { message = tr("Please log in again.", "कृपया दोबारा लॉगिन करें।"); return }
        if (fullName.isBlank() || phone.isBlank() || address.isBlank() || details.isBlank()) {
            message = tr("Enter customer name, phone, complete address, and work details.", "ग्राहक का नाम, मोबाइल, पूरा पता और काम की जानकारी भरें।"); return
        }
        val id = beginOperation(tr("Booking timed out. Check internet before retrying.", "बुकिंग में समय अधिक लगा। दोबारा भेजने से पहले इंटरनेट जाँचें।"))
        val booking = AppBooking(ownerUid=user.uid, customerName=fullName.trim(), phone=phone.trim(), district=district, address=address.trim(), category=category, details=details.trim(), status="New")
        db.collection("bookings").add(booking)
            .addOnSuccessListener {
                if (!isActiveOperation(id)) return@addOnSuccessListener
                details = ""
                finishOperation(id)
                message = tr("Booking submitted successfully!", "बुकिंग सफलतापूर्वक जमा हुई!")
                loadBookings(user, "customer")
            }
            .addOnFailureListener { e ->
                if (!isActiveOperation(id)) return@addOnFailureListener
                finishOperation(id)
                message = "${tr("Booking failed", "बुकिंग नहीं हुई")}: ${e.message ?: "Unknown error"}"
            }
    }

    // Admin assigns a painter to a booking
    fun assignWorker(row: BookingRow) {
        val targetEmail = workerEmail.trim().lowercase()
        if (targetEmail.isBlank()) { message = tr("Enter painter's registered email.", "पेंटर का रजिस्टर्ड ईमेल भरें।"); return }
        val id = beginOperation(tr("Painter assignment timed out. Try again.", "पेंटर असाइन करने में समय अधिक लगा। दोबारा कोशिश करें।"))
        db.collection("users").whereEqualTo("email", targetEmail).whereEqualTo("role", "worker").limit(1).get()
            .addOnSuccessListener { result ->
                if (!isActiveOperation(id)) return@addOnSuccessListener
                val worker = result.documents.firstOrNull()
                if (worker == null) {
                    finishOperation(id)
                    message = tr("Painter account not found.", "पेंटर का अकाउंट नहीं मिला।")
                    return@addOnSuccessListener
                }
                val uid = worker.getString("uid") ?: worker.id
                db.collection("bookings").document(row.id).update(mapOf("assignedWorkerUid" to uid, "assignedWorkerEmail" to targetEmail, "status" to "Assigned"))
                    .addOnSuccessListener {
                        if (!isActiveOperation(id)) return@addOnSuccessListener
                        finishOperation(id)
                        message = tr("Painter assigned successfully.", "पेंटर सफलतापूर्वक असाइन हुआ।")
                        currentUser?.let { loadBookings(it, "admin") }
                    }
                    .addOnFailureListener { e ->
                        if (!isActiveOperation(id)) return@addOnFailureListener
                        finishOperation(id); message = e.message ?: "Assignment failed"
                    }
            }
            .addOnFailureListener { e ->
                if (!isActiveOperation(id)) return@addOnFailureListener
                finishOperation(id); message = e.message ?: "Painter search failed"
            }
    }

    // Painter accepts, rejects or completes assigned work
    fun updateBookingStatus(row: BookingRow, newStatus: String) {
        val user = currentUser
        if (user == null) { message = tr("Please log in again.", "कृपया दोबारा लॉगिन करें।"); return }
        val id = beginOperation(tr("Status update timed out. Try again.", "स्थिति अपडेट होने में समय अधिक लगा। दोबारा कोशिश करें।"))
        db.collection("bookings").document(row.id).update("status", newStatus)
            .addOnSuccessListener {
                if (!isActiveOperation(id)) return@addOnSuccessListener
                finishOperation(id)
                message = tr("Booking status updated.", "बुकिंग की स्थिति अपडेट हुई।")
                loadBookings(user, userRole)
            }
            .addOnFailureListener { e ->
                if (!isActiveOperation(id)) return@addOnFailureListener
                finishOperation(id); message = e.message ?: "Update failed"
            }
    }

    // Main screen
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        // App title and language switch
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                "KaamSetu – Paint & P.O.P",
                style = MaterialTheme.typography.titleLarge
            )

            TextButton(
                onClick = { hindi = !hindi }
            ) {
                Text(if (hindi) "English" else "हिंदी")
            }
        }

        if (message.isNotBlank()) {
            Text(
                message,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (loading) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth()
            )
        }

        // Login and registration screen
        if (screen == "auth") {

            Text(
                tr(
                    "Connect customers with painters across Jammu & Kashmir",
                    "जम्मू-कश्मीर में ग्राहकों और पेंटरों को जोड़ें"
                )
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                FilterChip(
                    selected = selectedRole == "customer",
                    onClick = {
                        selectedRole = "customer"
                        registering = false
                    },
                    label = {
                        Text(tr("Customer", "ग्राहक"))
                    }
                )

                FilterChip(
                    selected = selectedRole == "worker",
                    onClick = {
                        selectedRole = "worker"
                        registering = false
                    },
                    label = {
                        Text(tr("Painter / Worker", "पेंटर / कारीगर"))
                    }
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = {
                    Text(tr("Email", "ईमेल"))
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = {
                    Text(tr("Password", "पासवर्ड"))
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (registering) {

                Text(
                    tr(
                        "Registration Form",
                        "रजिस्ट्रेशन फॉर्म"
                    ),
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = {
                        Text(tr("Full name", "पूरा नाम"))
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = {
                        Text(tr("Mobile number", "मोबाइल नंबर"))
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                DistrictPicker(
                    district,
                    { district = it },
                    hindi
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = {
                        Text(tr("Full address", "पूरा पता"))
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Extra fields for painter registration
                if (selectedRole == "worker") {

                    OutlinedTextField(
                        value = painterSkills,
                        onValueChange = { painterSkills = it },
                        label = {
                            Text(
                                tr(
                                    "Work Skills (Painting, P.O.P, Putty, Polish)",
                                    "काम की जानकारी (पेंटिंग, पी.ओ.पी., पुट्टी, पॉलिश)"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = painterExperience,
                        onValueChange = { painterExperience = it },
                        label = {
                            Text(
                                tr(
                                    "Work Experience (Years)",
                                    "काम का अनुभव (साल)"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = { register() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) {
                    Text(if (loading) tr("Submitting...", "जमा हो रहा है...") else tr("Submit Registration", "रजिस्ट्रेशन जमा करें"))
                }

                TextButton(
                    onClick = { registering = false }
                ) {
                    Text(
                        tr(
                            "Back to Login",
                            "लॉगिन पर वापस जाएँ"
                        )
                    )
                }

            } else {

                Button(
                    onClick = { signIn(selectedRole) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) {
                    Text(if (loading) tr("Please wait...", "कृपया इंतज़ार करें...") else tr("Login", "लॉगिन"))
                }

                OutlinedButton(
                    onClick = {
                        registering = true
                        message = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) {
                    Text(
                        tr(
                            "Create New Account / Register",
                            "नया अकाउंट बनाएँ / रजिस्ट्रेशन"
                        )
                    )
                }
            }

            Divider()

            Text(
                tr("Admin access", "एडमिन लॉगिन"),
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedButton(
                onClick = { signIn("admin") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading
            ) {
                Text(tr("Admin Login", "एडमिन लॉगिन"))
            }

            Text(
                tr(
                    "Admin login requires the Firebase custom claim: admin = true.",
                    "एडमिन लॉगिन के लिए Firebase में admin = true कस्टम क्लेम जरूरी है।"
                ),
                style = MaterialTheme.typography.bodySmall
            )

        } else {

            // Dashboard header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Column(
                    Modifier.weight(1f)
                ) {

                    Text(
                        when (userRole) {
                            "admin" -> tr(
                                "Admin Dashboard",
                                "एडमिन डैशबोर्ड"
                            )

                            "worker" -> tr(
                                "Painter Dashboard",
                                "पेंटर डैशबोर्ड"
                            )

                            else -> tr(
                                "Customer Dashboard",
                                "ग्राहक डैशबोर्ड"
                            )
                        },
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(userName)
                }

                TextButton(
                    onClick = {

                        auth.signOut()

                        currentUser = null
                        userRole = ""
                        bookings = emptyList()
                        screen = "auth"
                        registering = false

                        message = tr(
                            "Logged out",
                            "लॉगआउट हो गया"
                        )
                    }
                ) {
                    Text(tr("Logout", "लॉगआउट"))
                }
            }

            // Customer dashboard
            if (userRole == "customer") {

                Text(
                    tr("New Work Booking", "नया काम बुक करें"),
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = {
                        Text(tr("Customer name", "ग्राहक का नाम"))
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = {
                        Text(tr("Mobile number", "मोबाइल नंबर"))
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                DistrictPicker(
                    district,
                    { district = it },
                    hindi
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = {
                        Text(
                            tr(
                                "Complete work address",
                                "काम का पूरा पता"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    tr("Work category", "काम का प्रकार")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    listOf(
                        "Painting",
                        "P.O.P",
                        "Putty",
                        "Polish"
                    ).forEach { item ->

                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item) }
                        )
                    }
                }

                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = {
                        Text(
                            tr(
                                "Work details",
                                "काम की जानकारी"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Button(
                    onClick = { createBooking() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) {
                    Text(if (loading) tr("Submitting...", "जमा हो रहा है...") else tr("Submit Booking", "बुकिंग जमा करें"))
                }

                Text(
                    tr("My Bookings", "मेरी बुकिंग"),
                    style = MaterialTheme.typography.titleMedium
                )

                BookingList(
                    bookings,
                    hindi,
                    false,
                    ::updateBookingStatus
                )

            // Painter dashboard
            } else if (userRole == "worker") {

                Text(
                    tr(
                        "Painter Profile",
                        "पेंटर की जानकारी"
                    ),
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = {
                        Text(tr("Full Name", "पूरा नाम"))
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = {
                        Text(tr("Mobile Number", "मोबाइल नंबर"))
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                DistrictPicker(
                    district,
                    { district = it },
                    hindi
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = {
                        Text(tr("Full Address", "पूरा पता"))
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = painterSkills,
                    onValueChange = { painterSkills = it },
                    label = {
                        Text(
                            tr(
                                "Work Skills (Painting, P.O.P, Putty, Polish)",
                                "काम की जानकारी (पेंटिंग, पी.ओ.पी., पुट्टी, पॉलिश)"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                OutlinedTextField(
                    value = painterExperience,
                    onValueChange = { painterExperience = it },
                    label = {
                        Text(
                            tr(
                                "Work Experience (Years)",
                                "काम का अनुभव (साल)"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { savePainterProfile() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) {
                    Text(if (loading) tr("Saving...", "सेव हो रहा है...") else tr("Save Painter Profile", "पेंटर की जानकारी सेव करें"))
                }

                Divider()

                Text(
                    tr(
                        "Your assigned jobs",
                        "आपको दिए गए काम"
                    ),
                    style = MaterialTheme.typography.titleMedium
                )

                BookingList(
                    bookings,
                    hindi,
                    true,
                    ::updateBookingStatus
                )

            // Admin dashboard
            } else {

                Text(
                    tr(
                        "All customer bookings",
                        "सभी ग्राहक बुकिंग"
                    ),
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    tr(
                        "Commission rate: 10% per completed job (collected directly)",
                        "कमीशन: हर पूरे काम पर 10% (सीधे वसूलें)"
                    )
                )

                if (bookings.isEmpty()) {
                    Text(
                        tr(
                            "No customer bookings yet.",
                            "अभी कोई ग्राहक बुकिंग नहीं है।"
                        )
                    )
                }

                bookings.forEach { row ->

                    BookingCard(row, hindi)

                    OutlinedTextField(
                        value = workerEmail,
                        onValueChange = { workerEmail = it },
                        label = {
                            Text(
                                tr(
                                    "Painter registered email",
                                    "पेंटर का रजिस्टर्ड ईमेल"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { assignWorker(row) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    ) {
                        Text(
                            tr(
                                "Assign Painter",
                                "पेंटर असाइन करें"
                            )
                        )
                    }

                    Divider()
                }
            }

            OutlinedButton(
                onClick = {
                    currentUser?.let {
                        loadBookings(it, userRole)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(tr("Refresh", "रिफ्रेश"))
            }
        }
    }
}

// District selection
@Composable
private fun DistrictPicker(
    value: String,
    onSelect: (String) -> Unit,
    hindi: Boolean
) {

    var expanded by remember {
        mutableStateOf(false)
    }

    Column {

        Text(
            if (hindi) "जिला" else "District"
        )

        Box {

            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(value)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                }
            ) {

                districts.forEach { item ->

                    DropdownMenuItem(
                        text = { Text(item) },
                        onClick = {
                            onSelect(item)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

// Booking information card
@Composable
private fun BookingCard(
    row: BookingRow,
    hindi: Boolean
) {

    val b = row.booking

    Card(
        Modifier.fillMaxWidth()
    ) {

        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            Text(
                "${if (hindi) "ग्राहक" else "Customer"}: ${b.customerName}"
            )

            Text(
                "${if (hindi) "मोबाइल" else "Phone"}: ${b.phone}"
            )

            Text(
                "${if (hindi) "जिला" else "District"}: ${b.district}"
            )

            Text(
                "${if (hindi) "पता" else "Address"}: ${b.address}"
            )

            Text(
                "${if (hindi) "काम" else "Work"}: ${b.category}"
            )

            Text(
                "${if (hindi) "विवरण" else "Details"}: ${b.details}"
            )

            Text(
                "${if (hindi) "स्थिति" else "Status"}: ${b.status}"
            )

            if (b.assignedWorkerEmail.isNotBlank()) {

                Text(
                    "${if (hindi) "पेंटर" else "Painter"}: ${b.assignedWorkerEmail}"
                )
            }
        }
    }
}

// Booking list and painter actions
@Composable
private fun BookingList(
    rows: List<BookingRow>,
    hindi: Boolean,
    workerActions: Boolean,
    onStatus: (BookingRow, String) -> Unit
) {

    if (rows.isEmpty()) {

        Text(
            if (hindi) {
                "अभी कोई बुकिंग नहीं है।"
            } else {
                "No bookings yet."
            }
        )
    }

    rows.forEach { row ->

        BookingCard(row, hindi)

        if (workerActions) {

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                OutlinedButton(
                    onClick = {
                        onStatus(row, "Accepted")
                    }
                ) {
                    Text(
                        if (hindi) "स्वीकार" else "Accept"
                    )
                }

                OutlinedButton(
                    onClick = {
                        onStatus(row, "Rejected")
                    }
                ) {
                    Text(
                        if (hindi) "रिजेक्ट" else "Reject"
                    )
                }

                Button(
                    onClick = {
                        onStatus(row, "Completed")
                    }
                ) {
                    Text(
                        if (hindi) "पूरा हुआ" else "Complete"
                    )
                }
            }
        }

        Divider()
    }
}
              
                
