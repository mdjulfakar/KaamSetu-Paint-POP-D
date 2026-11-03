package com.kaamsetu.app

import android.app.Activity
import android.net.Uri
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.concurrent.TimeUnit

/** Firebase Phone Auth + cloud persistence used by the application. */
class KaamSetuFirebase(private val activity: Activity) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    var verificationId: String? = null
        private set

    fun sendOtp(phone10: String, onCodeSent: () -> Unit, onError: (String) -> Unit) {
        val phone = "+91${phone10.filter(Char::isDigit)}"
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone).setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    auth.signInWithCredential(credential).addOnFailureListener { onError(it.localizedMessage ?: "OTP verification failed") }
                }
                override fun onVerificationFailed(e: FirebaseException) { onError(e.localizedMessage ?: "OTP could not be sent") }
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = id
                    onCodeSent()
                }
            }).build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp(code: String, onSuccess: (FirebaseUser) -> Unit, onError: (String) -> Unit) {
        val id = verificationId ?: return onError("पहले OTP भेजें / Send OTP first")
        auth.signInWithCredential(PhoneAuthProvider.getCredential(id, code))
            .addOnSuccessListener { result -> result.user?.let(onSuccess) ?: onError("Login failed") }
            .addOnFailureListener { onError(it.localizedMessage ?: "Invalid OTP") }
    }

    fun saveRecord(record: KaamRecord, photoUri: Uri?, onSuccess: (KaamRecord) -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser ?: return onError("Login required")
        fun write(photoUrl: String) {
            val data = hashMapOf<String, Any>(
                "id" to record.id, "type" to record.type, "name" to record.name,
                "phone" to record.phone, "district" to record.district, "area" to record.area,
                "work" to record.work, "details" to record.details, "photo" to photoUrl,
                "status" to record.status, "assignedWorkerPhone" to record.assignedWorkerPhone,
                "assignedWorkerPhoneE164" to if (record.assignedWorkerPhone.length == 10) "+91${record.assignedWorkerPhone}" else "",
                "ownerUid" to user.uid, "createdAt" to System.currentTimeMillis()
            )
            db.collection("records").document(record.id).set(data)
                .addOnSuccessListener { onSuccess(record.copy(photo = photoUrl)) }
                .addOnFailureListener { onError(it.localizedMessage ?: "Cloud save failed") }
        }
        if (photoUri == null) write("") else {
            val ref = storage.reference.child("record_photos/${user.uid}/${record.id}.jpg")
            ref.putFile(photoUri).continueWithTask { task ->
                if (!task.isSuccessful) throw (task.exception ?: Exception("Photo upload failed"))
                ref.downloadUrl
            }.addOnSuccessListener { write(it.toString()) }
                .addOnFailureListener { onError(it.localizedMessage ?: "Photo upload failed") }
        }
    }

    fun observeRecords(onRecords: (List<KaamRecord>) -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser ?: return onError("Login required")
        user.getIdToken(false).addOnSuccessListener { token ->
            if (token.claims["admin"] == true) {
                db.collection("records").addSnapshotListener { snapshot, error ->
                    if (error != null) { onError(error.localizedMessage ?: "Cloud sync failed"); return@addSnapshotListener }
                    onRecords(snapshot?.documents.orEmpty().mapNotNull { it.toKaamRecord() })
                }
            } else {
                val ownerRecords = mutableMapOf<String, KaamRecord>()
                val assignedRecords = mutableMapOf<String, KaamRecord>()
                fun publish() = onRecords((ownerRecords.values + assignedRecords.values).associateBy { it.id }.values.toList())
                db.collection("records").whereEqualTo("ownerUid", user.uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) { onError(error.localizedMessage ?: "Cloud sync failed"); return@addSnapshotListener }
                        ownerRecords.clear(); snapshot?.documents.orEmpty().mapNotNull { it.toKaamRecord() }.forEach { ownerRecords[it.id] = it }; publish()
                    }
                val phoneE164 = user.phoneNumber
                if (!phoneE164.isNullOrBlank()) {
                    db.collection("records").whereEqualTo("assignedWorkerPhoneE164", phoneE164)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) { onError(error.localizedMessage ?: "Assigned jobs sync failed"); return@addSnapshotListener }
                            assignedRecords.clear(); snapshot?.documents.orEmpty().mapNotNull { it.toKaamRecord() }.forEach { assignedRecords[it.id] = it }; publish()
                        }
                }
            }
        }.addOnFailureListener { onError(it.localizedMessage ?: "Could not check account role") }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toKaamRecord(): KaamRecord? = try {
        KaamRecord(
            getString("id") ?: id, getString("type") ?: "", getString("name") ?: "",
            getString("phone") ?: "", getString("district") ?: "", getString("area") ?: "",
            getString("work") ?: "", getString("details") ?: "", getString("photo") ?: "",
            getString("status") ?: "नई", getString("assignedWorkerPhone") ?: ""
        )
    } catch (_: Exception) { null }

    fun updateRecord(record: KaamRecord, onError: (String) -> Unit = {}) {
        if (auth.currentUser == null) return onError("Login required")
        db.collection("records").document(record.id).update(
            mapOf("status" to record.status, "assignedWorkerPhone" to record.assignedWorkerPhone,
                 "assignedWorkerPhoneE164" to if (record.assignedWorkerPhone.length == 10) "+91${record.assignedWorkerPhone}" else "")
        ).addOnFailureListener { onError(it.localizedMessage ?: "Update failed") }
    }
}
