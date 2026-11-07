package me.vanpra.tally.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.vanpra.tally.ai.CloudSettings
import me.vanpra.tally.ai.ReaderState
import me.vanpra.tally.ai.SmartNoteReader
import me.vanpra.tally.course.Course
import me.vanpra.tally.course.OsmCourseParser
import me.vanpra.tally.platform.ImportFile
import me.vanpra.tally.platform.PlatformBackHandler
import me.vanpra.tally.platform.PlatformServices
import me.vanpra.tally.round.GolfshotImport
import me.vanpra.tally.round.RoundRepository
import me.vanpra.tally.round.formatToPar
import me.vanpra.tally.ui.components.ResumeBar
import me.vanpra.tally.ui.components.Tab
import me.vanpra.tally.ui.components.TabBar
import me.vanpra.tally.ui.courses.CoursesScreen
import me.vanpra.tally.ui.home.HomeScreen
import me.vanpra.tally.ui.home.RoundDetailScreen
import me.vanpra.tally.ui.profile.ProfileScreen
import me.vanpra.tally.ui.round.HoleDraft
import me.vanpra.tally.ui.round.RoundScreen
import me.vanpra.tally.ui.stats.StatsScreen
import me.vanpra.tally.ui.theme.Tally
import me.vanpra.tally.ui.theme.TallyTheme
import androidx.compose.runtime.CompositionLocalProvider
import me.vanpra.tally.round.BagStore
import me.vanpra.tally.round.tallyDatabase
import me.vanpra.tally.round.ClubDistances
import me.vanpra.tally.ui.round.LocalBagClubs
import tally.shared.generated.resources.Res

enum class ThemeChoice { DAY, DUSK, SYSTEM }

/**
 * Two worlds: the app (tabs, history, stats) and round mode (full screen, no tabs).
 * A live round can be minimised; a Resume bar then sits above the tabs until it's finished.
 */
