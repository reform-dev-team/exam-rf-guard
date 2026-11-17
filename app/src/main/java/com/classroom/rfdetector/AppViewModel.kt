package com.classroom.rfdetector

import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.classroom.rfdetector.core.SessionRecovery
import com.classroom.rfdetector.core.SessionRecoveryStore
import android.provider.Settings
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.ViewModel
import com.classroom.rfdetector.core.DeviceIdentityResolver
import com.classroom.rfdetector.core.DeviceTracker
import com.classroom.rfdetector.core.DistanceClass
import com.classroom.rfdetector.core.GeigerController
import com.classroom.rfdetector.core.RfObservation
import com.classroom.rfdetector.core.ScanSession
import com.classroom.rfdetector.core.SignalPresence
import com.classroom.rfdetector.core.SignalSnapshot
import com.classroom.rfdetector.core.SilentMode
import com.classroom.rfdetector.core.SnapshotSignal
import com.classroom.rfdetector.core.SnapshotStore
import com.classroom.rfdetector.core.TrackedDevice
import com.classroom.rfdetector.core.WatchView
import com.classroom.rfdetector.core.baselineGroupCounts
import com.classroom.rfdetector.core.baselineSamples
import com.classroom.rfdetector.core.baselineWifiAddresses
import com.classroom.rfdetector.core.watchView
import com.classroom.rfdetector.scanner.BleScanner
import com.classroom.rfdetector.scanner.WifiScanner
import com.classroom.rfdetector.sim.RadioSimulator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.Executors

/** 감시가 완전하지 않은 이유. 해결 전까지는 해당 전파가 있어도 표시되지 않는다. */
enum class RadioProblem { BLUETOOTH_OFF, WIFI_OFF, LOCATION_OFF }

