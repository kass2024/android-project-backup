package com.xander.pos

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xander.pos.palm.PalmEvent
import com.xander.pos.palm.PalmHand
import com.xander.pos.palm.PalmRecord
import com.xander.pos.palm.PalmTask
import com.xander.pos.palm.PalmVeinStore
import com.xander.pos.reader.CardUid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

data class WalletShort(
    val name: String,
    val balance: Int,
    val amount: Int,
    val lines: String,
)

data class PendingSale(
    val uid: String,
    val name: String,
    val className: String,
    val photoUrl: String,
    val studentId: Int,
    val balance: Int,
    val amount: Int,
    val lines: String,
    val walletReady: Boolean = true,
)

enum class DeskOverlay { None, Settings, Record, Today, Verify }

data class PalmFace(
    val uid: String,
    val name: String,
    val className: String,
    val photoUrl: String,
)

data class TillLine(val name: String, val unit: String, val qty: Int, val amount: Int)

data class TillTicket(
    val name: String,
    val className: String = "",
    val items: String,
    val amount: Int,
    val at: String,
)

data class TillToday(
    val label: String = "",
    val sales: Int = 0,
    val invoices: Int = 0,
    val students: Int = 0,
    val portions: Int = 0,
    val items: List<TillLine> = emptyList(),
    val recent: List<TillTicket> = emptyList(),
    val error: String = "",
)

enum class RecordPhase { WaitCard, ConfirmChange, Capturing, Saved, Failed }

enum class ConfirmMethod { Palm, Signature }

data class StudentHit(
    val id: Int,
    val name: String,
    val className: String,
    val regno: String,
    val photoUrl: String,
    val cardUid: String,
)

data class PalmCapture(
    val phase: RecordPhase = RecordPhase.WaitCard,
    val uid: String = "",
    val name: String = "",
    val className: String = "",
    val photoUrl: String = "",
    val studentId: Int = 0,
    val taken: Int = 0,
    val total: Int = 3,
    val hand: PalmHand = PalmHand.Left,
    val leftFeature: String = "",
    val rightSaved: Boolean = false,
    val note: String = "",
)

class PosModel(app: Application) : AndroidViewModel(app) {
    private val store = MenuStore(app)
    internal val palmsStore = PalmVeinStore(app)
    private val _items = MutableStateFlow(store.items())
    val items: StateFlow<List<MenuItem>> = _items
    private val _qty = MutableStateFlow<Map<String, Int>>(emptyMap())
    val qty: StateFlow<Map<String, Int>> = _qty
    private val _phone = MutableStateFlow(store.canteenPhone)
    val phone: StateFlow<String> = _phone
    private val _schoolId = MutableStateFlow(store.schoolId)
    val schoolId: StateFlow<Int> = _schoolId
    private val _brand = MutableStateFlow(store.brand)
    val brand: StateFlow<SchoolBrand> = _brand
    private val _pickingSchool = MutableStateFlow(false)
    val pickingSchool: StateFlow<Boolean> = _pickingSchool
    private val _entered = MutableStateFlow(tillReady())
    val entered: StateFlow<Boolean> = _entered
    private val _pending = MutableStateFlow<PendingSale?>(null)
    val pending: StateFlow<PendingSale?> = _pending
    private val _shortfall = MutableStateFlow<WalletShort?>(null)
    val shortfall: StateFlow<WalletShort?> = _shortfall
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _banner = MutableStateFlow("")
    val banner: StateFlow<String> = _banner
    private val _bannerOk = MutableStateFlow(false)
    val bannerOk: StateFlow<Boolean> = _bannerOk
    private val _sold = MutableStateFlow(store.soldToday)
    val sold: StateFlow<Int> = _sold
    private val _overlay = MutableStateFlow(DeskOverlay.None)
    val overlay: StateFlow<DeskOverlay> = _overlay
    private val _capture = MutableStateFlow(PalmCapture())
    val capture: StateFlow<PalmCapture> = _capture
    private val _studentHits = MutableStateFlow<List<StudentHit>>(emptyList())
    val studentHits: StateFlow<List<StudentHit>> = _studentHits
    private val _searched = MutableStateFlow("")
    val searched: StateFlow<String> = _searched
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching
    private var searchJob: Job? = null
    /** Drops a search result that finished after the cashier typed something else. */
    private val searchGen = AtomicInteger()
    private val _palms = MutableStateFlow(palmsStore.all())
    val palms: StateFlow<List<PalmRecord>> = _palms
    private val _palmTask = MutableStateFlow<PalmTask>(PalmTask.Stop)
    val palmTask: StateFlow<PalmTask> = _palmTask
    private val _palmReload = MutableStateFlow(0)
    val palmReload: StateFlow<Int> = _palmReload
    private val _palmStatus = MutableStateFlow("Starting the palm scanner…")
    val palmStatus: StateFlow<String> = _palmStatus
    private val _palmReady = MutableStateFlow(false)
    val palmReady: StateFlow<Boolean> = _palmReady
    private val _nfcStatus = MutableStateFlow("Starting the contactless reader…")
    val nfcStatus: StateFlow<String> = _nfcStatus
    private val _palmNote = MutableStateFlow("Place the palm that was recorded.")
    val palmNote: StateFlow<String> = _palmNote
    private var lastUid = ""
    private var lastAt = 0L
    private var palmLock = false
    private var enrollMisses = 0
    private var lastRejectAt = 0L
    private var deskId = 0
    private var deskRef = ""
    private var deskLines: JSONArray? = null
    private val _deskWake = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val deskWake: SharedFlow<Int> = _deskWake.asSharedFlow()

    private val cardsFile = File(getApplication<Application>().filesDir, "student-cards.json")
    private val methodFile = File(getApplication<Application>().filesDir, "confirm-methods.json")
    private var confirmMethods: MutableMap<String, String>? = null
    private val _confirm = MutableStateFlow(ConfirmMethod.Palm)
    val confirm: StateFlow<ConfirmMethod> = _confirm
    private var cardsRaw = ""

    init {
        viewModelScope.launch {
            loadSavedCards()
            var ticks = 0
            while (isActive) {
                if (_brand.value.acronym.isNotBlank() && online()) {
                    if (ticks % 5 == 0) refreshCatalog()
                    coroutineScope {
                        val palms = async {
                            val recording = _overlay.value == DeskOverlay.Record &&
                                _capture.value.phase == RecordPhase.Capturing
                            if (!recording) syncPalmBackups()
                        }
                        val cards = async { syncCards() }
                        palms.await()
                        cards.await()
                    }
                }
                ticks++
                delay(3000)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                if (_entered.value && !_pickingSchool.value) {
                    val watching = _pending.value != null && deskId > 0
                    pullDesk(if (watching) 0 else 6)
                    if (watching) delay(200)
                } else {
                    delay(250)
                }
            }
        }
        syncPalm()
    }

