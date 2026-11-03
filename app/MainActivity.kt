package com.kaamsetu.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS = "kaamsetu_local_data"
private const val RECORDS = "records"

private fun tx(english: Boolean, hindi: String, englishText: String): String = if (english) englishText else hindi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KaamSetuApp(this) }
    }
}

data class KaamRecord(
    val id: String, val type: String, val name: String, val phone: String,
    val district: String, val area: String, val work: String, val details: String,
    val photo: String, val status: String, val assignedWorkerPhone: String = ""
)

private fun loadRecords(context: Context): List<KaamRecord> {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(RECORDS, "[]") ?: "[]"
    val array = JSONArray(raw)
    return (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        KaamRecord(o.optString("id"), o.optString("type"), o.optString("name"), o.optString("phone"),
            o.optString("district"), o.optString("area"), o.optString("work"), o.optString("details"),
            o.optString("photo"), o.optString("status", "नया"), o.optString("assignedWorkerPhone", ""))
    }
}

private fun saveRecords(context: Context, records: List<KaamRecord>) {
    val array = JSONArray()
    records.forEach { r ->
        array.put(JSONObject().apply {
            put("id", r.id); put("type", r.type); put("name", r.name); put("phone", r.phone)
            put("district", r.district); put("area", r.area); put("work", r.work)
            put("details", r.details); put("photo", r.photo); put("status", r.status); put("assignedWorkerPhone", r.assignedWorkerPhone)
        })
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(RECORDS, array.toString()).apply()
}

@Composable
fun KaamSetuApp(activity: ComponentActivity) {
    val context = LocalContext.current
    val firebase = remember { KaamSetuFirebase(activity) }
    var signedInUser by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    var cloudError by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf("home") }
    var selectedPhoto by remember { mutableStateOf<Uri?>(null) }
    var records by remember { mutableStateOf(loadRecords(context)) }
    var english by remember { mutableStateOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("english", false)) }
    LaunchedEffect(signedInUser?.uid) {
        if (signedInUser != null) {
            firebase.observeRecords({ cloudRecords -> records = cloudRecords; saveRecords(context, cloudRecords) }, { cloudError = it })
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            selectedPhoto = uri
        }
    }
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (signedInUser == null) {
                PhoneOtpScreen(activity = activity, english = english, onLoggedIn = { signedInUser = it })
            } else when {
                screen == "booking" -> EntryForm(english, "काम बुक करें", "Book a Job", "अपनी जरूरत बताएँ। आपकी बुकिंग Firebase में सेव होगी।", "Tell us what you need. Your booking is saved to Firebase.", "बुकिंग सेव करें", "Save Booking", "ग्राहक", selectedPhoto,
                    onPickPhoto = { photoPicker.launch(arrayOf("image/*")) }, onSave = { r -> firebase.saveRecord(r, selectedPhoto, { saved -> records = records + saved; saveRecords(context, records); Toast.makeText(context, tx(english, "Firebase में सेव हो गया", "Saved to Firebase"), Toast.LENGTH_LONG).show() }, { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }) }, onBack = { screen = "home"; selectedPhoto = null })
                screen == "artisan" -> EntryForm(english, "कारीगर रजिस्ट्रेशन", "Worker Registration", "अपना हुनर और काम का इलाका दर्ज करें।", "Enter your skills and service area.", "रजिस्ट्रेशन सेव करें", "Save Registration", "कारीगर", selectedPhoto,
                    onPickPhoto = { photoPicker.launch(arrayOf("image/*")) }, onSave = { r -> firebase.saveRecord(r, selectedPhoto, { saved -> records = records + saved; saveRecords(context, records); Toast.makeText(context, tx(english, "Firebase में सेव हो गया", "Saved to Firebase"), Toast.LENGTH_LONG).show() }, { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }) }, onBack = { screen = "home"; selectedPhoto = null })
                screen == "adminLogin" -> AdminLoginScreen(english, onBack = { screen = "home" }, onLogin = { screen = "adminDashboard" })
                screen == "adminDashboard" -> AdminDashboardScreen(english, records = records, onChange = { updated -> records = updated; saveRecords(context, updated); updated.forEach { firebase.updateRecord(it) } }, onBack = { screen = "home" })
                screen == "workerLogin" -> WorkerLoginScreen(english, records, onBack = { screen = "home" }, onJobs = { phone -> screen = "workerJobs:$phone" })
                screen.startsWith("workerJobs:") -> {
                    val phone = screen.substringAfter(":")
                    WorkerJobsScreen(english, phone, records, onChange = { updated -> records = updated; saveRecords(context, updated); updated.forEach { firebase.updateRecord(it) } }, onBack = { screen = "home" })
                }
                else -> HomeScreen(english = english, onLanguage = { english = it; context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("english", it).apply() }, onBooking = { selectedPhoto = null; screen = "booking" }, onArtisan = { selectedPhoto = null; screen = "artisan" }, onAdmin = { screen = "adminLogin" }, onWorker = { screen = "workerLogin" }, recordCount = records.size)
            }
        }
    }
}

