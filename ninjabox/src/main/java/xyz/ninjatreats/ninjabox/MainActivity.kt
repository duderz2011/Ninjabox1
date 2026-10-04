package xyz.ninjatreats.ninjabox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val NinjaBlack = Color(0xFF09090B)
private val NinjaPanel = Color(0xFF17171B)
private val NinjaPanel2 = Color(0xFF232329)
private val NinjaRed = Color(0xFFE50914)
private val NinjaWhite = Color(0xFFF7F7F7)
private val NinjaMuted = Color(0xFFB4B4BC)

private val NinjaColors = darkColorScheme(
    primary = NinjaRed,
    onPrimary = Color.White,
    background = NinjaBlack,
    onBackground = NinjaWhite,
    surface = NinjaPanel,
    onSurface = NinjaWhite,
    surfaceVariant = NinjaPanel2,
    onSurfaceVariant = NinjaMuted,
)

@Composable
private fun NinjaBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NinjaColors, content = content)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NinjaBoxTheme {
                Surface(Modifier.fillMaxSize(), color = NinjaBlack) {
                    NinjaBoxRoot()
                }
            }
        }
    }
}

private enum class GateState { Checking, Login, App }

@Composable
private fun NinjaBoxRoot() {
    val context = LocalContext.current
    val auth = remember { AuthStore(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(GateState.Checking) }
    var loginError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        state = if (NinjaApi.validate(auth.token, auth.deviceId)) GateState.App else GateState.Login
        if (state == GateState.Login && auth.token.isNotBlank()) auth.clearSession()
    }

    when (state) {
        GateState.Checking -> BrandLoading()
        GateState.Login -> LoginScreen(
            initialUsername = auth.username,
            error = loginError,
            onLogin = { username, password ->
                scope.launch {
                    loginError = null
                    NinjaApi.login(username.trim(), password, auth.deviceId)
                        .onSuccess { token ->
                            auth.saveSession(token, username.trim())
                            state = GateState.App
                        }
                        .onFailure { loginError = it.message ?: "Unable to sign in" }
                }
            }
        )
        GateState.App -> AppShell(
            username = auth.username,
            token = auth.token,
            deviceId = auth.deviceId,
            onLogout = {
                scope.launch {
                    NinjaApi.logout(auth.token, auth.deviceId)
                    auth.clearSession()
                    state = GateState.Login
                }
            }
        )
    }
}

@Composable
private fun BrandLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.ninja_logo),
                contentDescription = "NinjaBox",
                modifier = Modifier.size(210.dp).clip(RoundedCornerShape(26.dp))
            )
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(color = NinjaRed)
        }
    }
}

@Composable
private fun LoginScreen(
    initialUsername: String,
    error: String?,
    onLogin: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loginFocused by remember { mutableStateOf(false) }

    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val loginFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun submitLogin() {
        if (busy || username.isBlank() || password.isBlank()) return
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        busy = true
        onLogin(username, password)
    }

    fun focusLoginButton() {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        loginFocus.requestFocus()
    }

    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            busy = false
            passwordFocus.requestFocus()
        }
    }

    Box(
        Modifier.fillMaxSize().background(NinjaBlack).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NinjaPanel),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.ninja_logo),
                    contentDescription = "NinjaBox",
                    modifier = Modifier.size(180.dp).clip(RoundedCornerShape(22.dp))
                )
                Spacer(Modifier.height(12.dp))
                Text("NinjaBox", fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text("Sign in to continue", color = NinjaMuted)
                Spacer(Modifier.height(26.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { passwordFocus.requestFocus() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(usernameFocus)
                        .focusProperties {
                            down = passwordFocus
                            next = passwordFocus
                        }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { submitLogin() },
                        onNext = { submitLogin() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocus)
                        .focusProperties {
                            up = usernameFocus
                            down = loginFocus
                            next = loginFocus
                        }
                )
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(error, color = Color(0xFFFF8A8A))
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { submitLogin() },
                    enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .focusRequester(loginFocus)
                        .focusProperties { up = passwordFocus }
                        .onFocusChanged { loginFocused = it.isFocused }
                        .border(
                            width = if (loginFocused) 3.dp else 0.dp,
                            color = NinjaWhite,
                            shape = RoundedCornerShape(28.dp)
                        )
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("OPEN NINJABOX", fontWeight = FontWeight.Bold)
                    }
                }

                if (!busy && username.isNotBlank() && password.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { focusLoginButton() }) {
                        Text("Fire TV: press Next/Done to sign in", color = NinjaMuted)
                    }
                }
            }
        }
    }
}

