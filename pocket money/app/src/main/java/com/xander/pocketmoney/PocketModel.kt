package com.xander.pocketmoney

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed class Destination {
    data object Auth : Destination()
    data object Home : Destination()
    data class Pay(val studentId: Int) : Destination()
    data class Activity(val studentId: Int) : Destination()
}

class PocketModel(app: Application) : AndroidViewModel(app) {
    private val store = SessionStore(app)
    private val _destination = MutableStateFlow<Destination>(if (store.parent() == null) Destination.Auth else Destination.Home)
    val destination: StateFlow<Destination> = _destination
    private val _parent = MutableStateFlow(store.parent())
    val parent: StateFlow<ParentAccount?> = _parent
    private val _students = MutableStateFlow<List<StudentWallet>>(emptyList())
    val students: StateFlow<List<StudentWallet>> = _students
    private val _ledger = MutableStateFlow<List<LedgerItem>>(emptyList())
    val ledger: StateFlow<List<LedgerItem>> = _ledger
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error
    private val _notice = MutableStateFlow("")
    val notice: StateFlow<String> = _notice
    private val _found = MutableStateFlow<StudentWallet?>(null)
    val found: StateFlow<StudentWallet?> = _found
    private val _matches = MutableStateFlow<List<StudentWallet>>(emptyList())
    val matches: StateFlow<List<StudentWallet>> = _matches
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching
    private val _lang = MutableStateFlow(store.lang)
    val lang: StateFlow<String> = _lang
    private val _langChosen = MutableStateFlow(store.langChosen)
    val langChosen: StateFlow<Boolean> = _langChosen
    private val _payMessage = MutableStateFlow("")
    val payMessage: StateFlow<String> = _payMessage
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending
    private val _resetSearch = MutableStateFlow(0)
    val resetSearch: StateFlow<Int> = _resetSearch
    private val _chargePercent = MutableStateFlow(3.0)
    val chargePercent: StateFlow<Double> = _chargePercent
    private val _signupCount = MutableStateFlow(0)
    val signupCount: StateFlow<Int> = _signupCount
    private val _family = MutableStateFlow<FamilyWelcome?>(null)
    val family: StateFlow<FamilyWelcome?> = _family
    private var pollJob: Job? = null
    private var searchJob: Job? = null
    private var liveJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                val health = io { PocketClient(store.baseUrl, "").health() }
                val percent = health.optDouble("chargePercent", 3.0)
                if (percent in 0.0..20.0) _chargePercent.value = percent
            }
        }
        if (_parent.value != null) {
            refresh()
            startLive()
        }
    }

    fun setLang(code: String) {
        val next = when (code) {
            "sw", "rw" -> "sw"
            "fr" -> "fr"
            else -> "en"
        }
        store.lang = next
        _lang.value = next
    }

    fun confirmLang(code: String) {
        setLang(code)
        store.langChosen = true
        _langChosen.value = true
    }

    fun clearMessages() {
        _error.value = ""
        _notice.value = ""
    }

    fun dismissFound() {
        _found.value = null
    }

    fun open(destination: Destination) {
        clearMessages()
        if (destination !is Destination.Pay) {
            pollJob?.cancel()
            _payMessage.value = ""
        }
        _destination.value = destination
        if (destination is Destination.Activity) loadLedger(destination.studentId)
        if (destination is Destination.Home) refresh()
    }

    fun login(phone: String, pin: String) = auth { client().login(phone, pin) }

    fun dismissFamily() {
        _family.value = null
    }

    fun startRegister(name: String, phone: String, pin: String) =
        auth(family = true) { client().registerStart(name, phone, pin, "") }

    fun confirmRegister(phone: String, code: String) = auth(family = true) { client().registerConfirm(phone, code) }
    fun confirmReset(phone: String, code: String, pin: String) = auth { client().recoverConfirm(phone, code, pin) }

    fun sendReset(phone: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = ""
            _notice.value = ""
            try {
                val json = io { client().recover(phone, SmsOtp.appHash(getApplication())) }
                _notice.value = if (json.optString("reason") == "code_sent") {
                    appCopy(_lang.value).codeOnTheWay
                } else {
                    json.optString("message", appCopy(_lang.value).codeOnTheWay)
                }
            } catch (error: PocketException) {
                _error.value = explain(error, "The code could not be sent.")
            } finally {
                _busy.value = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _busy.value = true
            _error.value = ""
            try {
                refreshAwait()
            } catch (error: PocketException) {
                if (error.message?.contains("Sign in") == true) signOutLocal() else _error.value = error.message ?: "Could not load wallets."
            } finally {
                _busy.value = false
            }
        }
    }

    fun startLive() {
        if (liveJob?.isActive == true) return
        liveJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (_parent.value == null || _sending.value) continue
                try {
                    refreshAwait()
                    val screen = _destination.value
                    if (screen is Destination.Activity) {
                        val json = io { client().ledger(screen.studentId) }
                        _ledger.value = json.getJSONArray("transactions").objects().map { it.toLedger() }
                    }
                } catch (error: PocketException) {
                    if (error.message?.contains("Sign in") == true) signOutLocal()
                }
            }
        }
    }

    fun search(regno: String) {
        val clean = regno.filter { it.isDigit() }
        searchJob?.cancel()
        if (clean.length < 4) {
            _found.value = null
            _matches.value = emptyList()
            _searching.value = false
            _error.value = ""
            return
        }
        searchJob = viewModelScope.launch {
            delay(160)
            _searching.value = true
            try {
                val json = io { client().lookup(clean) }
                val student = json.optJSONObject("student")?.toStudent()
                val list = json.optJSONArray("matches")?.objects()?.map { it.toStudent() }.orEmpty()
                _found.value = student
                _matches.value = if (student != null) listOf(student) else list
                if (student != null || list.isNotEmpty() || clean.length < 9) _error.value = ""
                if (student == null && list.isEmpty() && clean.length >= 9) _error.value = "No student uses that registration number."
            } catch (error: PocketException) {
                if (error.message?.contains("Sign in") == true) signOutLocal()
            } finally {
                _searching.value = false
            }
        }
    }

    fun choose(student: StudentWallet) {
        _found.value = student
        _matches.value = listOf(student)
        _error.value = ""
    }

    fun lookup(regno: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = ""
            _found.value = null
            try {
                _found.value = io { client().lookup(regno).getJSONObject("student").toStudent() }
            } catch (error: PocketException) {
                _error.value = error.message ?: "Student not found."
            } finally {
                _busy.value = false
            }
        }
    }

    fun link(studentId: Int, spendPin: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = ""
            try {
                val student = io { client().link(studentId, spendPin).getJSONObject("student").toStudent() }
                _found.value = null
                _notice.value = "${student.name} is on your account. Money you send stays on their card."
                refreshAwait()
                onDone()
            } catch (error: PocketException) {
                _error.value = error.message ?: "Could not add the student."
            } finally {
                _busy.value = false
            }
        }
    }

    fun unlink(studentId: Int) {
        viewModelScope.launch {
            _busy.value = true
            try {
                io { client().unlink(studentId) }
                _notice.value = "Student removed from your account."
                refreshAwait()
                _destination.value = Destination.Home
            } catch (error: PocketException) {
                _error.value = error.message ?: "Could not remove the student."
            } finally {
                _busy.value = false
            }
        }
    }

    fun changePin(studentId: Int, pin: String, oldPin: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = ""
            try {
                val json = io { client().spendPin(studentId, pin, oldPin) }
                _notice.value = json.optString("message", "Spend PIN saved.")
                refreshAwait()
            } catch (error: PocketException) {
                _error.value = error.message ?: "Could not save the PIN."
            } finally {
                _busy.value = false
            }
        }
    }

    fun remember(student: StudentWallet) {
        if (!student.cardLinked) return
        if (_students.value.any { it.id == student.id }) return
        viewModelScope.launch {
            try {
                val saved = io { client().link(student.id, "").getJSONObject("student").toStudent() }
                _students.value = listOf(saved) + _students.value.filter { it.id != saved.id }
                if (_found.value?.id == saved.id) _found.value = saved
            } catch (error: PocketException) {
                if (error.message?.contains("Sign in") == true) signOutLocal()
            }
        }
    }

    fun pay(studentId: Int, amount: Int, phone: String, mno: String) {
        val student = _students.value.firstOrNull { it.id == studentId } ?: _found.value
        if (student?.cardDisabled == true) {
            _error.value = "This card is disabled."
            return
        }
        pollJob?.cancel()
        viewModelScope.launch {
            _busy.value = true
            _sending.value = true
            _error.value = ""
            _payMessage.value = "Sending money to the wallet…"
            try {
                io { client().link(studentId, "") }
                val started = io { client().topup(studentId, amount, phone, mno) }
                val paymentId = started.optInt("paymentId")
                val receive = started.optInt("amount", amount)
                val transferFee = started.optInt("transferFee", 0)
                val withdrawFee = started.optInt("withdrawFee", 0)
                val total = started.optInt("total", receive + transferFee + withdrawFee)
                val prompt = appCopy(store.lang).feeLine(receive, transferFee, withdrawFee, total)
                _payMessage.value = prompt
                awaitPayment(paymentId, prompt)
            } catch (error: PocketException) {
                _error.value = error.message ?: "Payment failed."
                _payMessage.value = ""
            } finally {
                _busy.value = false
                _sending.value = false
            }
        }
    }

    fun loadLedger(studentId: Int) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val json = io { client().ledger(studentId) }
                _ledger.value = json.getJSONArray("transactions").objects().map { it.toLedger() }
                val student = json.getJSONObject("student").toStudent()
                _students.value = _students.value.map { if (it.id == student.id) student else it }
            } catch (error: PocketException) {
                _error.value = error.message ?: "Could not load activity."
            } finally {
                _busy.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            io { runCatching { client().logout() } }
            signOutLocal()
        }
    }

    private suspend fun awaitPayment(paymentId: Int, prompt: String) {
        repeat(45) {
            delay(3000)
            try {
                val status = io { client().topupStatus(paymentId) }
                when (status.optString("status")) {
                    "paid" -> {
                        refreshAwait()
                        _found.value = null
                        _matches.value = emptyList()
                        _resetSearch.value = _resetSearch.value + 1
                        _notice.value = appCopy(store.lang).paidNotice
                        _payMessage.value = ""
                        _destination.value = Destination.Home
                        return
                    }
                    "failed" -> {
                        _error.value = status.optString("message", "The payment was not approved.")
                        _payMessage.value = ""
                        return
                    }
                    else -> _payMessage.value = prompt
                }
            } catch (error: PocketException) {
                _payMessage.value = "Still contacting the school server…"
            }
        }
        _payMessage.value = ""
        _error.value = "Still waiting for Mobile Money. If you approved it, the card balance will update in the student list."
        _destination.value = Destination.Home
        _resetSearch.value = _resetSearch.value + 1
        refreshAwait()
    }

    private fun auth(family: Boolean = false, call: () -> JSONObject) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = ""
            try {
                val json = io { call() }
                val parentJson = json.getJSONObject("parent")
                val parent = ParentAccount(
                    parentJson.optInt("id"),
                    parentJson.optString("name"),
                    parentJson.optString("phone"),
                    parentJson.optString("role", "parent"),
                )
                store.token = json.optString("token")
                store.saveParent(parent)
                _parent.value = parent
                if (family) {
                    val kids = json.optJSONArray("students")?.objects()?.map { it.toStudent() }.orEmpty()
                    _family.value = if (kids.isEmpty()) {
                        null
                    } else {
                        FamilyWelcome(
                            registeredAs = json.optString("registeredAs"),
                            relation = json.optString("relation"),
                            students = kids,
                        )
                    }
                    if (kids.isEmpty()) _notice.value = appCopy(_lang.value).addByReg
                    _signupCount.value = 0
                }
                refreshAwait()
                _destination.value = Destination.Home
                startLive()
            } catch (error: PocketException) {
                if (_parent.value != null) _destination.value = Destination.Home
                _error.value = explain(error, "Sign in failed.")
            } finally {
                _busy.value = false
            }
        }
    }

    private fun explain(error: PocketException, fallback: String): String {
        val copy = appCopy(_lang.value)
        return when (error.reason) {
            "unknown_phone" -> copy.noAccount
            "wrong_pin" -> copy.wrongPin
            else -> error.message ?: fallback
        }
    }

    private suspend fun refreshAwait() {
        val json = io { client().wallets() }
        val fresh = json.getJSONArray("students").objects().map { it.toStudent() }
        val keepId = when (val screen = _destination.value) {
            is Destination.Pay -> screen.studentId
            is Destination.Activity -> screen.studentId
            else -> 0
        }
        if (keepId > 0 && fresh.none { it.id == keepId }) {
            _destination.value = Destination.Home
        }
        _students.value = fresh
    }

    private fun loadLedgerQuiet(studentId: Int) {
        viewModelScope.launch {
            runCatching {
                val json = io { client().ledger(studentId) }
                _ledger.value = json.getJSONArray("transactions").objects().map { it.toLedger() }
            }
        }
    }

    private fun signOutLocal() {
        pollJob?.cancel()
        liveJob?.cancel()
        store.clear()
        _parent.value = null
        _students.value = emptyList()
        _destination.value = Destination.Auth
    }

    private fun client() = PocketClient(store.baseUrl, store.token)

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
