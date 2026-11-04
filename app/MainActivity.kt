
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
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("customer") }
    var registering by remember { mutableStateOf(false) }

    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var userRole by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    var screen by remember { mutableStateOf("home") }

    var bookings by remember { mutableStateOf(listOf<Pair<String, Booking>>()) }

    var district by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Painting") }
    var details by remember { mutableStateOf("") }

    var workerEmail by remember { mutableStateOf("") }

    fun loadBookings(uid: String, userType: String) {
        val query = when (userType) {
            "customer" -> db.collection("bookings")
                .whereEqualTo("ownerUid", uid)
            "worker" -> db.collection("bookings")
                .whereEqualTo("assignedWorkerUid", uid)
            else -> db.collection("bookings")
        }

        query.get()
            .addOnSuccessListener { result ->
                bookings = result.documents.mapNotNull { doc ->
                    val item = doc.toObject(Booking::class.java)
                    if (item != null) Pair(doc.id, item) else null
                }
            }
            .addOnFailureListener {
                message = "Bookings load failed: ${it.message}"
            }
    }

    fun login() {
        if (email.isBlank() || password.isBlank()) {
            message = "Email और Password भरें"
            return
        }

        loading = true

        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user ?: return@addOnSuccessListener
                currentUser = user

                user.getIdToken(true)
                    .addOnSuccessListener { token ->
                        if (token.claims["admin"] == true) {
                            userRole = "admin"
                            message = "Admin Login Successful"
                            screen = "home"
                            loading = false
                        } else {
                            db.collection("users").document(user.uid).get()
                                .addOnSuccessListener { doc ->
                                    userRole = doc.getString("role") ?: ""
                                    if (userRole == "customer" ||
                                        userRole == "worker"
                                    ) {
                                        message = "Login Successful"
                                        screen = "home"
                                        loadBookings(user.uid, userRole)
                                    } else {
                                        message = "Account role नहीं मिला"
                                        auth.signOut()
                                        currentUser = null
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
            .addOnFailureListener {
                message = it.message ?: "Login Failed"
                loading = false
            }
    }

    fun register() {
        if (name.isBlank() || phone.length != 10 ||
            email.isBlank() || password.length < 6
        ) {
            message = "नाम, 10 अंकों का मोबाइल, Email और कम से कम 6 अक्षर का Password भरें"
            return
        }

        loading = true

        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user ?: return@addOnSuccessListener

                val profile = hashMapOf(
                    "name" to name.trim(),
                    "phone" to phone.trim(),
                    "email" to email.trim(),
                    "role" to role
                )

                db.collection("users").document(user.uid)
                    .set(profile)
                    .addOnSuccessListener {
                        currentUser = user
                        userRole = role
                        message = "Registration Successful"
                        screen = "home"
                        loading = false
                        loadBookings(user.uid, role)
                    }
                    .addOnFailureListener {
                        message = it.message ?: "Profile save failed"
                        loading = false
                    }
            }
            .addOnFailureListener {
                message = it.message ?: "Registration Failed"
                loading = false
            }
    }

    fun saveBooking() {
        val user = currentUser ?: return

        if (name.isBlank() || phone.length != 10 ||
            district.isBlank() || address.isBlank() ||
            details.isBlank()
        ) {
            message = "सभी जानकारी भरना जरूरी है"
            return
        }

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

        db.collection("bookings")
            .add(booking)
            .addOnSuccessListener {
                message = "Booking Successfully Saved"
                district = ""
                address = ""
                details = ""
                screen = "myBookings"
                loadBookings(user.uid, "customer")
            }
            .addOnFailureListener {
                message = it.message ?: "Booking Save Failed"
            }
    }

    fun updateStatus(id: String, status: String) {
        db.collection("bookings").document(id)
            .update("status", status)
            .addOnSuccessListener {
                message = "Status Updated: $status"
                currentUser?.let { loadBookings(it.uid, userRole) }
            }
            .addOnFailureListener {
                message = it.message ?: "Update Failed"
            }
    }

    fun assignWorker(id: String) {
        if (workerEmail.isBlank()) {
            message = "Painter Email भरें"
            return
        }

        db.collection("users")
            .whereEqualTo("email", workerEmail.trim())
            .whereEqualTo("role", "worker")
            .get()
            .addOnSuccessListener { result ->
                val painter = result.documents.firstOrNull()

                if (painter == null) {
                    message = "Painter नहीं मिला"
                } else {
                    db.collection("bookings").document(id)
                        .update(
                            mapOf(
                                "assignedWorkerUid" to painter.id,
                                "assignedWorkerEmail" to workerEmail.trim(),
                                "status" to "Assigned"
                            )
                        )
                        .addOnSuccessListener {
                            message = "Painter को काम दे दिया गया"
                            loadBookings(
                                currentUser?.uid ?: "",
                                "admin"
                            )
                        }
                        .addOnFailureListener {
                            message = it.message ?: "Assignment Failed"
                        }
                }
            }
            .addOnFailureListener {
                message = it.message ?: "Painter Search Failed"
            }
    }

    if (currentUser == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "KaamSetu – Paint & P.O.P",
                style = MaterialTheme.typography.headlineMedium
            )

            Text("Jammu & Kashmir Painting and P.O.P Services")

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { registering = false }) {
                    Text("Login")
                }
                Button(onClick = { registering = true }) {
                    Text("Register")
                }
            }

            if (registering) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter(Char::isDigit).take(10) },
                    label = { Text("10 Digit Mobile Number") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Select Account Type")

                Row {
                    RadioButton(
                        selected = role == "customer",
                        onClick = { role = "customer" }
                    )
                    Text("Customer", modifier = Modifier.padding(top = 12.dp))

                    Spacer(Modifier.width(12.dp))

                    RadioButton(
                        selected = role == "worker",
                        onClick = { role = "worker" }
                    )
                    Text("Painter / POP Worker", modifier = Modifier.padding(top = 12.dp))
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (Minimum 6 Characters)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (registering) register() else login()
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (loading) "Please Wait..."
                    else if (registering) "Create Account" else "Login"
                )
            }

            Text(message)
            Text("Login uses Email and Password. No OTP.")
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "KaamSetu – Paint & P.O.P",
                style = MaterialTheme.typography.headlineMedium
            )

            Text("Account: $userRole")

            Text(message)

            HorizontalDivider()

            if (userRole == "customer") {
                Button(
                    onClick = { screen = "booking" },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Book Painting / P.O.P Work")
                }

                Button(
                    onClick = {
                        loadBookings(currentUser!!.uid, "customer")
                        screen = "myBookings"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("My Bookings")
                }

                if (screen == "booking") {
                    Text("New Work Booking", style = MaterialTheme.typography.titleLarge)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Customer Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.filter(Char::isDigit).take(10) },
                        label = { Text("Mobile Number") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = district,
                        onValueChange = { district = it },
                        label = { Text("District") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Complete Work Address") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Select Work Category")

                    listOf(
                        "Painting",
                        "P.O.P",
                        "Putty",
                        "Polish"
                    ).forEach {
                        Row {
                            RadioButton(
                                selected = category == it,
                                onClick = { category = it }
                            )
                            Text(it, modifier = Modifier.padding(top = 12.dp))
                        }
                    }

                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text("Work Details") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    Button(
                        onClick = { saveBooking() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Booking")
                    }
                }

                if (screen == "myBookings") {
                    Text("My Bookings", style = MaterialTheme.typography.titleLarge)

                    bookings.forEach { (id, item) ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Work: ${item.category}")
                                Text("District: ${item.district}")
                                Text("Address: ${item.address}")
                                Text("Details: ${item.details}")
                                Text("Status: ${item.status}")
                                Text("Painter: ${item.assignedWorkerEmail.ifBlank { "Not Assigned" }}")
                            }
                        }
                    }
                }
            }

            if (userRole == "worker") {
                Button(
                    onClick = {
                        loadBookings(currentUser!!.uid, "worker")
                        screen = "jobs"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("My Assigned Jobs")
                }

                if (screen == "jobs" || screen == "home") {
                    bookings.forEach { (id, item) ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Customer: ${item.customerName}")
                                Text("Phone: ${item.phone}")
                                Text("District: ${item.district}")
                                Text("Address: ${item.address}")
                                Text("Work: ${item.category}")
                                Text("Details: ${item.details}")
                                Text("Status: ${item.status}")

                                if (item.status == "Assigned") {
                                    Button(onClick = { updateStatus(id, "Accepted") }) {
                                        Text("Accept Job")
                                    }
                                    Button(onClick = { updateStatus(id, "Rejected") }) {
                                        Text("Reject Job")
                                    }
                                }

                                if (item.status == "Accepted") {
                                    Button(onClick = { updateStatus(id, "Completed") }) {
                                        Text("Complete Job")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (userRole == "admin") {
                Button(
                    onClick = {
                        loadBookings(currentUser!!.uid, "admin")
                        screen = "admin"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Admin Panel - All Bookings")
                }

                if (screen == "admin" || screen == "home") {
                    Text("Admin Dashboard", style = MaterialTheme.typography.titleLarge)
                    Text("Total Bookings: ${bookings.size}")

                    bookings.forEach { (id, item) ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Customer: ${item.customerName}")
                                Text("Mobile: ${item.phone}")
                                Text("District: ${item.district}")
                                Text("Address: ${item.address}")
                                Text("Work: ${item.category}")
                                Text("Details: ${item.details}")
                                Text("Status: ${item.status}")
                                Text("Painter: ${item.assignedWorkerEmail.ifBlank { "Not Assigned" }}")

                                OutlinedTextField(
                                    value = workerEmail,
                                    onValueChange = { workerEmail = it },
                                    label = { Text("Painter Email") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = { assignWorker(id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Assign Painter")
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            OutlinedButton(
                onClick = {
                    auth.signOut()
                    currentUser = null
                    userRole = ""
                    bookings = emptyList()
                    message = "Logged Out"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Logout")
            }
        }
    }
}