private enum class AppTab(val label: String, val mark: String) {
    Home("Home", "⌂"),
    Movies("Movies", "M"),
    Tv("TV Shows", "TV"),
    MyList("My List", "★"),
    Search("Search", "⌕"),
}

@Composable
private fun AppShell(
    username: String,
    token: String,
    deviceId: String,
    onLogout: () -> Unit,
) {
    var tab by remember { mutableStateOf(AppTab.Home) }
    var detail by remember { mutableStateOf<CatalogItem?>(null) }
    val wide = LocalConfiguration.current.screenWidthDp >= 720

    BackHandler(enabled = detail != null) { detail = null }

    Column(Modifier.fillMaxSize().background(NinjaBlack)) {
        TopBrandBar(username, onLogout)
        if (detail != null) {
            DetailScreen(
                initial = detail!!,
                token = token,
                deviceId = deviceId,
                onBack = { detail = null }
            )
        } else if (wide) {
            Row(Modifier.fillMaxSize()) {
                NinjaRail(tab, { tab = it })
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    TabContent(tab, onOpen = { detail = it })
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    TabContent(tab, onOpen = { detail = it })
                }
                NinjaBottomBar(tab, { tab = it })
            }
        }
    }
}

@Composable
private fun TopBrandBar(username: String, onLogout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(66.dp).background(Color(0xFF101014)).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ninja_logo),
            contentDescription = null,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(12.dp))
        Text("Ninja", fontSize = 24.sp, fontWeight = FontWeight.Black, color = NinjaWhite)
        Text("Box", fontSize = 24.sp, fontWeight = FontWeight.Black, color = NinjaRed)
        Spacer(Modifier.weight(1f))
        Text(username, color = NinjaMuted, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        TextButton(onClick = onLogout) { Text("Sign out", color = NinjaRed) }
    }
}

@Composable
private fun NinjaRail(selected: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationRail(containerColor = Color(0xFF101014)) {
        Spacer(Modifier.height(16.dp))
        AppTab.entries.forEach { tab ->
            NavigationRailItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Text(tab.mark, fontWeight = FontWeight.Bold) },
                label = { Text(tab.label) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = NinjaRed,
                    unselectedIconColor = NinjaMuted,
                    unselectedTextColor = NinjaMuted,
                )
            )
        }
    }
}

@Composable
private fun NinjaBottomBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationBar(containerColor = Color(0xFF101014)) {
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Text(tab.mark, fontWeight = FontWeight.Bold) },
                label = { Text(tab.label, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = NinjaRed,
                    unselectedIconColor = NinjaMuted,
                    unselectedTextColor = NinjaMuted,
                )
            )
        }
    }
}

@Composable
private fun TabContent(tab: AppTab, onOpen: (CatalogItem) -> Unit) {
    when (tab) {
        AppTab.Home -> HomeScreen(onOpen)
        AppTab.Movies -> MoviesScreen(onOpen)
        AppTab.Tv -> TvScreen(onOpen)
        AppTab.MyList -> MyListScreen(onOpen)
        AppTab.Search -> SearchScreen(onOpen)
    }
}

