package com.kaamsetu.app

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

/**
 * KaamSetu Firebase Services
 *
 * Email and Password Authentication
 * Firestore Booking Management
 *
 * OTP and Photo Upload are not used.
 */
class KaamSetuFirebase(private val activity: Activity) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Current logged-in user
    fun currentUser(): FirebaseUser? {
        return auth.currentUser
    }

    // Register with Email and Password
    fun registerWithEmail(
        email: String,
        password: String,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        if (email.isBlank() || password.length < 6) {
            onError("Valid Email और कम से कम 6 अक्षर का Password भरें")
            return
        }

        auth.createUserWithEmailAndPassword(
            email.trim(),
            password
        ).addOnSuccessListener { result ->

            val user = result.user

            if (user != null) {
                onSuccess(user)
            } else {
                onError("Account नहीं बन पाया")
            }

        }.addOnFailureListener { error ->
            onError(error.localizedMessage ?: "Registration Failed")
        }
    }

    // Login with Email and Password
    fun loginWithEmail(
        email: String,
        password: String,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        if (email.isBlank() || password.isBlank()) {
            onError("Email और Password भरें")
            return
        }

        auth.signInWithEmailAndPassword(
            email.trim(),
            password
        ).addOnSuccessListener { result ->

            val user = result.user

            if (user != null) {
                onSuccess(user)
            } else {
                onError("Login Failed")
            }

        }.addOnFailureListener { error ->
            onError(error.localizedMessage ?: "Login Failed")
        }
    }

    // Save user profile
    fun saveUserProfile(
        uid: String,
        name: String,
        phone: String,
        email: String,
        role: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val profile = hashMapOf(
            "name" to name.trim(),
            "phone" to phone.trim(),
            "email" to email.trim(),
            "role" to role
        )

        db.collection("users")
            .document(uid)
            .set(profile)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Profile Save Failed")
            }
    }

    // Save customer booking
    fun saveBooking(
        booking: Booking,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = auth.currentUser

        if (user == null) {
            onError("पहले Login करें")
            return
        }

        db.collection("bookings")
            .add(booking)
            .addOnSuccessListener { document ->
                onSuccess(document.id)
            }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Booking Save Failed")
            }
    }

    // Load bookings for customer, worker or admin
    fun loadBookings(
        uid: String,
        role: String,
        onSuccess: (List<Pair<String, Booking>>) -> Unit,
        onError: (String) -> Unit
    ) {
        val query = when (role) {
            "customer" -> db.collection("bookings")
                .whereEqualTo("ownerUid", uid)

            "worker" -> db.collection("bookings")
                .whereEqualTo("assignedWorkerUid", uid)

            "admin" -> db.collection("bookings")

            else -> {
                onError("Invalid account role")
                return
            }
        }

        query.get()
            .addOnSuccessListener { result ->

                val bookingList = result.documents.mapNotNull { document ->

                    val booking = document.toObject(Booking::class.java)

                    if (booking != null) {
                        Pair(document.id, booking)
                    } else {
                        null
                    }
                }

                onSuccess(bookingList)
            }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Bookings Load Failed")
            }
    }

    // Update booking status
    fun updateBookingStatus(
        bookingId: String,
        status: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (auth.currentUser == null) {
            onError("पहले Login करें")
            return
        }

        db.collection("bookings")
            .document(bookingId)
            .update("status", status)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Status Update Failed")
            }
    }

    // Assign painter to booking
    fun assignPainter(
        bookingId: String,
        painterUid: String,
        painterEmail: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val updates = mapOf(
            "assignedWorkerUid" to painterUid,
            "assignedWorkerEmail" to painterEmail,
            "status" to "Assigned"
        )

        db.collection("bookings")
            .document(bookingId)
            .update(updates)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Painter Assignment Failed")
            }
    }

    // Logout
    fun logout() {
        auth.signOut()
    }
}
         

