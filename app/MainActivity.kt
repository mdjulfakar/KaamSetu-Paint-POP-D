
package com.kaamsetu.app

import android.os.Bundle
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

private val auth = FirebaseAuth.getInstance()
private val db = FirebaseFirestore.getInstance()

data class Booking(
    val ownerUid: String = "",
    val customerName: String = "",
    val phone: String = "",
    val district: String = "",
    val address: String = "",
    val category: String = "",
    val details: String = "",
    val status: String = "New",
    val assignedWorkerUid: String = "",
    val assignedWorkerEmail: String = ""
)

@Composable
fun KaamSetuApp() {
    var hindi by remember { mutableStateOf(true) }

    fun tr(en: String, hi: String) = if (hindi) hi else en

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    var selectedRole by remember { mutableStateOf("customer") }
    var registering by remember { mutableStateOf(false) }

    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var userRole by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf("home") }

    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    var district by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Painting") }
    var details by remember { mutableStateOf("") }
    var workerEmail by remember { mutableStateOf("") }

    var bookings by remember {
        mutableStateOf(listOf<Pair<String, Booking>>())
    }

    val districts = listOf(
        "Srinagar", "Jammu", "Anantnag", "Baramulla",
        "Budgam", "Kupwara", "Pulwama", "Shopian",
        "Ganderbal", "Bandipora", "Kulgam", "Doda",
        "Kishtwar", "Ramban", "Kathua", "Samba",
        "Udhampur", "Rajouri", "Poonch", "Reasi"
    )

    fun loadBookings(uid: String, role: String) {
        val query = when (role) {
            "customer" -> db.collection("bookings")
                .whereEqualTo("ownerUid", uid)
            "worker" -> db.collection("bookings")
                .whereEqualTo("assignedWorkerUid", uid)
            else -> db.collection("bookings")
        }

        query.get()
            .addOnSuccessListener { result ->
                bookings = result.documents.mapNotNull { doc ->
                    doc.toObject(Booking::class.java)
                        ?.let { Pair(doc.id, it) }
                }
            }
            .addOnFailureListener {
                message = "Bookings error: ${it.message}"
            }
    }

    fun openAccount(user: FirebaseUser) {
        loading = true

        user.getIdToken(true)
            .addOnSuccessListener { token ->
                if (token.claims["admin"] == true) {
                    currentUser = user
                    userRole = "admin"
                    screen = "home"
                    message = tr("Admin login successful", "एडमिन लॉगिन सफल")
                    loadBookings(user.uid, "admin")
                    loading = false
                } else {
                    db.collection("users").document(user.uid).get()
                        .addOnSuccessListener { doc ->
                            val role = doc.getString("role") ?: ""

                            if (role == "customer" || role == "worker") {
                                currentUser = user
                                userRole = role
                                name = doc.getString("name") ?: ""
                                phone = doc.getString("phone") ?: ""
                                screen = "home"
                                message = tr("Login successful", "लॉगिन सफल")
                                loadBookings(user.uid, role)
                            } else {
                                auth.signOut()
                                currentUser = null
                                userRole = ""
                                message = tr(
                                    "Account role not found",
                                    "अकाउंट की भूमिका नहीं मिली"
                                )
                            }
                            loading = false
                        }
                        .addOnFailureListener {
                            message = it.message ?: "Profile error"
                            loading = false
                        }
                }
            }
            .addOnFailureListener {
                message = it.message ?: "Permission check failed"
                loading = false
            }
    }

    fun login() {
        if (email.isBlank() || password.isBlank()) {
            message = tr("Enter email and password", "Email और Password भरें")
            return
        }

        loading = true
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) openAccount(user)
                else {
                    loading = false
                    message = "User not found"
                }
            }
            .addOnFailureListener {
                message = it.message ?: "Login failed"
                loading = false
            }
    }

    fun register() {
        if (name.isBlank() || phone.length != 10 ||
            email.isBlank() || password.length < 6
        ) {
            message = tr(
                "Enter name, 10-digit mobile, email and password (minimum 6 characters)",
                "नाम, 10 अंकों का मोबाइल, Email और कम से कम 6 अक्षर का Password भरें"
            )
            return
        }

        loading = true
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    loading = false
                    message = "Registration failed"
                    return@addOnSuccessListener
                }

                val profile = hashMapOf(
                    "name" to name.trim(),
                    "phone" to phone.trim(),
                    "email" to email.trim(),
                    "role" to selectedRole
                )

                db.collection("users").document(user.uid)
                    .set(profile)
                    .addOnSuccessListener {
                        currentUser = user
                        userRole = selectedRole
                        screen = "home"
                        message = tr(
                            "Registration successful",
                            "रजिस्ट्रेशन सफल हुआ"
                        )
                        loading = false
                        loadBookings(user.uid, selectedRole)
                    }
                    .addOnFailureListener {
                        message = it.message ?: "Profile save failed"
                        loading = false
                    }
            }
            .addOnFailureListener {
                message = it.message ?: "Registration failed"
                loading = false
            }
    }

    fun saveBooking() {
        val user = currentUser ?: return

        if (name.isBlank() || phone.length != 10 ||
            district.isBlank() || address.isBlank() ||
            details.isBlank()
        ) {
            message = tr(
                "Fill all booking details",
                "बुकिंग की सभी जानकारी भरें"
            )
            return
        }

        loading = true
        val booking = Booking(
            ownerUid = user.uid,
            customerName = name.trim(),
            phone = phone.trim(),
            district = district.trim(),
            address = address.trim(),
            category = category,
            details = details.trim(),
            status = "New"
        )

        db.collection("bookings").add(booking)
            .addOnSuccessListener {
                message = tr("Booking saved successfully", "बुकिंग सफलतापूर्वक सेव हुई")
                district = ""
                address = ""
                details = ""
                screen = "myBookings"
                loading = false
                loadBookings(user.uid, "customer")
            }
            .addOnFailureListener {
                message = it.message ?: "Booking save failed"
                loading = false
            }
    }

    fun updateStatus(id: String, status: String) {
        db.collection("bookings").document(id)
            .update("status", status)
            .addOnSuccessListener {
                message = tr("Status updated", "काम का स्टेटस अपडेट हुआ")
                currentUser?.let { loadBookings(it.uid, userRole) }
            }
            .addOnFailureListener {
                message = it.message ?: "Update failed"
            }
    }

    fun assignWorker(id: String) {
        if (workerEmail.isBlank()) {
            message = tr("Enter painter email", "पेंटर का Email भरें")
            return
        }

        loading = true
        db.collection("users")
            .whereEqualTo("email", workerEmail.trim())
            .whereEqualTo("role", "worker")
            .get()
            .addOnSuccessListener { result ->
                val painter = result.documents.firstOrNull()

                if (painter == null) {
                    message = tr("Painter not found", "पेंटर नहीं मिला")
                    loading = false
                } else {
                    db.collection("bookings").document(id)
                        .update(
                            mapOf(
                                "assignedWorkerUid" to painter.id,
                                "assignedWorkerEmail" to
                                    (painter.getString("email") ?: ""),
                                "status" to "Assigned"
                            )
                        )
                        .addOnSuccessListener {
                            message = tr(
                                "Work assigned successfully",
                                "पेंटर को काम दे दिया गया"
                            )
                            workerEmail = ""
                            loading = false
                            currentUser?.let {
                                loadBookings(it.uid, "admin")
                            }
                        }
                        .addOnFailureListener {
                            message = it.message ?: "Assignment failed"
                            loading = false
                        }
                }
            }
            .addOnFailureListener {
                message = it.message ?: "Painter search failed"
                loading = false
            }
    }

    fun logout() {
        auth.signOut()
        currentUser = null
        userRole = ""
        bookings = emptyList()
        screen = "home"
        registering = false
        email = ""
        password = ""
        message = tr("Logged out", "लॉगआउट हो गया")
    }

    LaunchedEffect(Unit) {
        auth.currentUser?.let { openAccount(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "KaamSetu – Paint & P.O.P",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            tr(
                "Jammu & Kashmir Painting and P.O.P Services",
                "जम्मू और कश्मीर पेंटिंग और P.O.P सेवाएं"
            )
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { hindi = true }) {
                Text("हिंदी")
            }
            OutlinedButton(onClick = { hindi = false }) {
                Text("English")
            }
        }

        HorizontalDivider()

        if (currentUser == null) {
            Text(
                tr("Welcome to KaamSetu", "KaamSetu में आपका स्वागत है"),
                style = MaterialTheme.typography.titleLarge
            )

            if (registering) {
                Text(tr("Create account", "अकाउंट बनाएं"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { selectedRole = "customer" },
                        enabled = selectedRole != "customer"
                    ) { Text(tr("Customer", "ग्राहक")) }

                    Button(
                        onClick = { selectedRole = "worker" },
                        enabled = selectedRole != "worker"
                    ) { Text(tr("Painter", "पेंटर")) }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("Full name", "पूरा नाम")) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        if (it.length <= 10 && it.all(Char::isDigit)) phone = it
                    },
                    label = { Text(tr("10-digit mobile number", "10 अंकों का मोबाइल नंबर")) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(tr("Login", "लॉगिन"), style = MaterialTheme.typography.titleLarge)
                Text(
                    tr(
                        "Customer and Painter use their registered email and password. Admin uses an authorized admin account.",
                        "ग्राहक और पेंटर अपने रजिस्टर्ड Email और Password से लॉगिन करें। एडमिन अधिकृत अकाउंट से लॉगिन करेगा।"
                    )
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(tr("Password", "पासवर्ड")) },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { if (registering) register() else login() },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (loading) tr("Please wait...", "कृपया इंतजार करें...")
                    else if (registering) tr("Register", "रजिस्टर करें")
                    else tr("Login", "लॉगिन करें")
                )
            }

            TextButton(onClick = {
                registering = !registering
                message = ""
            }) {
                Text(
                    if (registering)
                        tr("Already registered? Login", "पहले से अकाउंट है? लॉगिन करें")
                    else
                        tr("New Customer / Painter? Register", "नए ग्राहक / पेंटर? रजिस्टर करें")
                )
            }
        } else {
            Text(
                when (userRole) {
                    "customer" -> tr("CUSTOMER PANEL", "ग्राहक पैनल")
                    "worker" -> tr("PAINTER PANEL", "पेंटर पैनल")
                    else -> tr("ADMIN PANEL", "एडमिन पैनल")
                },
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                tr("Welcome", "स्वागत है") + " $name"
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    screen = "home"
                    currentUser?.let { loadBookings(it.uid, userRole) }
                }) {
                    Text(tr("Home", "होम"))
                }

                OutlinedButton(onClick = { logout() }) {
                    Text(tr("Logout", "लॉगआउट"))
                }
            }

            if (userRole == "customer") {
                Button(
                    onClick = { screen = "newBooking" },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("New Booking", "नई बुकिंग"))
                }

                Button(
                    onClick = {
                        screen = "myBookings"
                        currentUser?.let { loadBookings(it.uid, "customer") }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("My Bookings", "मेरी बुकिंग"))
                }
            }

            if (userRole == "worker") {
                Button(
                    onClick = {
                        screen = "myBookings"
                        currentUser?.let { loadBookings(it.uid, "worker") }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("My Assigned Jobs", "मुझे दिए गए काम"))
                }
            }

            if (userRole == "admin") {
                Button(
                    onClick = {
                        screen = "adminBookings"
                        currentUser?.let { loadBookings(it.uid, "admin") }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("All Customer Bookings", "सभी ग्राहकों की बुकिंग"))
                }
            }

            if (screen == "newBooking" && userRole == "customer") {
                HorizontalDivider()
                Text(
                    tr("Customer Booking Form", "ग्राहक बुकिंग फॉर्म"),
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("Customer Name", "ग्राहक का नाम")) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        if (it.length <= 10 && it.all(Char::isDigit)) phone = it
                    },
                    label = { Text(tr("Mobile Number", "मोबाइल नंबर")) },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(tr("Select District", "जिला चुनें"))
                districts.forEach { d ->
                    Row {
                        RadioButton(
                            selected = district == d,
                            onClick = { district = d }
                        )
                        Text(d, modifier = Modifier.padding(top = 12.dp))
                    }
                }

                Text(tr("Work Category", "काम का प्रकार"))
                listOf("Painting", "P.O.P", "Putty", "Polish").forEach { item ->
                    Row {
                        RadioButton(
                            selected = category == item,
                            onClick = { category = item }
                        )
                        Text(item, modifier = Modifier.padding(top = 12.dp))
                    }
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(tr("Complete Work Address", "काम का पूरा पता")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text(tr("Work Details", "काम की जानकारी")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Button(
                    onClick = { saveBooking() },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("Submit Booking", "बुकिंग जमा करें"))
                }
            }

            if (screen == "myBookings" || screen == "adminBookings") {
                HorizontalDivider()
                Text(
                    tr("Booking Details", "बुकिंग की जानकारी"),
                    style = MaterialTheme.typography.titleLarge
                )

                if (bookings.isEmpty()) {
                    Text(tr("No bookings found", "अभी कोई बुकिंग नहीं मिली"))
                }

                bookings.forEach { (id, booking) ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "${tr("Customer", "ग्राहक")}: ${booking.customerName}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text("${tr("Phone", "मोबाइल")}: ${booking.phone}")
                            Text("${tr("District", "जिला")}: ${booking.district}")
                            Text("${tr("Address", "पता")}: ${booking.address}")
                            Text("${tr("Category", "काम")}: ${booking.category}")
                            Text("${tr("Details", "विवरण")}: ${booking.details}")
                            Text("${tr("Status", "स्थिति")}: ${booking.status}")

                            if (booking.assignedWorkerEmail.isNotBlank()) {
                                Text(
                                    "${tr("Painter", "पेंटर")}: ${booking.assignedWorkerEmail}"
                                )
                            }

                            if (userRole == "admin") {
                                OutlinedTextField(
                                    value = workerEmail,
                                    onValueChange = { workerEmail = it },
                                    label = { Text(tr("Painter Email", "पेंटर का Email")) },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = { assignWorker(id) },
                                    enabled = !loading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(tr("Assign Painter", "पेंटर को काम दें"))
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = {
                                        updateStatus(id, "In Progress")
                                    }) {
                                        Text(tr("In Progress", "काम जारी"))
                                    }
                                    OutlinedButton(onClick = {
                                        updateStatus(id, "Completed")
                                    }) {
                                        Text(tr("Completed", "पूरा हुआ"))
                                    }
                                }
                            }

                            if (userRole == "worker") {
                                if (booking.status == "Assigned") {
                                    Button(
                                        onClick = {
                                            updateStatus(id, "Accepted")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(tr("Accept Job", "काम स्वीकार करें"))
                                    }
                                }

                                if (booking.status == "Accepted" ||
                                    booking.status == "In Progress"
                                ) {
                                    Button(
                                        onClick = {
                                            updateStatus(id, "In Progress")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(tr("Start Work", "काम शुरू करें"))
                                    }

                                    Button(
                                        onClick = {
                                            updateStatus(id, "Completed")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(tr("Complete Work", "काम पूरा करें"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (message.isNotBlank()) {
            HorizontalDivider()
            Text(message, color = MaterialTheme.colorScheme.primary)
        }

        if (loading) {
            CircularProgressIndicator()
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "KaamSetu – Paint & P.O.P",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