@Composable
private fun HomeScreen(onOpen: (CatalogItem) -> Unit) {
    var trending by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var movies by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var shows by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var topMovies by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var topTv by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            trending = TmdbApi.trendingAll()
            movies = TmdbApi.popularMovies()
            shows = TmdbApi.popularTv()
            topMovies = TmdbApi.topMovies()
            topTv = TmdbApi.topTv()
        }.onFailure { error = it.message }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(vertical = 12.dp),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        if (trending.isNotEmpty()) item { HeroCard(trending.first(), onOpen) }
        item { MediaRow("Trending Today", trending.drop(1), onOpen) }
        item { MediaRow("Popular Movies", movies, onOpen) }
        item { MediaRow("Popular TV Shows", shows, onOpen) }
        item { MediaRow("Top Movies", topMovies, onOpen) }
        item { MediaRow("Top TV Shows", topTv, onOpen) }
        if (!error.isNullOrBlank()) item { ErrorText(error!!) }
    }
}

@Composable
private fun MoviesScreen(onOpen: (CatalogItem) -> Unit) {
    var trending by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var latest by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var popular by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var top by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching {
            trending = TmdbApi.trendingMovies()
            latest = TmdbApi.nowPlaying()
            popular = TmdbApi.popularMovies()
            top = TmdbApi.topMovies()
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(top = 12.dp)) {
        item { SectionTitle("Movies") }
        item { MediaRow("Trending", trending, onOpen) }
        item { MediaRow("Latest Releases", latest, onOpen) }
        item { MediaRow("Popular", popular, onOpen) }
        item { MediaRow("Top Rated", top, onOpen) }
    }
}

@Composable
private fun TvScreen(onOpen: (CatalogItem) -> Unit) {
    var trending by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var today by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var popular by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var top by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching {
            trending = TmdbApi.trendingTv()
            today = TmdbApi.airingToday()
            popular = TmdbApi.popularTv()
            top = TmdbApi.topTv()
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(top = 12.dp)) {
        item { SectionTitle("TV Shows") }
        item { MediaRow("Trending", trending, onOpen) }
        item { MediaRow("Airing Today", today, onOpen) }
        item { MediaRow("Popular", popular, onOpen) }
        item { MediaRow("All-Time Favourites", top, onOpen) }
    }
}

@Composable
private fun MyListScreen(onOpen: (CatalogItem) -> Unit) {
    val context = LocalContext.current
    var list by remember { mutableStateOf(FavoriteStore(context).all()) }
    LaunchedEffect(Unit) { list = FavoriteStore(context).all() }
    if (list.isEmpty()) {
        EmptyState("My List is empty", "Add movies and TV shows from their details page.")
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(top = 12.dp)) {
            item { SectionTitle("My List") }
            item { MediaRow("Saved", list, onOpen) }
        }
    }
}

@Composable
private fun SearchScreen(onOpen: (CatalogItem) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runSearch() {
        if (query.isBlank() || busy) return
        busy = true
        error = null
        scope.launch {
            runCatching { TmdbApi.search(query.trim()) }
                .onSuccess { results = it }
                .onFailure { error = it.message }
            busy = false
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(top = 12.dp)) {
        item {
            SectionTitle("Search")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search movies & TV shows") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = { runSearch() }, enabled = !busy && query.isNotBlank()) {
                    Text(if (busy) "Searching…" else "Search")
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (!error.isNullOrBlank()) item { ErrorText(error!!) }
        item { MediaRow("Results", results, onOpen) }
    }
}

@Composable
private fun HeroCard(item: CatalogItem, onOpen: (CatalogItem) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Card(
        onClick = { onOpen(item) },
        interactionSource = interaction,
        colors = CardDefaults.cardColors(containerColor = NinjaPanel),
        modifier = Modifier
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .fillMaxWidth()
            .heightIn(min = 250.dp)
            .border(if (focused) 3.dp else 0.dp, NinjaRed, RoundedCornerShape(18.dp))
            .focusable(interactionSource = interaction),
        shape = RoundedCornerShape(18.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.backdrop ?: item.poster,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(Color(0xEE09090B), Color(0x7709090B), Color.Transparent)
                    )
                )
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(24.dp).fillMaxWidth(0.65f)
            ) {
                Text(item.title, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                val meta = mutableListOf<String>()
                if (item.year.isNotBlank()) meta.add(item.year)
                if (item.rating > 0) meta.add("★ " + "%.1f".format(item.rating))
                Text(meta.joinToString("   "), color = NinjaMuted)
                if (item.overview.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(item.overview, maxLines = 3, color = Color(0xFFD7D7DC))
                }
            }
        }
    }
}

