package com.cineflix.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cineflix.tv.ui.CineFlixViewModel
import com.cineflix.tv.ui.components.TvDestination
import com.cineflix.tv.ui.components.TvNavigationRail
import com.cineflix.tv.ui.screens.BrowseScreen
import com.cineflix.tv.ui.screens.DetailsScreen
import com.cineflix.tv.ui.screens.HomeScreen
import com.cineflix.tv.ui.screens.LibraryScreen
import com.cineflix.tv.ui.screens.PlayerScreen
import com.cineflix.tv.ui.screens.SearchScreen
import com.cineflix.tv.ui.screens.SettingsScreen
import com.cineflix.tv.ui.screens.TvApiSetupGuideScreen
import com.cineflix.tv.ui.theme.CineFlixColors
import com.cineflix.tv.ui.theme.CineFlixTvTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CineFlixViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CineFlixTvTheme {
                val prefs by viewModel.prefs.collectAsStateWithLifecycle()
                val destination by viewModel.destination.collectAsStateWithLifecycle()
                val genreMap by viewModel.genreMap.collectAsStateWithLifecycle()
                val movieGenres by viewModel.movieGenres.collectAsStateWithLifecycle()
                val tvGenres by viewModel.tvGenres.collectAsStateWithLifecycle()
                val homeCatalog by viewModel.homeCatalog.collectAsStateWithLifecycle()
                val browseState by viewModel.browseState.collectAsStateWithLifecycle()
                val searchState by viewModel.searchState.collectAsStateWithLifecycle()
                val detailsStack by viewModel.detailsStack.collectAsStateWithLifecycle()
                val activePlayer by viewModel.activePlayer.collectAsStateWithLifecycle()

                val isVerifyingKey by viewModel.isVerifyingKey.collectAsStateWithLifecycle()
                val keyError by viewModel.keyError.collectAsStateWithLifecycle()
                val keyFeedback by viewModel.keyFeedback.collectAsStateWithLifecycle()
                val keyVerifiedFlag by viewModel.keyVerifiedFlag.collectAsStateWithLifecycle()
                val cloudAuthLoading by viewModel.cloudAuthLoading.collectAsStateWithLifecycle()
                val cloudAuthError by viewModel.cloudAuthError.collectAsStateWithLifecycle()

                BackHandler(enabled = activePlayer != null || detailsStack.isNotEmpty() || destination != TvDestination.HOME) {
                    when {
                        activePlayer != null -> viewModel.closePlayer()
                        viewModel.popDetails() -> Unit
                        destination != TvDestination.HOME -> viewModel.navigateTo(TvDestination.HOME)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CineFlixColors.ObsidianBg)
                ) {
                    when {
                        // Fullscreen Hybrid Video Player (Media3 ExoPlayer + Hybrid TV WebView)
                        activePlayer != null -> {
                            val session = activePlayer!!
                            PlayerScreen(
                                session = session,
                                autoFailover = prefs.autoFailover,
                                onSelectServer = { serverId -> viewModel.selectPlayerServer(serverId) },
                                onSelectEpisode = { s, e -> viewModel.selectPlayerEpisode(s, e) },
                                onUpdateCustomUrl = { url -> viewModel.updatePlayerCustomUrl(url) },
                                onRecordProgress = { posSec, durSec ->
                                    viewModel.recordWatchProgress(
                                        item = session.item,
                                        mediaType = session.mediaType,
                                        season = session.seasonNumber,
                                        episode = session.episodeNumber,
                                        progressSec = posSec,
                                        durationSec = durSec
                                    )
                                },
                                onClose = { viewModel.closePlayer() }
                            )
                        }

                        // First-Launch TMDB BYOK Setup Gate (when no TMDB key is configured yet)
                        prefs.tmdbCredential.isBlank() -> {
                            TvApiSetupGuideScreen(
                                initialCredential = prefs.tmdbCredential,
                                companionUrl = viewModel.companionUrl,
                                isVerifying = isVerifyingKey,
                                errorMessage = keyError,
                                verifiedSuccess = keyVerifiedFlag,
                                onVerifyAndSave = { key -> viewModel.verifyAndSaveTmdbCredential(key) }
                            )
                        }

                        // Title Details Screen (with Season/Episode selector, Cast, Related)
                        detailsStack.isNotEmpty() -> {
                            val topDetails = detailsStack.last()
                            DetailsScreen(
                                state = topDetails,
                                preferredServerId = prefs.preferredServerId,
                                genreAffinity = prefs.genreAffinity,
                                isInWatchlist = viewModel.isInWatchlist(topDetails.mediaId, topDetails.mediaType),
                                onSelectSeason = { seasonNum -> viewModel.selectDetailsSeason(seasonNum) },
                                onWatchNow = { item, season, ep ->
                                    viewModel.startPlayback(item, season, ep)
                                },
                                onPlayTrailer = { item, ytKey ->
                                    viewModel.startPlayback(
                                        item = item,
                                        season = 1,
                                        episode = 1,
                                        forceServerId = "tmdb_youtube",
                                        explicitYoutubeKey = ytKey
                                    )
                                },
                                onToggleWatchlist = { item -> viewModel.toggleWatchlist(item) },
                                onCycleServer = {
                                    val next = com.cineflix.tv.data.model.CineFlixServers.nextServer(prefs.preferredServerId)
                                    viewModel.setPreferredServer(next.id)
                                },
                                onOpenRelatedTitle = { rel -> viewModel.openDetails(rel) },
                                onBack = { viewModel.popDetails() }
                            )
                        }

                        // Primary 10-Foot Google TV Shell (Left TV Navigation Rail + Active Screen)
                        else -> {
                            Row(modifier = Modifier.fillMaxSize()) {
                                TvNavigationRail(
                                    currentDestination = destination,
                                    watchlistCount = prefs.watchlist.size,
                                    onNavigate = { dest -> viewModel.navigateTo(dest) }
                                )

                                Box(modifier = Modifier.weight(1f)) {
                                    when (destination) {
                                        TvDestination.HOME -> {
                                            HomeScreen(
                                                catalog = homeCatalog,
                                                history = prefs.history,
                                                genreMap = genreMap,
                                                genreAffinity = prefs.genreAffinity,
                                                isInWatchlist = { id, type -> viewModel.isInWatchlist(id, type) },
                                                onWatchNow = { item, s, e -> viewModel.startPlayback(item, s, e) },
                                                onPlayTrailer = { item ->
                                                    viewModel.startPlayback(
                                                        item = item,
                                                        forceServerId = "tmdb_youtube"
                                                    )
                                                },
                                                onOpenDetails = { item -> viewModel.openDetails(item) },
                                                onToggleWatchlist = { item -> viewModel.toggleWatchlist(item) },
                                                onRetry = { viewModel.loadHomeCatalog() },
                                                onOpenSettings = { viewModel.navigateTo(TvDestination.SETTINGS) }
                                            )
                                        }

                                        TvDestination.MOVIES, TvDestination.TV, TvDestination.TRENDING -> {
                                            val activeGenres = if (destination == TvDestination.TV) {
                                                tvGenres
                                            } else movieGenres

                                            BrowseScreen(
                                                state = browseState,
                                                genres = activeGenres,
                                                onSelectGenre = { gid -> viewModel.selectBrowseGenre(gid) },
                                                onSelectSort = { sort -> viewModel.selectBrowseSort(sort) },
                                                onLoadMore = { viewModel.loadMoreBrowse() },
                                                onRetry = { viewModel.retryBrowse() },
                                                onOpenSettings = { viewModel.navigateTo(TvDestination.SETTINGS) },
                                                onOpenDetails = { item -> viewModel.openDetails(item) }
                                            )
                                        }

                                        TvDestination.MY_LIST -> {
                                            LibraryScreen(
                                                watchlist = prefs.watchlist,
                                                history = prefs.history,
                                                cloudAccount = prefs.cloudAccount,
                                                onOpenDetails = { item -> viewModel.openDetails(item) },
                                                onResumeHistory = { hist ->
                                                    viewModel.startPlayback(
                                                        item = hist.toMediaItem(),
                                                        season = hist.seasonNumber,
                                                        episode = hist.episodeNumber
                                                    )
                                                },
                                                onRemoveWatchlist = { entry -> viewModel.removeFromWatchlist(entry) },
                                                onClearHistory = { viewModel.clearHistory() },
                                                onOpenSettings = { viewModel.navigateTo(TvDestination.SETTINGS) }
                                            )
                                        }

                                        TvDestination.SEARCH -> {
                                            SearchScreen(
                                                state = searchState,
                                                onQueryChange = { q -> viewModel.updateSearchQuery(q) },
                                                onTypeFilterChange = { t -> viewModel.updateSearchTypeFilter(t) },
                                                onSortByChange = { s -> viewModel.updateSearchSortBy(s) },
                                                onLoadMore = { viewModel.loadMoreSearch() },
                                                onRetry = { viewModel.loadSearchSuggestions() },
                                                onOpenSettings = { viewModel.navigateTo(TvDestination.SETTINGS) },
                                                onOpenDetails = { item -> viewModel.openDetails(item) }
                                            )
                                        }

                                        TvDestination.SETTINGS -> {
                                            SettingsScreen(
                                                prefs = prefs,
                                                companionUrl = viewModel.companionUrl,
                                                isVerifyingKey = isVerifyingKey,
                                                keyFeedback = keyFeedback ?: keyError,
                                                cloudAuthFeedback = cloudAuthError,
                                                isCloudAuthLoading = cloudAuthLoading,
                                                onSaveAndVerifyTmdbKey = { key ->
                                                    viewModel.verifyAndSaveTmdbCredential(key)
                                                },
                                                onSelectPreferredServer = { id ->
                                                    viewModel.setPreferredServer(id)
                                                },
                                                onToggleAutoFailover = { enabled ->
                                                    viewModel.setAutoFailover(enabled)
                                                },
                                                onToggleAutoplayTrailers = { enabled ->
                                                    viewModel.setAutoplayTrailers(enabled)
                                                },
                                                onSaveCustomStreamUrl = { url ->
                                                    viewModel.saveCustomStreamUrl(url)
                                                },
                                                onCloudSignIn = { email, pass ->
                                                    viewModel.signInCloud(email, pass)
                                                },
                                                onCloudSignUp = { email, pass ->
                                                    viewModel.signUpCloud(email, pass)
                                                },
                                                onCloudSignOut = { viewModel.signOutCloud() }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