    fun openTill() {
        if (_schoolId.value <= 0 || _brand.value.acronym.isBlank()) return
        _entered.value = true
        _pickingSchool.value = false
        _overlay.value = DeskOverlay.None
        _banner.value = ""
        syncPalm()
        viewModelScope.launch { syncPalmBackups() }
    }

    fun pickSchool(open: Boolean) {
        if (!open) {
            if (tillReady()) openTill() else _pickingSchool.value = true
            return
        }
        _pickingSchool.value = true
        _entered.value = false
        _pending.value = null
        _overlay.value = DeskOverlay.None
        _banner.value = ""
        syncPalm()
    }

    fun submitAcronym(raw: String) {
        val acronym = raw.trim()
        if (acronym.isEmpty()) {
            show("Enter the school acronym.", false)
            return
        }
        _banner.value = ""
        viewModelScope.launch {
            _busy.value = true
            try {
                val api = client()
                val json = io { api.school(acronym) }
                val parsed = brandFrom(json.optJSONObject("school") ?: JSONObject())
                val school = parsed.copy(logoUrl = api.deviceUrl(parsed.logoUrl))
                if (school.id <= 0 || school.acronym.isBlank()) {
                    show("That school acronym was not found.", false)
                    return@launch
                }
                chooseSchool(school)
            } catch (error: PosException) {
                show(error.message ?: "That school acronym was not found.", false)
            } finally {
                _busy.value = false
            }
        }
    }

    fun chooseSchool(school: SchoolBrand) {
        store.brand = school
        store.schoolId = school.id
        store.schoolName = school.name
        store.acronymConfirmed = true
        _banner.value = ""
        _schoolId.value = school.id
        _brand.value = school
        _pickingSchool.value = false
        _entered.value = true
        _overlay.value = DeskOverlay.None
        _qty.value = emptyMap()
        syncPalm()
        viewModelScope.launch {
            refreshCatalog()
            syncPalmBackups()
        }
    }

    private val _report = MutableStateFlow(TillToday())
    val report: StateFlow<TillToday> = _report
    private val _reportBusy = MutableStateFlow(false)
    val reportBusy: StateFlow<Boolean> = _reportBusy

    fun openToday() {
        if (_busy.value || _pending.value != null) return
        _overlay.value = DeskOverlay.Today
        _banner.value = ""
        refreshToday()
        syncPalm()
    }

    fun refreshToday() {
        val acronym = _brand.value.acronym
        if (acronym.isBlank()) return
        viewModelScope.launch {
            _reportBusy.value = true
            try {
                val json = io { client().today(acronym) }
                val sold = mutableListOf<TillLine>()
                val rows = json.optJSONArray("items")
                if (rows != null) {
                    for (i in 0 until rows.length()) {
                        val row = rows.optJSONObject(i) ?: continue
                        sold.add(TillLine(row.optString("name"), row.optString("unit"), row.optInt("qty"), row.optInt("amount")))
                    }
                }
                val tickets = mutableListOf<TillTicket>()
                val recent = json.optJSONArray("tickets") ?: json.optJSONArray("recent")
                if (recent != null) {
                    for (i in 0 until recent.length()) {
                        val row = recent.optJSONObject(i) ?: continue
                        tickets.add(
                            TillTicket(
                                name = row.optString("name"),
                                className = row.optString("className"),
                                items = row.optString("items"),
                                amount = row.optInt("amount"),
                                at = row.optString("at"),
                            ),
                        )
                    }
                }
                _report.value = TillToday(
                    label = json.optString("label"),
                    sales = json.optInt("sales"),
                    invoices = json.optInt("invoices"),
                    students = json.optInt("students"),
                    portions = json.optInt("portions"),
                    items = sold,
                    recent = tickets,
                )
                _sold.value = json.optInt("sales")
                store.soldToday = _sold.value
            } catch (error: Exception) {
                _report.value = _report.value.copy(error = error.message ?: "Could not load today's report.")
            } finally {
                _reportBusy.value = false
            }
        }
    }

    private val _faces = MutableStateFlow<List<PalmFace>>(emptyList())
    val faces: StateFlow<List<PalmFace>> = _faces
    private val _verified = MutableStateFlow<PalmFace?>(null)
    val verified: StateFlow<PalmFace?> = _verified
    private val _verifyNote = MutableStateFlow("Place a registered palm.")
    val verifyNote: StateFlow<String> = _verifyNote

    fun openVerify() {
        if (_busy.value) return
        _overlay.value = DeskOverlay.Verify
        _banner.value = ""
        _verified.value = null
        lookupShownAt = 0L
        _verifyNote.value = "Place a registered palm."
        refreshFaces()
        ensureStudentIndex()
        syncPalm()
        viewModelScope.launch { syncPalmBackups() }
    }

    fun openSettings() {
        if (_busy.value) return
        _overlay.value = DeskOverlay.Settings
        _banner.value = ""
        syncPalm()
        viewModelScope.launch { syncPalmBackups() }
    }

    fun closeSettings() {
        _overlay.value = DeskOverlay.None
        syncPalm()
    }

    fun openRecord() {
        if (_busy.value) return
        searchGen.incrementAndGet()
        searchJob?.cancel()
        searchJob = null
        _studentHits.value = emptyList()
        _searched.value = ""
        _searching.value = false
        _capture.value = PalmCapture()
        _overlay.value = DeskOverlay.Record
        syncPalm()
        ensureStudentIndex()
    }

    fun closeRecord() {
        searchGen.incrementAndGet()
        searchJob?.cancel()
        searchJob = null
        _searching.value = false
        _overlay.value = if (_pending.value != null) DeskOverlay.None else DeskOverlay.Settings
        refreshFaces()
        _capture.value = PalmCapture()
        syncPalm()
        _palmReload.value = _palmReload.value + 1
    }

    fun recordAnother(note: String = "") {
        searchGen.incrementAndGet()
        searchJob?.cancel()
        _studentHits.value = emptyList()
        _searched.value = ""
        _searching.value = false
        _capture.value = PalmCapture(note = note)
        _overlay.value = DeskOverlay.Record
        syncPalm()
    }

    fun searchAgain() {
        if (_busy.value) return
        searchGen.incrementAndGet()
        searchJob?.cancel()
        _studentHits.value = emptyList()
        _searched.value = ""
        _searching.value = false
        _capture.value = PalmCapture()
        syncPalm()
    }