@Composable
private fun MediaRow(title: String, list: List<CatalogItem>, onOpen: (CatalogItem) -> Unit) {
    if (list.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(list, key = { it.type + ":" + it.id.toString() + ":" + title }) { item ->
                PosterCard(item, onOpen)
            }
        }
    }
}

@Composable
private fun PosterCard(item: CatalogItem, onOpen: (CatalogItem) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Card(
        onClick = { onOpen(item) },
        interactionSource = interaction,
        modifier = Modifier
            .width(145.dp)
            .border(if (focused) 3.dp else 0.dp, NinjaRed, RoundedCornerShape(12.dp))
            .focusable(interactionSource = interaction),
        colors = CardDefaults.cardColors(containerColor = NinjaPanel),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            AsyncImage(
                model = item.poster ?: item.backdrop,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)
            )
            Text(
                item.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                modifier = Modifier.padding(9.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        fontSize = 30.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
    )
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = NinjaMuted)
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = Color(0xFFFF8A8A), modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
}

@Composable
private fun DetailScreen(
    initial: CatalogItem,
    token: String,
    deviceId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val favorites = remember { FavoriteStore(context) }
    var detail by remember(initial.id, initial.type) { mutableStateOf<MediaDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var favorite by remember(initial.id, initial.type) { mutableStateOf(favorites.contains(initial)) }
    var selectedSeason by remember { mutableIntStateOf(0) }
    var episodes by remember { mutableStateOf<List<EpisodeInfo>>(emptyList()) }
    var sourceBusy by remember { mutableStateOf(false) }
    var streams by remember { mutableStateOf<List<PlayableStream>?>(null) }
    var playTitle by remember { mutableStateOf(initial.title) }

    LaunchedEffect(initial.id, initial.type) {
        runCatching { TmdbApi.detail(initial) }
            .onSuccess {
                detail = it
                selectedSeason = it.seasons.firstOrNull { s -> s.number > 0 }?.number
                    ?: it.seasons.firstOrNull()?.number ?: 0
            }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(selectedSeason, detail?.item?.id) {
        val d = detail ?: return@LaunchedEffect
        if (d.item.type == "tv" && selectedSeason >= 0) {
            episodes = runCatching { TmdbApi.episodes(d.item.id, selectedSeason) }.getOrDefault(emptyList())
        }
    }

    fun resolve(item: CatalogItem, season: Int = 0, episode: Int = 0, title: String = item.title) {
        if (sourceBusy) return
        sourceBusy = true
        playTitle = title
        scope.launch {
            NinjaApi.resolveStreams(token, deviceId, item, season, episode)
                .onSuccess {
                    if (it.isEmpty()) error = "No playable sources were returned."
                    else streams = it
                }
                .onFailure { error = it.message ?: "Unable to load sources" }
            sourceBusy = false
        }
    }

    if (streams != null) {
        SourceDialog(
            title = playTitle,
            streams = streams!!,
            onDismiss = { streams = null },
            onPlay = { stream ->
                streams = null
                launchPlayer(context, stream, playTitle)
            }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("← Back") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val item = detail?.item ?: initial
                    favorite = favorites.toggle(item)
                }) {
                    Text(if (favorite) "★ In My List" else "☆ Add to My List")
                }
            }
        }

        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NinjaRed)
                }
            }
        } else {
            detail?.let { d ->
                item {
                    Box(Modifier.fillMaxWidth().heightIn(min = 320.dp)) {
                        AsyncImage(
                            model = d.item.backdrop ?: d.item.poster,
                            contentDescription = d.item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(Color(0x44000000), NinjaBlack))
                            )
                        )
                        Row(
                            Modifier.align(Alignment.BottomStart).padding(24.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            AsyncImage(
                                model = d.item.poster,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.width(150.dp).aspectRatio(0.68f).clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.width(22.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.item.title, fontSize = 34.sp, fontWeight = FontWeight.Black)
                                val meta = mutableListOf<String>()
                                if (d.item.year.isNotBlank()) meta.add(d.item.year)
                                if (d.item.rating > 0) meta.add("★ " + "%.1f".format(d.item.rating))
                                if (d.genres.isNotEmpty()) meta.add(d.genres.joinToString(" • "))
                                Text(meta.joinToString("   "), color = NinjaMuted)
                                Spacer(Modifier.height(10.dp))
                                Text(d.item.overview, color = Color(0xFFE2E2E5))
                                if (d.cast.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("Cast: " + d.cast.joinToString(", "), color = NinjaMuted, maxLines = 2)
                                }
                                if (d.item.type == "movie") {
                                    Spacer(Modifier.height(16.dp))
                                    Button(onClick = { resolve(d.item) }, enabled = !sourceBusy) {
                                        Text(if (sourceBusy) "Finding sources…" else "▶ Watch")
                                    }
                                }
                            }
                        }
                    }
                }

                if (d.item.type == "tv") {
                    item {
                        Text(
                            "Seasons",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(d.seasons, key = { it.number }) { s ->
                                FilterChip(
                                    selected = selectedSeason == s.number,
                                    onClick = { selectedSeason = s.number },
                                    label = { Text(s.name) }
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    items(episodes, key = { it.number }) { ep ->
                        EpisodeCard(ep) {
                            val title = d.item.title + " — S" + selectedSeason + "E" + ep.number + " " + ep.name
                            resolve(d.item, selectedSeason, ep.number, title)
                        }
                    }
                }
            }
        }

        if (!error.isNullOrBlank()) item { ErrorText(error!!) }
    }
}

@Composable
private fun EpisodeCard(episode: EpisodeInfo, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .padding(horizontal = 18.dp, vertical = 7.dp)
            .fillMaxWidth()
            .border(if (focused) 3.dp else 0.dp, NinjaRed, RoundedCornerShape(14.dp))
            .focusable(interactionSource = interaction),
        colors = CardDefaults.cardColors(containerColor = NinjaPanel),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = episode.still,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(180.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(9.dp))
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("E" + episode.number + "  " + episode.name, fontWeight = FontWeight.Bold)
                if (episode.airDate.isNotBlank()) Text(episode.airDate, color = NinjaMuted)
                if (episode.overview.isNotBlank()) Text(episode.overview, maxLines = 2, color = Color(0xFFD7D7DC))
            }
            Text("▶", color = NinjaRed, fontSize = 22.sp)
        }
    }
}

