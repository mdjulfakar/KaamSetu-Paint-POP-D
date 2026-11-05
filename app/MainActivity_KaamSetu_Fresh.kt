package com.kaamsetu.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
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

    // Enter account after checking role and admin claim
    fun enterAccount(user: FirebaseUser) {

        loading = true

        user.getIdToken(true)
            .addOnSuccessListener { token ->

                val isAdminClaim = token.claims["admin"] == true

                db.collection("users")
                    .document(user.uid)
                    .get()
                    .addOnSuccessListener { doc ->

                        val savedRole = doc.getString("role") ?: ""

                        val role = if (isAdminClaim) {
                            "admin"
                        } else {
                            savedRole
                        }

                        if (role !in listOf("customer", "worker", "admin")) {

                            loading = false
                            auth.signOut()
                            currentUser = null

                            message = tr(
                                "This account has no valid role. Contact the administrator.",
                                "इस अकाउंट की भूमिका सही नहीं है। एडमिन से संपर्क करें।"
                            )

                            return@addOnSuccessListener
                        }

                        currentUser = user
                        userRole = role

                        userName = doc.getString("name")
                            ?: user.email.orEmpty()

                        fullName = userName

                        phone = doc.getString("phone") ?: ""
                        district = doc.getString("district")
                            ?: districts.first()

                        address = doc.getString("address") ?: ""

                        painterSkills = doc.getString("skills") ?: ""

                        painterExperience =
                            doc.getString("experience") ?: ""

                        screen = "dashboard"

                        message = if (role == "admin") {
                            tr(
                                "Admin login successful",
                                "एडमिन लॉगिन सफल"
                            )
                        } else {
                            tr(
                                "Login successful",
                                "लॉगिन सफल"
                            )
                        }

                        loadBookings(user, role)

                        loading = false
                    }
                    .addOnFailureListener { e ->

                        loading = false

                        message = e.message ?: tr(
                            "Profile could not be loaded",
                            "प्रोफाइल लोड नहीं हुई"
                        )
                    }
            }
            .addOnFailureListener { e ->

                loading = false

                message = e.message ?: tr(
                    "Login verification failed",
                    "लॉगिन सत्यापन विफल"
                )
            }
    }

    // Email and password login
    fun signIn(role: String) {

        val cleanEmail = email.trim().lowercase()

        if (cleanEmail.isBlank() || password.isBlank()) {

            message = tr(
                "Enter email and password",
                "ईमेल और पासवर्ड भरें"
            )

            return
        }

        loading = true

        auth.signInWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { result ->

                val user = result.user

                if (user == null) {

                    loading = false
                    message = tr("Login failed", "लॉगिन असफल")

                } else if (role == "admin") {

                    user.getIdToken(true)
                        .addOnSuccessListener { token ->

                            if (token.claims["admin"] == true) {
                                enterAccount(user)
                            } else {
                                auth.signOut()
                                currentUser = null
                                loading = false

                                message = tr(
                                    "This account does not have Admin permission.",
                                    "इस अकाउंट को एडमिन की अनुमति नहीं मिली है।"
                                )
                            }
                        }
                        .addOnFailureListener { e ->
                            loading = false
                            message = e.message ?: "Admin verification failed"
                        }

                } else {

                    db.collection("users")
                        .document(user.uid)
                        .get()
                        .addOnSuccessListener { doc ->

                            val savedRole = doc.getString("role") ?: ""

                            if (savedRole != role) {

                                auth.signOut()
                                currentUser = null
                                loading = false

                                message = tr(
                                    "This account belongs to a different login type.",
                                    "यह अकाउंट दूसरे लॉगिन प्रकार का है।"
                                )

                            } else {
                                enterAccount(user)
                            }
                        }
                        .addOnFailureListener { e ->

                            loading = false
                            message = e.message ?: "Profile error"
                        }
                }
            }
            .addOnFailureListener { e ->

                loading = false

                message = e.message ?: tr(
                    "Login failed",
                    "लॉगिन असफल"
                )
            }
    }

    // Customer and painter registration
    fun register() {

        val cleanEmail = email.trim().lowercase()

        if (
            fullName.isBlank() ||
            phone.isBlank() ||
            cleanEmail.isBlank() ||
            password.length < 6 ||
            address.isBlank()
        ) {

            message = tr(
                "Fill name, phone, email, password (at least 6 characters), and address.",
                "नाम, मोबाइल, ईमेल, कम से कम 6 अक्षर का पासवर्ड और पता भरें।"
            )

            return
        }

        if (selectedRole == "worker" &&
            (painterSkills.isBlank() || painterExperience.isBlank())
        ) {

            message = tr(
                "Enter your work skills and experience.",
                "अपने काम की जानकारी और अनुभव भरें।"
            )

            return
        }

        loading = true

        auth.createUserWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { result ->

                val user = result.user

                if (user == null) {

                    loading = false
                    message = tr(
                        "Registration failed",
                        "रजिस्ट्रेशन असफल"
                    )

                    return@addOnSuccessListener
                }

                val profile = hashMapOf(
                    "uid" to user.uid,
                    "name" to fullName.trim(),
                    "phone" to phone.trim(),
                    "email" to cleanEmail,
                    "district" to district,
                    "address" to address.trim(),
                    "role" to selectedRole,
                    "skills" to painterSkills.trim(),
                    "experience" to painterExperience.trim(),
                    "createdAt" to System.currentTimeMillis()
                )

                db.collection("users")
                    .document(user.uid)
                    .set(profile)
                    .addOnSuccessListener {

                        loading = false

                        message = tr(
                            "Successfully Submitted! Please log in.",
                            "सफलतापूर्वक जमा हुआ! अब लॉगिन करें।"
                        )

                        registering = false
                        password = ""

                        screen = "auth";


                    
                    }
                    .addOnFailureListener { e ->

                        loading = false

                        message = "${tr("Profile save failed", "प्रोफाइल सेव नहीं हुई")}: ${e.message}"
                    }
            }
            .addOnFailureListener { e ->

                loading = false

                message = e.message ?: tr(
                    "Registration failed",
                    "रजिस्ट्रेशन असफल"
                )
            }
    }

    // Save or update painter profile from painter dashboard
    fun savePainterProfile() {

        val user = currentUser

        if (user == null) {
            message = tr("Please log in again.", "कृपया दोबारा लॉगिन करें।")
            return
        }

        if (
            fullName.isBlank() ||
            phone.isBlank() ||
            address.isBlank() ||
            painterSkills.isBlank() ||
            painterExperience.isBlank()
        ) {

            message = tr(
                "Fill all painter profile details.",
                "पेंटर की सभी जानकारी भरें।"
            )

            return
        }

        loading = true

        val updates = hashMapOf<String, Any>(
            "uid" to user.uid,
            "name" to fullName.trim(),
            "phone" to phone.trim(),
            "email" to (user.email ?: ""),
            "district" to district,
            "address" to address.trim(),
            "role" to "worker",
            "skills" to painterSkills.trim(),
            "experience" to painterExperience.trim()
        )

        db.collection("users")
            .document(user.uid)
            .update(updates)
            .addOnSuccessListener {

                loading = false
                userName = fullName.trim()

                message = tr(
                    "Painter profile saved successfully!",
                    "पेंटर की जानकारी सफलतापूर्वक सेव हुई!"
                )
            }
            .addOnFailureListener { e ->

                // If profile document does not exist, create it.
                db.collection("users")
                    .document(user.uid)
                    .set(updates)
                    .addOnSuccessListener {

                        loading = false
                        userName = fullName.trim()

                        message = tr(
                            "Painter profile saved successfully!",
                            "पेंटर की जानकारी सफलतापूर्वक सेव हुई!"
                        )
                    }
                    .addOnFailureListener { secondError ->

                        loading = false

                        message = secondError.message
                            ?: e.message
                            ?: "Profile save failed"
                    }loadBookings(user, "customer");


            }
    }

    // Customer creates a booking without image attachments.
    fun createBooking() {

        val user = currentUser
        if (user == null) {
            message = tr("Please log in again.", "कृपया दोबारा लॉगिन करें।")
            return
        }

        if (
            fullName.isBlank() ||
            phone.isBlank() ||
            address.isBlank() ||
            details.isBlank()
        ) {
            message = tr(
                "Enter customer name, phone, complete address, and work details.",
                "ग्राहक का नाम, मोबाइल, पूरा पता और काम की जानकारी भरें।"
            )
            return
        }

        loading = true
        message = tr("Saving booking...", "बुकिंग सेव हो रही है...")

        val booking = AppBooking(
            ownerUid = user.uid,
            customerName = fullName.trim(),
            phone = phone.trim(),
            district = district,
            address = address.trim(),
            category = category,
            details = details.trim(),
            status = "New"
        )

        db.collection("bookings")
            .add(booking)
            .addOnSuccessListener { document ->
                loading = false
                message = tr(
                    "Booking submitted successfully! Reference: ${document.id}",
                    "बुकिंग सफलतापूर्वक जमा हुई! रेफरेंस: ${document.id}"
                )
                details = ""
                loadBookings(user, "customer")
            }
            .addOnFailureListener { e ->
                loading = false
                message = "${tr("Booking failed", "बुकिंग सेव नहीं हुई")}: ${e.message}"
            }
    }

    // Admin assigns a painter to a booking
    fun assignWorker(row: BookingRow) {

        val targetEmail = workerEmail.trim().lowercase()

        if (targetEmail.isBlank()) {

            message = tr(
                "Enter painter's registered email.",
                "पेंटर का रजिस्टर्ड ईमेल भरें।"
            )

            return
        }

        loading = true

        db.collection("users")
            .whereEqualTo("email", targetEmail)
            .whereEqualTo("role", "worker")
            .limit(1)
            .get()
            .addOnSuccessListener { result ->

                val worker = result.documents.firstOrNull()

                if (worker == null) {

                    loading = false

                    message = tr(
                        "Painter account not found.",
                        "पेंटर का अकाउंट नहीं मिला।"
                    )

                } else {

                    val uid = worker.getString("uid") ?: worker.id

                    db.collection("bookings")
                        .document(row.id)
                        .update(
                            mapOf(
                                "assignedWorkerUid" to uid,
                                "assignedWorkerEmail" to targetEmail,
                                "status" to "Assigned"
                            )
                        )
                        .addOnSuccessListener {

                            loading = false

                            message = tr(
                                "Painter assigned successfully.",
                                "पेंटर सफलतापूर्वक असाइन हुआ।"
                            )

                            currentUser?.let {
                                loadBookings(it, "admin")
                            }
                        }
                        .addOnFailureListener { e ->

                            loading = false
                            message = e.message ?: "Assignment failed"
                        }
                }
            }
            .addOnFailureListener { e ->

                loading = false
                message = e.message ?: "Painter search failed"
            }
    }

    // Painter accepts, rejects or completes assigned work
    fun updateBookingStatus(row: BookingRow, newStatus: String) {

        val user = currentUser ?: return

        loading = true

        val updates: Map<String, Any> = if (newStatus == "Rejected") {
            mapOf(
                "status" to "Reassignment Requested",
                "assignedWorkerUid" to "",
                "assignedWorkerEmail" to ""
            )
        } else {
            mapOf("status" to newStatus)
        }

        db.collection("bookings")
            .document(row.id)
            .update(updates)
            .addOnSuccessListener {

                loading = false

                message = tr(
                    "Booking status updated.",
                    "बुकिंग की स्थिति अपडेट हुई।"
                )

                loadBookings(user, userRole)
            }
            .addOnFailureListener { e ->

                loading = false
                message = e.message ?: "Update failed"
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
                    Text(
                        tr(
                            "Submit Registration",
                            "रजिस्ट्रेशन जमा करें"
                        )
                    )
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
                    Text(tr("Login", "लॉगिन"))
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
                    Text(
                        tr(
                            "Submit Booking",
                            "बुकिंग जमा करें"
                        )
                    )
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
                    Text(
                        tr(
                            "Save Painter Profile",
                            "पेंटर की जानकारी सेव करें"
                        )
                    )
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
              
                