    fun searchStudents(raw: String, byCard: Boolean = true) {
        val query = raw.trim()
        val generation = searchGen.incrementAndGet()
        searchJob?.cancel()
        if (_capture.value.phase != RecordPhase.WaitCard) return
        if (query.length < 2) {
            _studentHits.value = emptyList()
            _searched.value = ""
            _searching.value = false
            return
        }
        val acronym = _brand.value.acronym
        if (acronym.isBlank()) {
            _searching.value = false
            _capture.value = _capture.value.copy(note = "Choose the school before searching for a student.")
            return
        }
        if (_capture.value.note.startsWith("Could not") || _capture.value.note.startsWith("Cannot reach")) {
            _capture.value = _capture.value.copy(note = "")
        }
        ensureStudentIndex()
        if (indexAcronym == acronym && studentIndex.isNotEmpty()) {
            showStudentHits(query, filterStudents(query, byCard))
            _searching.value = false
            return
        }
        _searching.value = true
        searchJob = viewModelScope.launch {
            val mine = coroutineContext[Job]
            try {
                delay(120)
                if (generation != searchGen.get()) return@launch
                val loading = indexJob
                if (loading != null && loading.isActive) loading.join()
                if (generation != searchGen.get() || !isActive) return@launch
                if (_capture.value.phase != RecordPhase.WaitCard) return@launch
                if (indexAcronym == acronym && studentIndex.isNotEmpty()) {
                    showStudentHits(query, filterStudents(query, byCard))
                    return@launch
                }
                var json: JSONObject? = null
                var last: PosException? = null
                repeat(2) { attempt ->
                    try {
                        json = io { client().searchStudents(acronym, query) }
                        last = null
                        return@repeat
                    } catch (error: PosException) {
                        last = error
                        if (attempt == 0) delay(300)
                    }
                }
                if (generation != searchGen.get() || !isActive) return@launch
                if (_capture.value.phase != RecordPhase.WaitCard) return@launch
                val ready = json
                if (ready == null) throw last ?: PosException("Could not search students.")
                showStudentHits(query, filterStudents(query, byCard, parseStudents(ready)))
            } catch (error: PosException) {
                if (generation != searchGen.get() || !isActive) return@launch
                if (_capture.value.phase != RecordPhase.WaitCard) return@launch
                _studentHits.value = emptyList()
                _searched.value = query
                _capture.value = _capture.value.copy(note = error.message ?: "Could not search students.")
            } finally {
                if (searchJob === mine && generation == searchGen.get()) _searching.value = false
            }
        }
    }

    private var studentIndex: List<StudentHit> = emptyList()
    private val cardIndex = HashMap<String, StudentHit>()
    private var saleGen = 0
    private var heldProof: String? = null
    private var indexAcronym = ""
    private var indexJob: Job? = null

    private fun loadSavedCards() {
        val acronym = _brand.value.acronym
        if (acronym.isBlank() || !cardsFile.exists()) return
        try {
            val raw = cardsFile.readText()
            val json = JSONObject(raw)
            if (!json.optString("tillAcronym").equals(acronym, true)) return
            if (!json.optBoolean("indexed")) return
            val list = parseStudents(json)
            if (list.isEmpty()) return
            studentIndex = list
            indexAcronym = acronym
            reindexCards()
            cardsRaw = raw
        } catch (error: Exception) {
            Log.w("PosModel", "saved cards ${error.message}")
        }
    }

    /** Cards and photos stay on the till. The 3-second pull replaces them from the server. */
    private suspend fun syncCards() {
        val acronym = _brand.value.acronym
        if (acronym.isBlank()) return
        try {
            val json = io { client().studentIndex(acronym) }
            if (!json.optBoolean("indexed")) return
            json.put("tillAcronym", acronym)
            val raw = json.toString()
            val list = parseStudents(json)
            studentIndex = list
            indexAcronym = acronym
            reindexCards()
            refreshFaces()
            if (raw != cardsRaw) {
                cardsRaw = raw
                io { cardsFile.writeText(raw) }
            }
        } catch (error: Exception) {
            Log.w("PosModel", "card sync ${error.message}")
        }
    }

    /** Download this school once, then name search stays on the till. */
    private fun ensureStudentIndex() {
        val acronym = _brand.value.acronym
        if (acronym.isBlank()) return
        if (indexAcronym == acronym && studentIndex.isNotEmpty()) return
        if (indexJob?.isActive == true) return
        indexJob = viewModelScope.launch {
            try {
                val json = io { client().studentIndex(acronym) }
                if (!json.optBoolean("indexed")) return@launch
                val list = parseStudents(json)
                studentIndex = list
                indexAcronym = acronym
                reindexCards()
                refreshFaces()
                Log.i("PosModel", "student index ${list.size}")
            } catch (error: Exception) {
                Log.w("PosModel", "student index ${error.message}")
            }
        }
    }