@Composable
private fun SourceDialog(
    title: String,
    streams: List<PlayableStream>,
    onDismiss: () -> Unit,
    onPlay: (PlayableStream) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose source") },
        text = {
            Column {
                Text(title, color = NinjaMuted, maxLines = 2)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(streams) { stream ->
                        val interaction = remember { MutableInteractionSource() }
                        val focused by interaction.collectIsFocusedAsState()
                        Card(
                            onClick = { onPlay(stream) },
                            interactionSource = interaction,
                            colors = CardDefaults.cardColors(containerColor = NinjaPanel2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .border(if (focused) 2.dp else 0.dp, NinjaRed, RoundedCornerShape(10.dp))
                                .focusable(interactionSource = interaction)
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(stream.provider, fontWeight = FontWeight.Bold)
                                    if (stream.quality.isNotBlank()) Text(stream.quality, color = NinjaMuted)
                                }
                                Text("▶", color = NinjaRed)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun launchPlayer(
    context: android.content.Context,
    stream: PlayableStream,
    title: String,
) {
    val headers = JSONObject()
    stream.headers.forEach { pair -> headers.put(pair.key, pair.value) }

    val subtitles = JSONArray()
    stream.subtitles.forEach { s ->
        subtitles.put(JSONObject().put("label", s.label).put("url", s.url))
    }

    context.startActivity(
        Intent(context, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_URL, stream.url)
            .putExtra(PlayerActivity.EXTRA_TITLE, title)
            .putExtra(PlayerActivity.EXTRA_HEADERS, headers.toString())
            .putExtra(PlayerActivity.EXTRA_SUBTITLES, subtitles.toString())
    )
}