@Composable
fun AppRoot(services: PlatformServices, systemDark: Boolean) {
    val scope = rememberCoroutineScope()
    val repo = remember { RoundRepository(services.database?.invoke()?.let(::tallyDatabase), scope, legacy = services.store) }
    val state by repo.state.collectAsStateWithLifecycle()

    var theme by remember { mutableStateOf(services.store.read(THEME_FILE)?.let { runCatching { ThemeChoice.valueOf(it.trim()) }.getOrNull() } ?: ThemeChoice.SYSTEM) }
    val dusk = when (theme) { ThemeChoice.DAY -> false; ThemeChoice.DUSK -> true; ThemeChoice.SYSTEM -> systemDark }

    // Notes are read online (Gemini) when allowed and there's signal, otherwise on the phone (Gemini Nano).
    val cloud = remember { CloudSettings(services.store) }
    val bags = remember { BagStore(services.store) }
    val bag by bags.bag.collectAsStateWithLifecycle()
    val cloudOn by cloud.enabled.collectAsStateWithLifecycle()
    val online by services.online.collectAsStateWithLifecycle()
    val reader = remember {
        val cloudReady = combine(cloud.enabled, services.online, services.cloudReader.state) { on, net, s -> on && net && s == ReaderState.Ready }
            .stateIn(scope, SharingStarted.Eagerly, false)
        SmartNoteReader(device = services.noteReader, cloud = services.cloudReader, cloudReady = cloudReady, scope = scope)
    }
    val readerState by services.noteReader.state.collectAsStateWithLifecycle()
    var course by remember { mutableStateOf<Course?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun import(files: List<ImportFile>) {
        message = when (val r = GolfshotImport.parse(files)) {
            is GolfshotImport.Result.Problem -> r.message
            is GolfshotImport.Result.Success -> {
                val added = repo.importRounds(r.rounds)
                when {
                    added == 0 -> "Those Golfshot rounds are already here."
                    else -> "Imported $added ${if (added == 1) "round" else "rounds"} from Golfshot."
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        services.noteReader.prepare()
        // Wanstead is bundled for now; course search and download come later.
        val json = Res.readBytes("files/wanstead_osm.json").decodeToString()
        course = withContext(Dispatchers.Default) { OsmCourseParser.parse(json, courseId = "osm-relation-3883387") }
        withContext(Dispatchers.Default) { services.takeImportFolderFiles() }.takeIf { it.isNotEmpty() }?.let(::import)
    }
    LaunchedEffect(message) { if (message != null) { delay(3500); message = null } }

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var roundOpen by rememberSaveable { mutableStateOf(state.activeRoundId != null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    val drafts = remember { mutableStateMapOf<Int, HoleDraft>() }
    val active = state.activeRound
    val history = state.history

    val tracked = remember(state.rounds, course) {
        val holes = course?.holes?.associateBy { it.number }.orEmpty()
        ClubDistances.of(state.rounds.filter { it.courseId == course?.id }, { holes }, minShots = 1)
    }

    TallyTheme(dusk = dusk) {
      CompositionLocalProvider(LocalBagClubs provides bag.clubs) {
        val c = Tally.colors
        Box(Modifier.fillMaxSize().background(c.bg)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    val detail = detailId?.let { id -> history.firstOrNull { it.id == id } }
                    when {
                        detail != null -> RoundDetailScreen(detail, onBack = { detailId = null }, onDelete = { repo.deleteRound(detail.id); detailId = null })
                        tab == Tab.HOME -> HomeScreen(
                            course = course,
                            history = history,
                            roundInProgress = active != null,
                            onStartRound = { course?.let { repo.startRound(it); roundOpen = true } },
                            onOpenRound = { detailId = it.id },
                            onImport = { tab = Tab.PROFILE },
                        )
                        tab == Tab.STATS -> StatsScreen(history, repo.db, course, tracked = state.rounds)
                        tab == Tab.COURSES -> CoursesScreen(course, history, active != null, onStartRound = { course?.let { repo.startRound(it); roundOpen = true } })
                        tab == Tab.PROFILE -> ProfileScreen(
                            theme = theme,
                            onTheme = { theme = it; services.store.write(THEME_FILE, it.name) },
                            history = history,
                            canPickFiles = services.pickFiles != null,
                            onImport = { services.pickFiles?.invoke { files -> import(files) } },
                            readerState = readerState,
                            cloudAvailable = services.cloudReader.state.value == ReaderState.Ready,
                            cloudOn = cloudOn,
                            online = online,
                            onCloud = cloud::setEnabled,
                            bag = bag,
                            tracked = tracked,
                            onToggleClub = bags::toggle,
                            onClubYards = bags::setYards,
                        )
                    }
                }
                if (active != null && detailId == null) {
                    ResumeBar(
                        title = "${active.courseName.removeSuffix(" Golf Club")} · Hole ${(active.done.maxOfOrNull { it.number } ?: 0) + 1}".let { if (active.done.size >= 18) "${active.courseName} · Card" else it },
                        subtitle = if (active.done.isEmpty()) "Round in progress" else "${formatToPar(active.toPar)} thru ${active.done.size}",
                        onResume = { roundOpen = true },
                    )
                }
                if (detailId == null) TabBar(tab) { tab = it }
            }

            PlatformBackHandler(enabled = !roundOpen && detailId == null && tab != Tab.HOME) { tab = Tab.HOME }

            val c2 = course
            AnimatedVisibility(
                visible = roundOpen && active != null && c2 != null,
                enter = slideInVertically(tween(260)) { it / 6 } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(220)) { it / 6 } + fadeOut(tween(180)),
            ) {
                if (active != null && c2 != null) {
                    RoundScreen(
                        course = c2,
                        round = active,
                        history = history,
                        location = services.location,
                        noteReader = reader,
                        voice = services.voice,
                        drafts = drafts,
                        onSaveHole = { repo.saveHole(active.id, it) },
                        onMarkShot = { n, mark -> repo.updateHole(active.id, n) { it.copy(shots = it.shots + mark) } },
                        onUndoMark = { n -> repo.updateHole(active.id, n) { it.copy(shots = it.shots.dropLast(1)) } },
                        onMinimise = { roundOpen = false },
                        onFinish = {
                            val id = active.id
                            val hadScores = active.played.isNotEmpty()
                            repo.finishRound(id)
                            drafts.clear()
                            roundOpen = false
                            tab = Tab.HOME
                            if (hadScores) detailId = id
                            message = if (hadScores) "Round saved." else "Round discarded: no holes were scored."
                        },
                        onDiscard = {
                            repo.deleteRound(active.id)
                            drafts.clear()
                            roundOpen = false
                            message = "Round discarded."
                        },
                    )
                }
            }

            message?.let { text ->
                Box(Modifier.fillMaxSize().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
                    Text(
                        text,
                        style = Tally.type.title.copy(color = c.bg),
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(c.ink).padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
      }
    }
}

private const val THEME_FILE = "theme.txt"