    private fun filterStudents(query: String, byCard: Boolean, source: List<StudentHit> = studentIndex): List<StudentHit> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return source.filter { hit ->
            if (byCard) studentCardHit(hit.cardUid, q) else hit.name.contains(q, ignoreCase = true)
        }.take(20)
    }

    private fun studentCardHit(cardUid: String, query: String): Boolean {
        val stored = CardUid.lookupVariants(cardUid).map { it.uppercase() }.filter { it.length >= 4 }
        if (stored.isEmpty()) return false
        val typed = CardUid.lookupVariants(query).map { it.uppercase() }.filter { it.length >= 4 }
        if (typed.any { needle -> stored.any { it == needle || it.contains(needle) } }) return true
        val compact = query.uppercase().filter { it.isDigit() || it in 'A'..'F' }
        return compact.length >= 2 && stored.any { it.contains(compact) }
    }

    private fun showStudentHits(query: String, list: List<StudentHit>) {
        _studentHits.value = list
        _searched.value = query
        val note = _capture.value.note
        if (note.startsWith("Could not") || note.startsWith("Cannot reach")) {
            _capture.value = _capture.value.copy(note = "")
        }
    }

    private fun parseStudents(json: JSONObject): List<StudentHit> {
        val rows = json.optJSONArray("students") ?: JSONArray()
        val api = client()
        val list = mutableListOf<StudentHit>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val name = row.optString("name").trim()
            if (name.isBlank()) continue
            list.add(
                StudentHit(
                    id = row.optInt("id"),
                    name = name,
                    className = row.optString("className"),
                    regno = row.optString("regno"),
                    photoUrl = api.deviceUrl(row.optString("photoUrl")),
                    cardUid = row.optString("cardUid"),
                ),
            )
        }
        return list
    }

    private fun reindexCards() {
        cardIndex.clear()
        studentIndex.forEach { hit ->
            if (hit.cardUid.isBlank()) return@forEach
            CardUid.lookupVariants(hit.cardUid).forEach { key ->
                if (key.length >= 4) cardIndex[key.uppercase()] = hit
            }
        }
    }

    private fun localStudent(uid: String): StudentHit? {
        CardUid.lookupVariants(uid).forEach { key ->
            cardIndex[key.uppercase()]?.let { return it }
        }
        return cardIndex[uid.uppercase()]
    }

    fun chooseStudent(hit: StudentHit) {
        if (_busy.value || _capture.value.phase != RecordPhase.WaitCard) return
        val uid = CardUid.readerForm(hit.cardUid)
        searchGen.incrementAndGet()
        searchJob?.cancel()
        _studentHits.value = emptyList()
        _searched.value = ""
        _searching.value = false
        if (uid.length < 4) {
            _capture.value = PalmCapture(
                phase = RecordPhase.Capturing,
                name = hit.name,
                className = hit.className,
                photoUrl = hit.photoUrl,
                studentId = hit.id,
                total = 3,
                note = "No card is saved for ${hit.name}. Add the card on the school website, then record the palm.",
            )
            syncPalm()
            return
        }
        openCapture(uid, hit.name, hit.className, hit.photoUrl, hit.id)
    }

    fun confirmChangePalm() {
        val current = _capture.value
        if (current.phase != RecordPhase.ConfirmChange || current.uid.length < 4) return
        _capture.value = current.copy(
            phase = RecordPhase.Capturing,
            taken = 0,
            note = "Place either palm. The infrared camera takes 3 samples.",
        )
        syncPalm()
    }

    fun keepExistingPalm() {
        val name = _capture.value.name.ifBlank { "this student" }
        recordAnother("Kept the palm for $name.")
    }

    private fun openCapture(uid: String, name: String, className: String, photoUrl: String, studentId: Int) {
        val existing = palmsStore.find(uid) != null
        _capture.value = PalmCapture(
            phase = if (existing) RecordPhase.ConfirmChange else RecordPhase.Capturing,
            uid = uid.uppercase(),
            name = name,
            className = className,
            photoUrl = photoUrl,
            studentId = studentId,
            hand = PalmHand.Left,
            total = 3,
            note = if (existing) {
                "$name already has a palm on this till. Change it?"
            } else {
                "Place either palm. The infrared camera takes 3 samples."
            },
        )
        syncPalm()
    }

    fun retryRecord() {
        val current = _capture.value
        if (current.uid.length < 4) {
            _capture.value = PalmCapture()
        } else {
            _capture.value = current.copy(
                phase = RecordPhase.Capturing,
                taken = 0,
                total = 3,
                note = "Place either palm. The infrared camera takes 3 samples.",
            )
        }
        syncPalm()
    }

    fun recordThisStudent() {
        val sale = _pending.value ?: return
        if (_busy.value) return
        _capture.value = PalmCapture(
            phase = RecordPhase.Capturing,
            uid = sale.uid,
            name = sale.name,
            className = sale.className,
            photoUrl = sale.photoUrl,
            studentId = sale.studentId,
            hand = PalmHand.Left,
            total = 3,
            note = "Place either palm. The infrared camera takes 3 samples.",
        )
        _overlay.value = DeskOverlay.Record
        syncPalm()
    }

    fun deletePalm(uid: String) {
        palmsStore.delete(uid)
        _palms.value = palmsStore.all()
        if (_verified.value?.uid.equals(uid, ignoreCase = true)) {
            _verified.value = null
            _verifyNote.value = "Place a registered palm."
        }
        refreshFaces()
        _palmReload.value = _palmReload.value + 1
        viewModelScope.launch { syncPalmBackups() }
    }

    fun onNfcReady() {
        _nfcStatus.value = "Contactless reader ready"
    }

    fun onNfcUnavailable(reason: String) {
        _nfcStatus.value = reason
    }

    fun onPalm(event: PalmEvent) {
        when (event) {
            is PalmEvent.Status -> {
                _palmStatus.value = event.text
                _palmReady.value = event.ready
                if (_overlay.value == DeskOverlay.Record && _capture.value.phase == RecordPhase.Capturing) {
                    _capture.value = _capture.value.copy(note = event.text)
                }
                if (_pending.value != null && _overlay.value == DeskOverlay.None && event.text.isNotBlank()) {
                    _palmNote.value = event.text
                }
                if (_overlay.value == DeskOverlay.Verify && event.text.isNotBlank() && _verified.value == null) {
                    _verifyNote.value = event.text
                }
            }
            is PalmEvent.Identified -> onPalmIdentified(event.id, event.score, event.accepted)
            is PalmEvent.EnrollStep -> {
                val current = _capture.value
                if (current.phase != RecordPhase.Capturing) return
                _capture.value = current.copy(
                    taken = event.taken,
                    total = event.total,
                    note = if (event.taken <= 0) {
                        "Place either palm. The infrared camera takes ${event.total} samples."
                    } else {
                        "Infrared sample ${event.taken} of ${event.total}. Keep either palm on the glass."
                    },
                )
            }
            is PalmEvent.Enrolled -> onEnrolled(event.id, event.feature)
            is PalmEvent.EnrollFailed -> {
                val current = _capture.value
                enrollMisses = 0
                _capture.value = current.copy(phase = RecordPhase.Failed, note = event.reason)
                syncPalm()
            }
        }
    }

    fun total(): Int = _items.value.sumOf { item -> item.price * (_qty.value[item.id] ?: 0) }

    fun lines(): String = _items.value.mapNotNull { item ->
        val count = _qty.value[item.id] ?: 0
        if (count > 0) "${item.name} x$count" else null
    }.joinToString(", ")

    fun dismissShortfall() {
        _shortfall.value = null
    }

    fun add(id: String) {
        if (_busy.value || _pending.value != null || _overlay.value != DeskOverlay.None) return
        _shortfall.value = null
        val item = _items.value.firstOrNull { it.id == id } ?: return
        val have = _qty.value[id] ?: 0
        if (item.stock <= 0) {
            show("Set the opening stock for ${item.name} on the school website.", false)
            return
        }
        if (have >= item.stock) {
            show("Only ${item.stock} ${item.name} left.", false)
            return
        }
        _qty.value = _qty.value.toMutableMap().apply { this[id] = have + 1 }
        _banner.value = ""
    }

    fun remove(id: String) {
        if (_busy.value || _pending.value != null || _overlay.value != DeskOverlay.None) return
        _shortfall.value = null
        val next = _qty.value.toMutableMap()
        val count = (next[id] ?: 0) - 1
        if (count <= 0) next.remove(id) else next[id] = count
        _qty.value = next
    }

    fun clearCart() {
        _qty.value = emptyMap()
        _shortfall.value = null
    }

    fun cancelSale() {
        if (_busy.value) return
        val dropping = deskId
        val acronym = _brand.value.acronym
        saleGen++
        heldProof = null
        if (dropping > 0) _qty.value = emptyMap()
        clearDesk()
        _pending.value = null
        palmLock = false
        syncPalm()
        if (dropping > 0 && acronym.isNotBlank()) {
            viewModelScope.launch {
                try {
                    io { client().deskCancel(acronym, dropping) }
                } catch (error: Exception) {
                    Log.w("PosModel", "desk cancel ${error.message}")
                }
            }
        }
    }

    private fun clearDesk() {
        deskId = 0
        deskRef = ""
        deskLines = null
    }

    private suspend fun pullDesk(waitSeconds: Int = 0) {
        val acronym = _brand.value.acronym
        if (acronym.isBlank() || palmLock) return
        val holding = if (_pending.value != null && deskId > 0) deskId else 0
        try {
            val json = io { client().desk(acronym, holding, if (holding > 0) 0 else waitSeconds) }
            val paidId = json.optInt("paidId")
            if (paidId > 0 && paidId == deskId) {
                val sale = _pending.value
                val amount = json.optInt("amount", sale?.amount ?: 0)
                val balance = json.optInt("balance", sale?.balance ?: 0)
                val name = sale?.name ?: "the student"
                _qty.value = emptyMap()
                _pending.value = null
                clearDesk()
                heldProof = null
                palmLock = false
                _sold.value = _sold.value + amount
                store.soldToday = _sold.value
                show("Charged ${money(amount)} from $name. Balance ${money(balance)}.", true)
                syncPalm()
                return
            }
            val cancelId = json.optInt("cancelId")
            if (cancelId > 0 && cancelId == deskId) {
                clearDesk()
                heldProof = null
                palmLock = false
                _qty.value = emptyMap()
                _pending.value = null
                show("The cashier cancelled this sale.", false)
                syncPalm()
                return
            }
            if (holding > 0) return
            val request = json.optJSONObject("request") ?: return
            if (palmLock) return
            val recording = _overlay.value == DeskOverlay.Record &&
                _capture.value.phase == RecordPhase.Capturing
            if (recording) return
            if (_overlay.value != DeskOverlay.None) _overlay.value = DeskOverlay.None
            _busy.value = false
            openDesk(request)
            yield()
            syncPalm()
        } catch (error: Exception) {
            Log.w("PosModel", "desk ${error.message}")
        }
    }

    private fun openDesk(row: JSONObject) {
        val lines = row.optJSONArray("lines") ?: JSONArray()
        val payload = JSONArray()
        val nextQty = mutableMapOf<String, Int>()
        for (i in 0 until lines.length()) {
            val line = lines.optJSONObject(i) ?: continue
            val id = line.optInt("id")
            val count = line.optInt("qty")
            if (id <= 0 || count <= 0) continue
            payload.put(JSONObject().put("id", id).put("qty", count))
            nextQty[id.toString()] = count
        }
        val card = row.optString("card").trim()
        if (payload.length() == 0 || card.length < 4) return
        saleGen++
        deskId = row.optInt("id")
        deskRef = row.optString("clientRef")
        deskLines = payload
        heldProof = null
        palmLock = false
        _shortfall.value = null
        _qty.value = nextQty
        val name = row.optString("name").ifBlank { "Student" }
        _pending.value = PendingSale(
            uid = card.uppercase(),
            name = name,
            className = row.optString("className"),
            photoUrl = client().deviceUrl(row.optString("photoUrl")),
            studentId = row.optInt("studentId"),
            balance = row.optInt("balance"),
            amount = row.optInt("amount"),
            lines = row.optString("items"),
            walletReady = true,
        )
        noteForConfirm(card, name)
        _deskWake.tryEmit(deskId)
    }

    fun hasPalm(uid: String): Boolean = palmsStore.find(uid) != null

    private fun sameStudentCard(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return true
        val left = com.xander.pos.reader.CardUid.lookupVariants(a).map { it.uppercase() }.toSet()
        if (b.uppercase() in left) return true
        return com.xander.pos.reader.CardUid.lookupVariants(b).any { it.uppercase() in left }
    }

    fun onCard(raw: String) {
        val uid = CardUid.readerForm(raw)
        if (uid.length < 4 || !_entered.value || _pickingSchool.value || _busy.value) return
        if (_overlay.value == DeskOverlay.Record) {
            if (_capture.value.phase == RecordPhase.WaitCard) beginRecord(uid)
            return
        }
        if (_overlay.value != DeskOverlay.None || _pending.value != null) return
        val now = System.currentTimeMillis()
        if (sameStudentCard(uid, lastUid) && now - lastAt < 400) return
        lastUid = uid
        lastAt = now
        if (_qty.value.values.none { it > 0 }) {
            if (palmsStore.find(uid) != null) {
                _overlay.value = DeskOverlay.Record
                _capture.value = PalmCapture(note = "Checking this card…")
                beginRecord(uid)
            } else {
                show("Set a quantity first, then tap the student card.", false)
            }
            return
        }
        val amount = total()
        if (!online()) {
            show("Connect to the internet before confirming this palm.", false)
            return
        }
        val known = localStudent(uid) ?: StudentHit(0, "Student", "", "", "", uid)
        openFastSale(uid, known, amount)
    }

    /** The card list is already on the till, so the next student opens at once. */
    private fun openFastSale(uid: String, hit: StudentHit, amount: Int) {
        val gen = ++saleGen
        heldProof = null
        palmLock = false
        _shortfall.value = null
        _pending.value = PendingSale(
            uid = uid.uppercase(),
            name = hit.name,
            className = hit.className,
            photoUrl = hit.photoUrl,
            studentId = hit.id,
            balance = 0,
            amount = amount,
            lines = lines(),
            walletReady = false,
        )
        noteForConfirm(uid, hit.name)
        syncPalm()
        viewModelScope.launch {
            try {
                val json = io { client().preview(_brand.value.acronym, uid) }
                if (gen != saleGen) return@launch
                val balance = json.optJSONObject("student")?.optDouble("balance", 0.0)?.toInt()
                    ?: json.optDouble("balance", 0.0).toInt()
                applyWallet(uid, balance, gen)
            } catch (error: Exception) {
                if (gen != saleGen) return@launch
                _palmNote.value = error.message ?: "Cannot reach the school server."
            }
        }
    }

    private fun applyWallet(uid: String, balance: Int, gen: Int) {
        val sale = _pending.value ?: return
        if (gen != saleGen || !sameStudentCard(sale.uid, uid)) return
        if (balance < sale.amount) {
            heldProof = null
            palmLock = false
            _pending.value = null
            _shortfall.value = WalletShort(sale.name, balance, sale.amount, sale.lines)
            syncPalm()
            return
        }
        val ready = sale.copy(balance = balance, walletReady = true)
        _pending.value = ready
        val proof = heldProof
        if (proof != null && !palmLock) {
            heldProof = null
            palmLock = true
            _palmNote.value = "Palm matched. Charging the card…"
            charge(ready, proof)
        }
    }

    private fun beginRecord(uid: String) {
        viewModelScope.launch { syncPalmBackups() }
        viewModelScope.launch {
            _busy.value = true
            try {
                val json = io { client().preview(_brand.value.acronym, uid) }
                val student = json.optJSONObject("student") ?: JSONObject()
                openCapture(
                    uid,
                    student.optString("name", "Student"),
                    student.optString("className"),
                    client().deviceUrl(student.optString("photoUrl")),
                    student.optInt("id"),
                )
            } catch (error: PosException) {
                _capture.value = _capture.value.copy(note = error.message ?: "Card not found.")
            } finally {
                _busy.value = false
            }
        }
    }

    private fun preview(uid: String, amount: Int?) {
        if (amount != null && !online()) {
            show("Connect to the internet before confirming this palm.", false)
            return
        }
        val gen = saleGen
        viewModelScope.launch {
            _busy.value = true
            try {
                val json = io { client().preview(_brand.value.acronym, uid) }
                if (gen != saleGen) return@launch
                val student = json.optJSONObject("student") ?: JSONObject()
                val name = student.optString("name", "Student")
                val balance = student.optDouble("balance", 0.0).toInt()
                if (amount == null) {
                    val palm = if (palmsStore.find(uid) == null) " No palm recorded yet." else " Palm is on file."
                    show("$name has ${money(balance)}.$palm Add what they took, then tap the card.", true)
                } else {
                    if (balance < amount) {
                        _pending.value = null
                        palmLock = false
                        _shortfall.value = WalletShort(
                            name = name,
                            balance = balance,
                            amount = amount,
                            lines = lines(),
                        )
                        syncPalm()
                        return@launch
                    }
                    _shortfall.value = null
                    palmLock = false
                    _pending.value = PendingSale(
                        uid = uid,
                        name = name,
                        className = student.optString("className"),
                        photoUrl = client().deviceUrl(student.optString("photoUrl")),
                        studentId = student.optInt("id"),
                        balance = balance,
                        amount = amount,
                        lines = lines(),
                    )
                    noteForConfirm(uid, name)
                    syncPalm()
                }
            } catch (error: PosException) {
                show(error.message ?: "Card not found.", false)
            } finally {
                _busy.value = false
            }
        }
    }

    private var lookupShownAt = 0L

    private fun onLookup(id: String, score: Int, accepted: Boolean) {
        if (!accepted) {
            val now = System.currentTimeMillis()
            if (score <= 0) {
                if (_verified.value != null && now - lookupShownAt > 1000) {
                    _verified.value = null
                    _verifyNote.value = "Place a registered palm."
                }
                return
            }
            if (now - lookupShownAt < 1000) return
            if (now - lastRejectAt < 1600) return
            lastRejectAt = now
            _verified.value = null
            _verifyNote.value = "Not a registered palm (score $score). Place the next palm."
            return
        }
        val record = palmsStore.find(id) ?: return
        val face = faceFor(record)
        lookupShownAt = System.currentTimeMillis()
        _verified.value = face
        _verifyNote.value = "This palm belongs to ${face.name}. Place the next palm."
    }

    private fun refreshFaces() {
        _faces.value = palmsStore.all().map { faceFor(it) }
        val shown = _verified.value ?: return
        val still = _faces.value.firstOrNull { sameStudentCard(it.uid, shown.uid) }
        _verified.value = still
        if (still == null) _verifyNote.value = "Place a registered palm."
    }

    private fun faceFor(record: PalmRecord): PalmFace {
        val hit = studentForCard(record.uid)
        return PalmFace(
            uid = record.uid,
            name = record.name.ifBlank { hit?.name ?: "Student" },
            className = record.className.ifBlank { hit?.className ?: "" },
            photoUrl = hit?.photoUrl ?: "",
        )
    }

    private fun studentForCard(uid: String): StudentHit? {
        if (uid.isBlank()) return null
        return studentIndex.firstOrNull { hit ->
            hit.cardUid.isNotBlank() && sameStudentCard(hit.cardUid, uid)
        }
    }

    private fun onPalmIdentified(id: String, score: Int, accepted: Boolean) {
        if (_overlay.value == DeskOverlay.Verify) {
            onLookup(id, score, accepted)
            return
        }
        val sale = _pending.value ?: return
        if (_confirm.value != ConfirmMethod.Palm) return
        if (_overlay.value != DeskOverlay.None || _busy.value || palmLock) return
        if (!accepted) {
            val now = System.currentTimeMillis()
            if (now - lastRejectAt < 1600) return
            lastRejectAt = now
            _palmNote.value = if (score > 0) {
                "Not this palm (score $score). Place the one recorded for ${sale.name}."
            } else {
                "Not this palm. Place the one recorded for ${sale.name}."
            }
            return
        }
        // Engine maps numeric SDK palm ids back to card UIDs before this event.
        if (!sameStudentCard(id, sale.uid)) {
            val owner = palmsStore.find(id)?.name ?: "another student"
            _palmNote.value = "That palm belongs to $owner."
            return
        }
        if (palmsStore.find(sale.uid) == null) {
            _palmNote.value = "No palm recorded for ${sale.name}."
            return
        }
        if (!online()) {
            _palmNote.value = "Palm matched. Connect to the internet, then place it again to confirm."
            return
        }
        val proof = palmProofPng(sale.name, sale.uid, score)
        if (!sale.walletReady) {
            heldProof = proof
            _palmNote.value = "Palm matched. Checking the wallet…"
            return
        }
        palmLock = true
        _palmNote.value = "Palm matched. Charging the card…"
        charge(sale, proof)
    }

    private fun onEnrolled(id: String, feature: String) {
        enrollMisses = 0
        val current = _capture.value
        if (!id.equals(current.uid, ignoreCase = true)) return
        rememberPalm(current, feature, reloadSdk = true)
        val pending = _pending.value
        if (pending != null && sameStudentCard(current.uid, pending.uid)) {
            _palmNote.value = "Palm saved. Place it again to confirm payment."
            closeRecord()
            return
        }
        recordAnother("Palm saved for ${current.name}. Find the next student.")
    }

    private fun charge(sale: PendingSale, proof: String) {
        viewModelScope.launch {
            _busy.value = true
            _banner.value = ""
            try {
                val ref = deskRef.ifBlank { UUID.randomUUID().toString().replace("-", "").take(24) }
                val payload = deskLines ?: JSONArray().also { rows ->
                    _items.value.forEach { item ->
                        val count = _qty.value[item.id] ?: 0
                        if (count > 0) rows.put(JSONObject().put("id", item.id.toIntOrNull() ?: 0).put("qty", count))
                    }
                }
                val json = io {
                    client().charge(
                        _brand.value.acronym,
                        sale.uid,
                        sale.amount,
                        _phone.value,
                        sale.lines,
                        payload,
                        proof,
                        ref,
                    )
                }
                val student = json.optJSONObject("student") ?: JSONObject()
                val name = student.optString("name", sale.name)
                val balance = student.optDouble("balance", json.optDouble("balance", 0.0)).toInt()
                _qty.value = emptyMap()
                _pending.value = null
                clearDesk()
                palmLock = false
                _sold.value = _sold.value + sale.amount
                store.soldToday = _sold.value
                show("Charged ${money(sale.amount)} from $name. Balance ${money(balance)}.", true)
                syncPalm()
                refreshCatalog()
            } catch (error: PosException) {
                palmLock = false
                if ((error.message ?: "").contains("cancelled", ignoreCase = true)) {
                    clearDesk()
                    _pending.value = null
                    syncPalm()
                }
                val fallback = if (_confirm.value == ConfirmMethod.Signature) {
                    "The signature was not accepted. Draw it again."
                } else {
                    "The card was not charged. Place the palm again."
                }
                _palmNote.value = error.message ?: fallback
                show(error.message ?: "The card was not charged.", false)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun syncPalm() {
        val capture = _capture.value
        _palmTask.value = when {
            !_entered.value || _pickingSchool.value -> PalmTask.Stop
            _overlay.value == DeskOverlay.Record && capture.phase == RecordPhase.Capturing && capture.uid.length >= 4 ->
                PalmTask.Enroll(capture.uid, capture.hand)
            _overlay.value == DeskOverlay.Record -> PalmTask.Watch
            _overlay.value == DeskOverlay.Verify -> PalmTask.Lookup
            _pending.value != null && _confirm.value == ConfirmMethod.Palm -> PalmTask.Verify(_pending.value!!.uid)
            _pending.value != null -> PalmTask.Stop
            _entered.value -> PalmTask.Watch
            else -> PalmTask.Stop
        }
    }

    private suspend fun refreshCatalog() {
        val acronym = _brand.value.acronym
        if (acronym.isBlank()) return
        try {
            val json = io { client().catalog(acronym) }
            val rows = json.optJSONArray("items")
            val list = mutableListOf<MenuItem>()
            if (rows != null) {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val name = row.optString("name").trim()
                    val price = row.optInt("price")
                    if (name.isNotBlank() && price > 0) {
                        list.add(MenuItem(row.optInt("id").toString(), name, price, row.optInt("stock"), row.optString("unit")))
                    }
                }
            }
            val brandJson = json.optJSONObject("brand")
            if (brandJson != null) {
                val incoming = brandFrom(brandJson)
                val current = _brand.value
                val brand = incoming.copy(
                    acronym = incoming.acronym.ifBlank { current.acronym },
                    id = if (incoming.id > 0) incoming.id else current.id,
                )
                if (brand.acronym.isNotBlank() && brand.id > 0) {
                    store.brand = brand
                    store.acronymConfirmed = true
                    _brand.value = brand
                    _schoolId.value = brand.id
                    if (!_pickingSchool.value) _entered.value = true
                }
            }
            store.save(list)
            store.canteenPhone = json.optString("canteenPhone")
            store.schoolName = json.optString("school", store.schoolName)
            _items.value = list
            _phone.value = store.canteenPhone
        } catch (error: PosException) {
            if (_items.value.isEmpty()) show(error.message ?: "Could not download the menu.", false)
        }
    }

    private fun tillReady(): Boolean =
        store.acronymConfirmed && store.schoolId > 0 && store.brand.acronym.isNotBlank()

    private fun rememberPalm(current: PalmCapture, feature: String, reloadSdk: Boolean) {
        palmsStore.save(
            PalmRecord(
                uid = current.uid.uppercase(),
                name = current.name,
                className = current.className,
                feature = feature,
                savedAt = System.currentTimeMillis(),
                studentId = current.studentId,
            ),
        )
        _palms.value = palmsStore.all()
        if (reloadSdk) _palmReload.value = _palmReload.value + 1
        viewModelScope.launch {
            val ok = uploadPalm(current.uid)
            if (!ok) {
                val note = _capture.value.note
                if (!note.contains("Server backup will retry")) {
                    _capture.value = _capture.value.copy(note = "$note Server backup will retry.")
                }
            }
        }
    }

    private suspend fun uploadPalm(uid: String): Boolean {
        val record = palmsStore.find(uid) ?: return false
        if (record.syncedAt >= record.savedAt) return true
        return try {
            val json = io {
                client().savePalm(
                    _brand.value.acronym,
                    record.uid,
                    record.name,
                    record.className,
                    record.feature,
                    record.studentId,
                    record.savedAt,
                )
            }
            if (json.optBoolean("deleted")) {
                return false
            }
            palmsStore.markSynced(record.uid, record.savedAt)
            _palms.value = palmsStore.all()
            true
        } catch (error: Exception) {
            Log.e("PosModel", "savePalm ${record.uid} failed", error)
            val detail = error.message?.trim().orEmpty()
            if (detail.isNotEmpty()) {
                val note = _capture.value.note
                if (!note.contains(detail)) {
                    _capture.value = _capture.value.copy(note = "$note $detail".trim())
                }
            }
            false
        }
    }

    /** This till has a network path. Palm matching stays on the device; only the charge needs this. */
    private fun online(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun syncPalmBackups(): Boolean {
        val acronym = _brand.value.acronym
        if (acronym.isBlank() || _schoolId.value <= 0) return false
        var changed = false
        var ok = true
        // A delete on this till stays here. The server copy is kept so a new install can restore it.
        palmsStore.all().filter { it.syncedAt < it.savedAt }.forEach { record ->
            try {
                io {
                    val saved = client().savePalm(
                        acronym,
                        record.uid,
                        record.name,
                        record.className,
                        record.feature,
                        record.studentId,
                        record.savedAt,
                    )
                    if (saved.optBoolean("deleted")) {
                        throw PalmRemoved()
                    }
                }
                palmsStore.markSynced(record.uid, record.savedAt)
                changed = true
            } catch (_: PalmRemoved) {
                palmsStore.forget(record.uid)
                changed = true
            } catch (error: Exception) {
                ok = false
                Log.e("PosModel", "savePalm ${record.uid} failed", error)
            }
        }
        // Pull palms saved on the server onto this till. A palm removed on the website
        // is removed here on the same check. A delete done on this till is not sent back.
        try {
            val json = io { client().palms(acronym) }
            val rows = json.optJSONArray("palms") ?: JSONArray()
            val hidden = palmsStore.tombstones()
            val present = mutableSetOf<String>()
            var dropped = false
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val card = row.optString("card").trim().uppercase()
                val feature = row.optString("feature").trim()
                if (card.length < 4 || feature.length < 40) continue
                val keys = cardKeys(card)
                present += keys
                if (keys.any { it in hidden }) continue
                val savedAt = row.optLong("savedAt").let { if (it > 0L) it else System.currentTimeMillis() }
                val local = palmsStore.find(card)
                if (local != null && (local.syncedAt < local.savedAt || local.savedAt >= savedAt)) continue
                palmsStore.save(
                    PalmRecord(
                        uid = card,
                        name = row.optString("name"),
                        className = row.optString("className"),
                        feature = feature,
                        savedAt = savedAt,
                        studentId = row.optInt("studentId"),
                        syncedAt = savedAt,
                    ),
                )
                changed = true
            }
            val removed = json.optJSONArray("deleted") ?: JSONArray()
            val removedAt = mutableMapOf<String, Long>()
            val removedStudentAt = mutableMapOf<Int, Long>()
            for (i in 0 until removed.length()) {
                val row = removed.optJSONObject(i) ?: continue
                val deletedAt = row.optLong("deletedAt")
                cardKeys(row.optString("card")).forEach { key ->
                    if (deletedAt >= (removedAt[key] ?: 0L)) removedAt[key] = deletedAt
                }
                val studentId = row.optInt("studentId")
                if (studentId > 0 && deletedAt >= (removedStudentAt[studentId] ?: 0L)) {
                    removedStudentAt[studentId] = deletedAt
                }
            }
            palmsStore.all().toList().forEach { record ->
                val byCard = cardKeys(record.uid).mapNotNull { removedAt[it] }.maxOrNull()
                val byStudent = removedStudentAt[record.studentId]
                val deletedAt = listOfNotNull(byCard, byStudent).maxOrNull() ?: return@forEach
                if (deletedAt > 0L && record.savedAt > deletedAt) return@forEach
                palmsStore.forget(record.uid)
                changed = true
                dropped = true
            }
            palmsStore.all().filter { it.syncedAt >= it.savedAt && it.savedAt > 0L }.forEach { record ->
                if (cardKeys(record.uid).none { it in present }) {
                    palmsStore.forget(record.uid)
                    changed = true
                    dropped = true
                }
            }
            if (dropped) {
                val shown = _verified.value
                if (shown != null && palmsStore.find(shown.uid) == null) {
                    _verified.value = null
                    _verifyNote.value = "That palm was removed. Place the next palm."
                }
                val sale = _pending.value
                if (sale != null && palmsStore.find(sale.uid) == null) {
                    _palmNote.value = "This palm was removed on the school website."
                }
            }
        } catch (error: Exception) {
            Log.e("PosModel", "palm backup sync failed", error)
            ok = false
        }
        if (changed) {
            _palms.value = palmsStore.all()
            refreshFaces()
            val recording = _overlay.value == DeskOverlay.Record && _capture.value.phase == RecordPhase.Capturing
            if (!recording && (_pending.value == null || palmsStore.find(_pending.value?.uid.orEmpty()) == null)) {
                _palmReload.value = _palmReload.value + 1
                syncPalm()
            }
        }
        return ok
    }

    private fun cardKeys(uid: String): Set<String> {
        val keys = com.xander.pos.reader.CardUid.lookupVariants(uid).map { it.trim().uppercase() }.filter { it.length >= 4 }.toSet()
        if (keys.isNotEmpty()) return keys
        val key = uid.trim().uppercase()
        return if (key.length >= 4) setOf(key) else emptySet()
    }

    fun chooseConfirm(uid: String, method: ConfirmMethod) {
        if (_busy.value || uid.length < 4) return
        cardKeys(uid).forEach { confirmMap()[it] = if (method == ConfirmMethod.Signature) "signature" else "palm" }
        saveConfirmMap()
        val name = _pending.value?.name ?: "this student"
        noteForConfirm(uid, name)
        syncPalm()
    }

    fun chargeWithSignature(png: String) {
        val sale = _pending.value ?: return
        if (_busy.value || palmLock || _confirm.value != ConfirmMethod.Signature) return
        if (!online()) {
            _palmNote.value = "Connect to the internet before confirming this signature."
            return
        }
        if (png.length < 80) {
            _palmNote.value = "Draw the signature first."
            return
        }
        palmLock = true
        _palmNote.value = "Saving the signature…"
        charge(sale, png)
    }

    private fun noteForConfirm(uid: String, name: String) {
        val method = savedMethod(uid)
        _confirm.value = method
        _palmNote.value = if (method == ConfirmMethod.Signature) {
            "Draw the signature for $name."
        } else if (palmsStore.find(uid) == null) {
            "No palm recorded for $name. Record it, or use a signature."
        } else {
            "Place the palm recorded for $name."
        }
    }

    private fun savedMethod(uid: String): ConfirmMethod {
        val map = confirmMap()
        return if (cardKeys(uid).any { map[it] == "signature" }) ConfirmMethod.Signature else ConfirmMethod.Palm
    }

    private fun confirmMap(): MutableMap<String, String> {
        confirmMethods?.let { return it }
        val map = mutableMapOf<String, String>()
        try {
            if (methodFile.exists()) {
                val json = JSONObject(methodFile.readText())
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next().trim().uppercase()
                    if (key.length >= 4) map[key] = json.optString(key)
                }
            }
        } catch (error: Exception) {
            Log.w("PosModel", "confirm methods ${error.message}")
        }
        confirmMethods = map
        return map
    }

    private fun saveConfirmMap() {
        val json = JSONObject()
        confirmMap().forEach { (key, value) -> json.put(key, value) }
        try {
            methodFile.writeText(json.toString())
        } catch (error: Exception) {
            Log.w("PosModel", "confirm methods save ${error.message}")
        }
    }

    private fun show(text: String, ok: Boolean) {
        _banner.value = text
        _bannerOk.value = ok
    }

    private fun client() = PosClient(BuildConfig.DEFAULT_BASE_URL, BuildConfig.POS_KEY)

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}

fun money(amount: Int): String = "%,d RWF".format(amount)

private class PalmRemoved : Exception()