@Composable
fun PhoneOtpScreen(activity: ComponentActivity, english: Boolean, onLoggedIn: (FirebaseUser) -> Unit) {
    val firebase = remember { KaamSetuFirebase(activity) }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth -> auth.currentUser?.let(onLoggedIn) }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        onDispose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("KaamSetu – Paint & P.O.P", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(tx(english, "मोबाइल OTP से सुरक्षित लॉगिन", "Secure mobile OTP login"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(10); message = "" }, label = { Text(tx(english, "10 अंकों का मोबाइल नंबर", "10-digit mobile number")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        if (sent) OutlinedTextField(otp, { otp = it.filter(Char::isDigit).take(6) }, label = { Text(tx(english, "SMS OTP", "SMS OTP")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
        Button(enabled = !busy && if (sent) otp.length == 6 else phone.length == 10, onClick = {
            busy = true
            if (!sent) firebase.sendOtp(phone, { sent = true; busy = false; message = tx(english, "OTP भेज दिया गया।", "OTP sent.") }, { message = it; busy = false })
            else firebase.verifyOtp(otp, { onLoggedIn(it); busy = false }, { message = it; busy = false })
        }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(if (busy) tx(english, "कृपया प्रतीक्षा करें…", "Please wait…") else if (sent) tx(english, "OTP सत्यापित करें", "Verify OTP") else tx(english, "OTP भेजें", "Send OTP")) }
        Text(tx(english, "OTP के लिए Firebase Console में Phone provider चालू होना चाहिए। SMS शुल्क लागू हो सकते हैं।", "Phone provider must be enabled in Firebase Console. SMS charges may apply."), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun HomeScreen(english: Boolean, onLanguage: (Boolean) -> Unit, onBooking: () -> Unit, onArtisan: () -> Unit, onAdmin: () -> Unit, onWorker: () -> Unit, recordCount: Int) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { onLanguage(!english) }) { Text(if (english) "हिन्दी" else "English") } }
        Text("KaamSetu", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(tx(english, "काम मिले, हुनर आगे बढ़े", "Find Work. Grow Your Skills."), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(tx(english, "जम्मू-कश्मीर में भरोसेमंद POP और पेंटिंग सेवाएँ", "Trusted POP and painting services in Jammu & Kashmir"), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBooking, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) { Text(tx(english, "काम बुक करें", "Book a Job")) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onArtisan, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) { Text(tx(english, "कारीगर रजिस्ट्रेशन", "Worker Registration")) }
        OutlinedButton(onClick = onWorker, modifier = Modifier.fillMaxWidth()) { Text(tx(english, "कारीगर लॉगिन / मेरा काम", "Worker Login / My Jobs")) }
        TextButton(onClick = onAdmin) { Text(tx(english, "Admin Login", "Admin Login")) }
        Text(tx(english, "क्लाउड रिकॉर्ड: $recordCount", "Cloud records: $recordCount"), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
        Text(tx(english, "हमारी सेवाएँ", "Our Services"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        listOf(if (english) "POP Design" else "POP डिजाइन", if (english) "Painting" else "पेंटिंग", if (english) "Putty" else "पुट्टी", if (english) "Polish / Finishing" else "पॉलिश / फिनिशिंग").forEach { item ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(item, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) }
        }
        Spacer(Modifier.height(12.dp))
        Text(tx(english, "MVP 1.3 • Firebase क्लाउड सिंक", "MVP 1.3 • Firebase cloud sync"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun WorkerLoginScreen(english: Boolean, records: List<KaamRecord>, onBack: () -> Unit, onJobs: (String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center) {
        TextButton(onClick = onBack) { Text(tx(english, "← वापस", "← Back")) }
        Text(tx(english, "कारीगर लॉगिन", "Worker Login"), style = MaterialTheme.typography.headlineMedium)
        Text(tx(english, "पहले OTP लॉगिन करें; फिर रजिस्ट्रेशन वाला नंबर डालें", "Sign in with OTP, then enter your registered number"))
        OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(10); error = false }, label = { Text(tx(english, "मोबाइल नंबर", "Mobile number")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        if (error) Text(tx(english, "इस नंबर से कारीगर रजिस्ट्रेशन नहीं मिला।", "No worker registration found for this number."), color = MaterialTheme.colorScheme.error)
        Button(onClick = { if (records.any { it.type == "कारीगर" && it.phone == phone && it.status in listOf("मंजूर", "Approved") }) onJobs(phone) else error = true }, enabled = phone.length == 10, modifier = Modifier.fillMaxWidth()) { Text(tx(english, "मेरे काम देखें", "View My Jobs")) }
        Text(tx(english, "आपके OTP खाते से जुड़े और आपको सौंपे गए काम Firebase से सिंक होते हैं।", "Your own records and jobs assigned to your verified phone sync from Firebase."), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun WorkerJobsScreen(english: Boolean, phone: String, records: List<KaamRecord>, onChange: (List<KaamRecord>) -> Unit, onBack: () -> Unit) {
    val jobs = records.filter { it.type == "ग्राहक" && it.assignedWorkerPhone == phone }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = onBack) { Text(tx(english, "← होम", "← Home")) }
        Text(tx(english, "मेरे सौंपे गए काम", "My Assigned Jobs"), style = MaterialTheme.typography.headlineMedium)
        if (jobs.isEmpty()) Text(tx(english, "अभी आपको कोई काम नहीं सौंपा गया।", "No jobs have been assigned to you yet."))
        jobs.reversed().forEach { job ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${job.work} • ${job.name}", fontWeight = FontWeight.Bold)
                    Text("${job.phone} • ${job.district}, ${job.area}")
                    if (job.details.isNotBlank()) Text(job.details)
                    Text(tx(english, "स्थिति: ${job.status}", "Status: ${job.status}"))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("स्वीकार किया" to "Accepted", "अस्वीकार" to "Rejected", "काम जारी" to "In Progress", "पूरा" to "Completed").forEach { (hi, en) ->
                            TextButton(onClick = { onChange(records.map { if (it.id == job.id) it.copy(status = if (english) en else hi) else it }) }) { Text(if (english) en else hi) }
                        }
                    }
                }
            }
        }
        Text(tx(english, "आपको सौंपे गए काम Firebase से सिंक होते हैं। स्थिति बदलने के लिए इंटरनेट कनेक्शन जरूरी है।", "Jobs assigned to your verified phone sync from Firebase. An internet connection is required to update status."), style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryForm(english: Boolean, title: String, titleEn: String, subtitle: String, subtitleEn: String, buttonText: String, buttonEn: String, recordType: String, photoUri: Uri?, onPickPhoto: () -> Unit, onSave: (KaamRecord) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("श्रीनगर") }
    var area by remember { mutableStateOf("") }
    var work by remember { mutableStateOf("POP डिजाइन") }
    var details by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val districts = listOf("श्रीनगर", "अनंतनाग", "बारामूला", "बडगाम", "कुपवाड़ा", "पुलवामा", "शोपियां", "गांदरबल", "बांदीपोरा", "कुलगाम", "जम्मू", "सांबा", "कठुआ", "उधमपुर", "रियासी", "डोडा", "किश्तवाड़", "रामबन", "राजौरी", "पुंछ", "अन्य")
    val works = listOf("POP डिजाइन", "पेंटिंग", "पुट्टी", "पॉलिश")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = onBack) { Text(tx(english, "← वापस", "← Back")) }
        Text(tx(english, title, titleEn), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(tx(english, subtitle, subtitleEn), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(name, { name = it; saved = false }, label = { Text(tx(english, "पूरा नाम", "Full Name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(10); saved = false }, label = { Text(tx(english, "मोबाइल नंबर", "Mobile Number")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(tx(english, "जिला", "District"), fontWeight = FontWeight.SemiBold)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(district, {}, readOnly = true, label = { Text(tx(english, "जिला चुनें", "Select District")) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { districts.forEach { item -> DropdownMenuItem(text = { Text(if (english) mapOf("श्रीनगर" to "Srinagar", "अनंतनाग" to "Anantnag", "बारामूला" to "Baramulla", "बडगाम" to "Budgam", "कुपवाड़ा" to "Kupwara", "पुलवामा" to "Pulwama", "शोपियां" to "Shopian", "गांदरबल" to "Ganderbal", "बांदीपोरा" to "Bandipora", "कुलगाम" to "Kulgam", "जम्मू" to "Jammu", "सांबा" to "Samba", "कठुआ" to "Kathua", "उधमपुर" to "Udhampur", "रियासी" to "Reasi", "डोडा" to "Doda", "किश्तवाड़" to "Kishtwar", "रामबन" to "Ramban", "राजौरी" to "Rajouri", "पुंछ" to "Poonch", "अन्य" to "Other")[item] ?: item else item) }, onClick = { district = item; expanded = false }) } }
        }
        OutlinedTextField(area, { area = it; saved = false }, label = { Text(tx(english, "इलाका / पता", "Area / Address")) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp)); Text(tx(english, "काम का प्रकार", "Type of Work"), fontWeight = FontWeight.SemiBold)
        works.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = work == item, onClick = { work = item }); Text(if (english) mapOf("POP डिजाइन" to "POP Design", "पेंटिंग" to "Painting", "पुट्टी" to "Putty", "पॉलिश" to "Polish")[item] ?: item else item) } }
        OutlinedTextField(details, { details = it; saved = false }, label = { Text(if (recordType == "ग्राहक") tx(english, "काम की जानकारी", "Job Details") else tx(english, "अनुभव / काम की जानकारी", "Experience / Work Details")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onPickPhoto, modifier = Modifier.fillMaxWidth()) { Text(if (photoUri == null) tx(english, "गैलरी से फोटो चुनें", "Choose Photo from Gallery") else tx(english, "फोटो चुनी गई • दूसरी चुनें", "Photo selected • Choose another")) }
        Button(onClick = {
            val rec = KaamRecord(System.currentTimeMillis().toString(), recordType, name.trim(), phone, district, area.trim(), work, details.trim(), photoUri?.toString() ?: "", if (recordType == "कारीगर") "जाँच बाकी" else "नई")
            onSave(rec); saved = true
        }, enabled = name.isNotBlank() && phone.length == 10 && area.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text(tx(english, buttonText, buttonEn)) }
        if (saved) { Text(tx(english, "सफलतापूर्वक Firebase में सेव हो गया! Admin Dashboard में सिंक होगा।", "Saved to Firebase! It will sync to the Admin Dashboard."), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp)) }
        Text(tx(english, "ध्यान दें: रिकॉर्ड Firebase Firestore में और चुनी गई फोटो Firebase Storage में अपलोड होती है। इंटरनेट जरूरी है।", "Note: Records are saved in Firebase Firestore and selected photos are uploaded to Firebase Storage. Internet is required."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
fun AdminLoginScreen(english: Boolean, onBack: () -> Unit, onLogin: () -> Unit) {
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text(tx(english, "← वापस", "← Back")) }
        Text("Admin Login", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(tx(english, "Firebase OTP खाते की Admin अनुमति जाँची जाएगी।", "Admin permission is checked on your Firebase OTP account."))
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
        Button(enabled = !busy, onClick = {
            busy = true
            FirebaseAuth.getInstance().currentUser?.getIdToken(true)
                ?.addOnSuccessListener { token ->
                    busy = false
                    if (token.claims["admin"] == true) onLogin()
                    else message = tx(english, "इस खाते को Admin अनुमति नहीं मिली है। Firebase administrator को admin claim देना होगा।", "This account has no Admin permission. A Firebase administrator must grant the admin claim.")
                }?.addOnFailureListener { busy = false; message = it.localizedMessage ?: "Permission check failed" }
                ?: run { busy = false; message = tx(english, "पहले मोबाइल OTP लॉगिन करें।", "Please sign in with mobile OTP first.") }
        }, modifier = Modifier.fillMaxWidth()) { Text(tx(english, "Admin अनुमति जाँचें", "Check Admin Access")) }
    }
}

@Composable
fun AdminDashboardScreen(english: Boolean, records: List<KaamRecord>, onChange: (List<KaamRecord>) -> Unit, onBack: () -> Unit) {
    var tab by remember(english) { mutableStateOf(tx(english, "बुकिंग", "Bookings")) }
    val bookings = records.filter { it.type == "ग्राहक" }
    val workers = records.filter { it.type == "कारीगर" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = onBack) { Text(tx(english, "← होम पर जाएँ", "← Back to Home")) }
        Text("Admin Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(tx(english, "KaamSetu • Firebase क्लाउड रिकॉर्ड", "KaamSetu • Firebase cloud records"))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(tx(english, "बुकिंग", "Bookings") to bookings.size, tx(english, "कारीगर", "Workers") to workers.size, tx(english, "कुल", "Total") to records.size).forEach { pair ->
                Card(Modifier.weight(1f)) { Column(Modifier.padding(10.dp)) { Text(pair.first, style = MaterialTheme.typography.bodySmall); Text(pair.second.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) } }
            }
        }
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(tx(english, "बुकिंग", "Bookings"), tx(english, "कारीगर", "Workers"), tx(english, "सेटिंग", "Settings")).forEachIndexed { i, value -> SegmentedButton(selected = tab == value, onClick = { tab = value }, shape = SegmentedButtonDefaults.itemShape(index = i, count = 3)) { Text(value) } }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            tx(english, "बुकिंग", "Bookings") -> if (bookings.isEmpty()) Text(tx(english, "अभी कोई बुकिंग सेव नहीं है।", "No bookings saved yet.")) else bookings.reversed().forEach { r ->
                RecordCard(context = LocalContext.current, english = english, r = r, workers = workers.filter { it.status in listOf("मंजूर", "Approved") }, onAssign = { workerPhone -> onChange(records.map { if (it.id == r.id) it.copy(assignedWorkerPhone = workerPhone, status = "सौंपा गया") else it }) }, actions = if (english) listOf("New", "Contacted", "In Progress", "Completed") else listOf("नया", "संपर्क किया", "काम जारी", "पूरा"), onStatus = { s -> onChange(records.map { if (it.id == r.id) it.copy(status = s) else it }) })
            }
            tx(english, "कारीगर", "Workers") -> if (workers.isEmpty()) Text(tx(english, "अभी कोई कारीगर रजिस्ट्रेशन नहीं है।", "No worker registrations yet.")) else workers.reversed().forEach { r ->
                RecordCard(context = LocalContext.current, english = english, r = r, actions = if (english) listOf("Pending Review", "Approved", "Rejected") else listOf("जाँच बाकी", "मंजूर", "अस्वीकार"), onStatus = { s -> onChange(records.map { if (it.id == r.id) it.copy(status = s) else it }) })
            }
            else -> Text(tx(english, "सेवा क्षेत्र: जम्मू-कश्मीर के 20 जिले\nसेवाएँ: POP, पेंटिंग, पुट्टी, पॉलिश\nडेटा: Firebase Firestore और Storage\nसुरक्षा: OTP लॉगिन और Admin custom claim", "Service area: 20 districts of Jammu & Kashmir\nServices: POP, Painting, Putty, Polish\nData: Firebase Firestore and Storage\nSecurity: OTP login and Admin custom claim"))
        }
        Spacer(Modifier.height(18.dp))
        Text(tx(english, "जरूरी: Firebase Phone provider, Firestore और Storage चालू होने चाहिए तथा firestore.rules/storage.rules प्रकाशित हों। APK और अलग-अलग फोन पर परीक्षण अभी बाकी है।", "Important: Firebase Phone provider, Firestore and Storage must be enabled, and firestore.rules/storage.rules deployed. APK and multi-device testing are still pending."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordCard(context: Context, english: Boolean, r: KaamRecord, workers: List<KaamRecord> = emptyList(), onAssign: (String) -> Unit = {}, actions: List<String>, onStatus: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("${if (english) if (r.type == "ग्राहक") "Customer" else "Worker" else r.type} • ${if (english) mapOf("POP डिजाइन" to "POP Design", "पेंटिंग" to "Painting", "पुट्टी" to "Putty", "पॉलिश" to "Polish")[r.work] ?: r.work else r.work}", fontWeight = FontWeight.Bold)
            Text("${r.name} • ${r.phone}")
            Text("${if (english) mapOf("श्रीनगर" to "Srinagar", "अनंतनाग" to "Anantnag", "बारामूला" to "Baramulla", "बडगाम" to "Budgam", "कुपवाड़ा" to "Kupwara", "पुलवामा" to "Pulwama", "शोपियां" to "Shopian", "गांदरबल" to "Ganderbal", "बांदीपोरा" to "Bandipora", "कुलगाम" to "Kulgam", "जम्मू" to "Jammu", "सांबा" to "Samba", "कठुआ" to "Kathua", "उधमपुर" to "Udhampur", "रियासी" to "Reasi", "डोडा" to "Doda", "किश्तवाड़" to "Kishtwar", "रामबन" to "Ramban", "राजौरी" to "Rajouri", "पुंछ" to "Poonch", "अन्य" to "Other")[r.district] ?: r.district else r.district}, ${r.area}")
            if (r.details.isNotBlank()) Text(r.details)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val number = r.phone.filter(Char::isDigit).let { if (it.length == 10) "91$it" else it }
                    val message = Uri.encode(if (english) "Hello ${r.name}, we are contacting you about KaamSetu." else "नमस्ते ${r.name}, KaamSetu के संबंध में संपर्क कर रहे हैं।")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=$message"))
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }) { Text(tx(english, "WhatsApp संपर्क", "Contact on WhatsApp")) }
                if (r.photo.isNotBlank()) OutlinedButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(r.photo), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    } catch (_: Exception) {}
                }) { Text(tx(english, "फोटो देखें", "View Photo")) }
            }
            if (r.type == "ग्राहक") {
                var selectedWorker by remember(r.id, r.assignedWorkerPhone) { mutableStateOf(r.assignedWorkerPhone) }
                var workerMenu by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = workerMenu, onExpandedChange = { workerMenu = !workerMenu }) {
                    OutlinedTextField(value = selectedWorker, onValueChange = {}, readOnly = true, label = { Text(tx(english, "कारीगर का मोबाइल चुनें", "Assign worker phone")) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = workerMenu, onDismissRequest = { workerMenu = false }) {
                        workers.forEach { worker -> DropdownMenuItem(text = { Text("${worker.name} • ${worker.phone}") }, onClick = { selectedWorker = worker.phone; workerMenu = false }) }
                    }
                }
                Button(onClick = { if (selectedWorker.length == 10) onAssign(selectedWorker) }, enabled = selectedWorker.length == 10 && workers.any { it.phone == selectedWorker }) { Text(tx(english, "कारीगर को काम दें", "Assign Job")) }
                if (r.assignedWorkerPhone.isNotBlank()) Text(tx(english, "सौंपा गया: ${r.assignedWorkerPhone}", "Assigned to: ${r.assignedWorkerPhone}"))
            }
            Text(tx(english, "स्थिति: ${r.status}", "Status: ${r.status}"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                actions.forEach { s -> TextButton(onClick = { onStatus(s) }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(s) } }
            }
        }
    }
}