/** 빈 교실 기록 → 시험 중 감시의 상태를 화면 재생성과 무관하게 유지한다. 화면 밖에서는 수신을 멈춘다. */
class AppViewModel(private val app: Context) : ViewModel() {
    companion object {
        private val archiveDispatcher = Executors.newSingleThreadExecutor { task -> Thread(task, "rf-archive").apply { isDaemon = true } }.asCoroutineDispatcher()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storageScope = CoroutineScope(SupervisorJob() + archiveDispatcher)
    private var closed = false

    val tracker = DeviceTracker()
    val geiger = GeigerController(app)
    val ble = BleScanner(app)
    val wifi = WifiScanner(app, scope)
    /** 가상 전파. 화면에서는 켤 수 없고 자동 테스트에서만 쓴다. */
    val sim = RadioSimulator()
    /** 회사 코드·주소 앞자리로 제조사를 찾는 등록부. 앱에 들어 있어 인터넷 없이 조회한다. */
    private var identityResolver = DeviceIdentityResolver(emptyMap(), emptyMap())

    private val _devices = MutableStateFlow<List<TrackedDevice>>(emptyList())
    val devices: StateFlow<List<TrackedDevice>> = _devices
    val scanning = MutableStateFlow(false)
    val simMode = MutableStateFlow(false)
    val geigerOn = MutableStateFlow(false)
    val scanSession = MutableStateFlow<ScanSession?>(null)
    val healthTick = MutableStateFlow(0L)

    /** 지금 감시에 쓰는 빈 교실 기록. null이면 아직 측정 전이다. */
    val baselineSnapshot = MutableStateFlow<SignalSnapshot?>(null)
    val baselineSet = MutableStateFlow(false)
    /** 앱을 다시 열었을 때 이어 쓸 수 있는, 마지막으로 저장한 빈 교실 기록. */
    val savedBaseline = MutableStateFlow<SignalSnapshot?>(null)
    val snapshotBusy = MutableStateFlow(true)
    val snapshotError = MutableStateFlow<String?>(null)
    private val snapshotStore = SnapshotStore(app.filesDir)
    private val recoveryStore = SessionRecoveryStore(app.filesDir)
    val pendingRecovery = MutableStateFlow<SessionRecovery?>(null)
    val recoveryLoading = MutableStateFlow(true)
    val recoveryError = MutableStateFlow<String?>(null)
    internal val recoveryPendingWrites = MutableStateFlow(0)
    private var recoveryRevision = 0L
    private var lastCheckpointElapsedMs = 0L
    private var baselineGroups: Map<String, Int> = emptyMap()
    private var baselineWifi: Set<String> = emptySet()
    private var baselineSamples: List<SnapshotSignal> = emptyList()

    /** 빈 교실 기록과 비교해 지금 잡히는 전파를 묶음으로 나눈 결과. */
    val watch = MutableStateFlow(WatchView())
    val problems = MutableStateFlow<List<RadioProblem>>(emptyList())
    private var problemsCheckedAtMs = 0L
    /** 위치 찾기 대상(trackingKey). 지정하면 근접 진동이 이 기기를 따른다. */
    val findTarget = MutableStateFlow<String?>(null)
    /** 감독관이 "확인함"으로 표시한 묶음(groupKey). 이번 감시 동안만 유지한다. */
    val acknowledged = MutableStateFlow<Set<String>>(emptySet())
    private var pausedAtMs: Long? = null
    private val silent = SilentMode(app)
    /** 방해 금지 권한이 없어 휴대폰을 자동으로 무음으로 바꾸지 못하는 상태. */
    val silenceMissing = MutableStateFlow(!silent.allowed())
    /** 비행기 모드가 꺼져 있어 전화·문자가 들어올 수 있는 상태. 앱이 직접 켤 수는 없다. */
    val airplaneOff = MutableStateFlow(airplaneModeOff())

    private var simJob: Job? = null
    private var refreshJob: Job? = null

    init {
        scope.launch {
            identityResolver = withContext(Dispatchers.IO) {
                fun rows(file: String) = app.assets.open(file).bufferedReader().useLines { lines ->
                    lines.filter { it.contains('	') }.associate { line -> line.substringBefore('	') to line.substringAfter('	') }
                }
                DeviceIdentityResolver(rows("mac-vendors.tsv"), rows("bluetooth-companies.tsv").mapKeys { it.key.toInt() })
            }
        }
        loadRecovery()
        scope.launch {
            try {
                savedBaseline.value = withContext(archiveDispatcher) { snapshotStore.load() }.filter { !it.simulation }.maxByOrNull { it.capturedAtMs }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snapshotError.value = "저장된 빈 교실 기록을 읽지 못했습니다. 새로 측정해 주세요."
            } finally { snapshotBusy.value = false }
        }
    }

    private fun loadRecovery() {
        val revision = recoveryRevision
        scope.launch {
            try {
                val saved = withContext(archiveDispatcher) { recoveryStore.load() }
                if (revision == recoveryRevision) {
                    if (saved != null && !saved.canRestore(System.currentTimeMillis())) {
                        discardRecovery()
                    } else pendingRecovery.value = saved
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                recoveryError.value = "이전 감시를 읽지 못했습니다. 저장한 빈 교실 기록으로 새 감시를 시작할 수 있습니다."
            } finally { recoveryLoading.value = false }
        }
    }

    /** Writes are serialized with baseline storage; a stop tombstone follows earlier checkpoints. */
    private fun writeRecovery(record: SessionRecovery?) {
        recoveryRevision++
        recoveryPendingWrites.update { it + 1 }
        storageScope.launch {
            try {
                recoveryStore.save(record)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                recoveryError.value = "감시 복구 기록을 저장하지 못했습니다. 종료 시 최근 확인 처리가 유실되거나 이전 복원 기록이 남을 수 있습니다."
            } finally { recoveryPendingWrites.update { it - 1 } }
        }
    }

    private fun checkpointRecovery(interruptedAtMs: Long? = pausedAtMs) {
        if (simMode.value || (!scanning.value && pausedAtMs == null)) return
        val baseline = baselineSnapshot.value?.takeUnless { it.simulation } ?: return
        val session = scanSession.value ?: return
        lastCheckpointElapsedMs = SystemClock.elapsedRealtime()
        writeRecovery(SessionRecovery(session, baseline, acknowledged.value.toSet(), System.currentTimeMillis(), interruptedAtMs))
    }

    fun discardRecovery() {
        pendingRecovery.value = null
        writeRecovery(null)
    }

    /** Only an explicit confirmation may resume a checkpoint in a new process. */
    fun restoreSession(): Boolean {
        val recovery = pendingRecovery.value ?: return false
        if (scanning.value || closed || simMode.value) return false
        if (!recovery.canRestore(System.currentTimeMillis())) {
            discardRecovery()
            recoveryError.value = "이전 감시 기록의 시간이 유효하지 않거나 24시간이 지났습니다. 새 감시를 시작해 주세요."
            return false
        }
        checkExamMode()
        if (requiredPermissions().any { ContextCompat.checkSelfPermission(app, it) != PackageManager.PERMISSION_GRANTED } ||
            silenceMissing.value || problems.value.isNotEmpty() || (airplaneOff.value && !BuildConfig.DEBUG)) {
            recoveryError.value = "시작 전 준비를 마친 뒤 이전 감시를 복원해 주세요."
            return false
        }
        pendingRecovery.value = null
        begin(resumedPauseAtMs = null, recovery = recovery)
        return true
    }

    /** API 레벨별로 런타임 요청이 필요한 권한 목록 */
    fun requiredPermissions(): List<String> {
        val p = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            p += "android.permission.BLUETOOTH_SCAN"
            p += "android.permission.BLUETOOTH_CONNECT"
        }
        p += "android.permission.ACCESS_COARSE_LOCATION"
        p += "android.permission.ACCESS_FINE_LOCATION"
        if (Build.VERSION.SDK_INT >= 33) p += "android.permission.NEARBY_WIFI_DEVICES"
        return p
    }

    /** WifiManager 스캔 결과에는 위치 서비스가 필요하다. */
    fun locationServicesOff(): Boolean {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return !LocationManagerCompat.isLocationEnabled(lm)
    }

    fun startScan() {
        if (closed || scanning.value) return
        discardRecovery()
        begin(resumedPauseAtMs = null)
    }

    /** 앱이 화면 밖으로 나가면 수신을 멈추되, 돌아오면 같은 감시를 이어 간다. */
    fun pauseForBackground() {
        if (!scanning.value) return
        stopReceiving(keepSilent = true)
        pausedAtMs = System.currentTimeMillis()
        checkpointRecovery()
    }

    fun resumeFromBackground() {
        begin(pausedAtMs ?: return)
    }

    private fun begin(resumedPauseAtMs: Long?, recovery: SessionRecovery? = null) {
        if (scanning.value || closed) return
        pausedAtMs = null
        val paused = scanSession.value?.takeIf { resumedPauseAtMs != null }
        if (recovery != null) {
            tracker.clear()
            _devices.value = emptyList()
            watch.value = WatchView()
            findTarget.value = null
            baselineSnapshot.value = recovery.baseline
            acknowledged.value = recovery.acknowledged
            scanSession.value = recovery.restoredSession(System.currentTimeMillis())
        } else if (paused != null) {
            scanSession.value = paused.copy(pauses = paused.pauses + 1,
                pausedMs = paused.pausedMs + (System.currentTimeMillis() - resumedPauseAtMs!!).coerceAtLeast(0))
        } else {
            acknowledged.value = emptySet()
            tracker.clear()
            _devices.value = emptyList()
            watch.value = WatchView()
            scanSession.value = ScanSession(UUID.randomUUID().toString(), System.currentTimeMillis(), "시험실")
        }
        applyBaseline()
        scanning.value = true
        checkExamMode()
        checkpointRecovery()
        if (simMode.value) {
            if (paused == null) sim.reset()
            simJob = scope.launch {
                while (isActive) {
                    sim.tick().forEach { onObservation(it) }
                    delay(250)
                }
            }
        } else {
            if (ble.isUsable()) ble.start(::onObservation)
            wifi.start(::onObservation)
        }
        refreshJob = scope.launch {
            while (isActive) {
                refresh()
                delay(100)
            }
        }
    }

    private fun onObservation(obs: RfObservation) {
        if (scanning.value) tracker.update(obs)
    }

    private fun airplaneModeOff() = Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 0

    /** 비행기 모드, 방해 금지 권한, 블루투스·와이파이·위치 상태를 다시 확인한다. 권한을 방금 허용했다면 바로 무음으로 바꾼다. 가상 전파로 도는 자동 테스트에서는 휴대폰 설정을 건드리지 않는다. */
    fun checkExamMode() {
        silenceMissing.value = !silent.allowed()
        airplaneOff.value = airplaneModeOff()
        problems.value = radioProblems()
        if (scanning.value && !simMode.value) silent.on()
    }

    /** keepSilent: 앱을 잠시 벗어난 동안에도 시험 중이므로 무음을 유지한다. */
    fun stopScan(keepSilent: Boolean = false) {
        pausedAtMs = null
        stopReceiving(keepSilent)
        discardRecovery()
    }

    private fun stopReceiving(keepSilent: Boolean) {
        if (!scanning.value) {
            if (!keepSilent) silent.off()
            return
        }
        scanning.value = false
        refresh()
        simJob?.cancel(); simJob = null
        refreshJob?.cancel(); refreshJob = null
        ble.stop()
        wifi.stop()
        if (geigerOn.value) setGeiger(false)
        if (!keepSilent) silent.off()
    }

    private fun applyBaseline() {
        val baseline = baselineSnapshot.value?.takeIf { scanSession.value != null && it.simulation == simMode.value }
        baselineSet.value = baseline != null
        baselineGroups = baseline?.let(::baselineGroupCounts) ?: emptyMap()
        baselineWifi = baseline?.let(::baselineWifiAddresses) ?: emptySet()
        baselineSamples = baseline?.let { baselineSamples(it) } ?: emptyList()
    }

    /** 지금까지 잡힌 전파 전부를 이 교실에 원래 있던 것으로 저장하고 감시를 시작한다. */
    fun takeBaseline() {
        val session = scanSession.value ?: return
        if (!scanning.value || snapshotBusy.value || devices.value.isEmpty()) return
        refresh()
        val snapshot = SignalSnapshot.capture(session.area, System.currentTimeMillis(), simMode.value, devices.value)
        snapshotBusy.value = true
        snapshotError.value = null
        storageScope.launch {
            try {
                // 마지막 빈 교실 기록 하나만 보관한다.
                snapshotStore.save(listOf(snapshot))
                withContext(Dispatchers.Main.immediate) {
                    if (!snapshot.simulation) savedBaseline.value = snapshot
                    if (!closed && scanSession.value?.id == session.id) {
                        baselineSnapshot.value = snapshot
                        applyBaseline()
                        refresh()
                        checkpointRecovery()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snapshotError.value = "빈 교실 기록을 저장하지 못했습니다. 다시 눌러 주세요."
            } finally { snapshotBusy.value = false }
        }
    }

    /** 앱을 다시 연 뒤, 마지막으로 저장한 빈 교실 기록으로 바로 감시한다. */
    fun useSavedBaseline() {
        val saved = savedBaseline.value ?: return
        baselineSnapshot.value = saved
        applyBaseline()
    }

    /** 빈 교실 기록을 비운다. 다음 시작은 1단계 측정부터다. */
    fun resetBaseline() {
        discardRecovery()
        baselineSnapshot.value = null
        watch.value = WatchView()
        applyBaseline()
    }

    /** 빈 교실 기록과 목록을 비우고 새 측정을 바로 시작한다. */
    fun restartFromEmptyRoom() {
        stopScan()
        resetBaseline()
        startScan()
    }

    fun acknowledge(groupKey: String) {
        acknowledged.value += groupKey
        checkpointRecovery()
        refresh() // 다음 갱신 주기를 기다리지 않고 목록에서 바로 뺀다.
    }

    fun setGeiger(on: Boolean) {
        if (on && !scanning.value) return
        geigerOn.value = on
        if (on) geiger.start() else geiger.stop()
    }

    /** 자동 테스트용: 실제 무선 대신 가상 전파를 쓴다. */
    fun setSim(on: Boolean) {
        if (simMode.value == on) return
        if (scanning.value) stopScan()
        simMode.value = on
        tracker.clear()
        _devices.value = emptyList()
        scanSession.value = null
        resetBaseline()
    }

    fun simAddDevice() {
        if (scanning.value && simMode.value) sim.addDevice()
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        healthTick.value = now
        if (scanning.value && recoveryPendingWrites.value == 0 && SystemClock.elapsedRealtime() - lastCheckpointElapsedMs >= 15_000L) checkpointRecovery()
        val list = tracker.snapshot(now, includeInactive = true).map { d ->
            d.copy(identity = identityResolver.resolve(d.key, d.displayName, setOf(d.source), d.manufacturerIds))
        }
        _devices.value = list
        val view = if (baselineSet.value) watchView(list, baselineGroups, acknowledged.value, now, baselineWifi, baselineSamples) else WatchView()
        watch.value = view
        geiger.setClass(list.firstOrNull { it.trackingKey == findTarget.value && it.presence == SignalPresence.RECENT }?.distanceClass ?: DistanceClass.UNKNOWN)
        // 설정 상태 확인은 1초에 한 번이면 충분하다.
        if (now - problemsCheckedAtMs >= 1_000) {
            problemsCheckedAtMs = now
            checkExamMode()
            // 감시 도중에 블루투스를 켠 경우 바로 받기 시작한다. 오류로 멈춘 탐색은 자동으로 되풀이하지 않는다.
            if (scanning.value && !simMode.value && !ble.active && ble.lastError == null && ble.isUsable()) ble.start(::onObservation)
        }
    }

    private fun radioProblems(): List<RadioProblem> = if (simMode.value) emptyList() else listOfNotNull(
        RadioProblem.BLUETOOTH_OFF.takeIf { !ble.isUsable() },
        RadioProblem.WIFI_OFF.takeIf { !wifi.isEnabled() },
        RadioProblem.LOCATION_OFF.takeIf { locationServicesOff() },
    )

    fun shutdown() {
        if (closed) return
        if (scanning.value) checkpointRecovery(interruptedAtMs = System.currentTimeMillis())
        stopReceiving(keepSilent = false)
        closed = true
        scope.cancel()
    }

    override fun onCleared() { shutdown() }
}
