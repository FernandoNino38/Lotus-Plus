using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.IO;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Threading;
using Lotus.Windows.Models;
using Lotus.Windows.Services;

namespace Lotus.Windows.ViewModels;

public class MainViewModel : INotifyPropertyChanged, IDisposable
{
    private readonly PersistenceService _persistence;
    private readonly MaterialYouColorService _colorService;
    private readonly MusicScannerService _scanner;
    private readonly MetadataService _metadataService;
    private readonly LrclibLyricsService _lrclibService;
    private readonly MusicBrainzMetadataService _musicBrainzService;
    private readonly AudioPlayerService _audioPlayer;

    private readonly Dictionary<string, int> _playCounts;
    private readonly Dictionary<string, CachedLyricsEntry> _lyricsCache;
    private readonly Random _random = new();
    private CancellationTokenSource? _lyricsCts;
    private CancellationTokenSource? _scanCts;

    private List<Track> _allTracks = new();
    private List<Playlist> _customPlaylists = new();

    private LibraryTab _selectedTab = LibraryTab.Tracks;
    private Playlist? _openedCollection;
    private string _searchQuery = string.Empty;

    private Track? _currentTrack;
    private bool _isPlaying;
    private long _positionMs;
    private long _durationMs;
    private bool _isDraggingSlider;

    private bool _isScanning;
    private string _scanStatusText = string.Empty;
    private string _snackbarMessage = string.Empty;
    private DispatcherTimer? _snackbarTimer;

    // Panels & Sheets
    private bool _isLyricsPanelOpen;
    private bool _isLyricsFullscreen;
    private bool _showSyncedLyrics = true;
    private bool _isLoadingLyrics;
    private Lyrics? _currentLyrics;
    private int _currentLyricLineIndex = -1;

    private bool _isQueuePanelOpen;
    private bool _isSettingsPanelOpen;
    private bool _isTrackInfoOpen;
    private Track? _inspectedTrack;

    // Sleep timer
    private DispatcherTimer? _sleepTimer;
    private DateTime? _sleepTimerEndUtc;
    private bool _stopAfterCurrentTrack;
    private string _sleepTimerStatusText = string.Empty;

    public AppSettings Settings { get; }

    public ObservableCollection<Track> FilteredTracks { get; } = new();
    public ObservableCollection<Playlist> DynamicPlaylists { get; } = new();
    public ObservableCollection<Playlist> FilteredPlaylists { get; } = new();
    public ObservableCollection<Playlist> FilteredAlbums { get; } = new();
    public ObservableCollection<Playlist> FilteredArtists { get; } = new();
    public ObservableCollection<Playlist> FilteredGenres { get; } = new();
    public ObservableCollection<Playlist> FilteredFolders { get; } = new();
    public ObservableCollection<Track> OpenedCollectionTracks { get; } = new();

    public ObservableCollection<Track> Queue { get; } = new();
    public ObservableCollection<LyricLine> ActiveLyricLines { get; } = new();
    public ObservableCollection<string> PlainLyricLines { get; } = new();
    public ObservableCollection<MetadataSearchResult> MusicBrainzResults { get; } = new();

    public event Action<int>? ActiveLyricLineChanged;

    public MainViewModel()
    {
        _persistence = new PersistenceService();
        _colorService = new MaterialYouColorService();
        _scanner = new MusicScannerService();
        _metadataService = new MetadataService();
        _lrclibService = new LrclibLyricsService();
        _musicBrainzService = new MusicBrainzMetadataService();
        _audioPlayer = new AudioPlayerService();

        Settings = _persistence.LoadSettings();
        _playCounts = _persistence.LoadPlayCounts();
        _lyricsCache = _persistence.LoadLyricsCache();
        _customPlaylists = _persistence.LoadCustomPlaylists();

        _selectedTab = Settings.DefaultTab;
        _audioPlayer.Volume = Settings.Volume;

        _audioPlayer.PositionChanged += OnPlayerPositionChanged;
        _audioPlayer.PlaybackStateChanged += OnPlayerPlaybackStateChanged;
        _audioPlayer.TrackEnded += OnPlayerTrackEnded;
        _audioPlayer.PlaybackError += msg => ShowSnackbar(msg);

        Settings.PropertyChanged += (_, args) =>
        {
            _persistence.SaveSettings(Settings);
            if (args.PropertyName is nameof(AppSettings.Appearance)
                or nameof(AppSettings.AmoledDarkTheme)
                or nameof(AppSettings.UseDynamicColor)
                or nameof(AppSettings.UseAlbumArtColor)
                or nameof(AppSettings.PaletteStyle)
                or nameof(AppSettings.UseDarkPaletteOnLyricsSheet))
            {
                ApplyDynamicTheme();
            }
            else if (args.PropertyName is nameof(AppSettings.Volume))
            {
                _audioPlayer.Volume = Settings.Volume;
            }
            else if (args.PropertyName is nameof(AppSettings.TrackSort)
                     or nameof(AppSettings.TrackSortOrder)
                     or nameof(AppSettings.PlaylistSort)
                     or nameof(AppSettings.PlaylistSortOrder))
            {
                RefreshLibraryViews();
            }
            else if (args.PropertyName is nameof(AppSettings.LyricsSyncOffsetMs))
            {
                UpdateActiveLyricLine(_positionMs);
            }
        };

        ApplyDynamicTheme();
    }

    public LibraryTab SelectedTab
    {
        get => _selectedTab;
        set
        {
            if (_selectedTab != value)
            {
                _selectedTab = value;
                OpenedCollection = null;
                OnPropertyChanged();
                OnPropertyChanged(nameof(CurrentViewTitle));
            }
        }
    }

    public Playlist? OpenedCollection
    {
        get => _openedCollection;
        set
        {
            _openedCollection = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(HasOpenedCollection));
            OnPropertyChanged(nameof(CurrentViewTitle));
            RefreshOpenedCollectionTracks();
        }
    }

    public bool HasOpenedCollection => _openedCollection != null;

    public string CurrentViewTitle => _openedCollection != null
        ? _openedCollection.Name
        : _selectedTab switch
        {
            LibraryTab.Playlists => "Playlists",
            LibraryTab.Tracks => "Faixas",
            LibraryTab.Albums => "Álbuns",
            LibraryTab.Artists => "Artistas",
            LibraryTab.Genres => "Gêneros",
            LibraryTab.Folders => "Pastas",
            _ => "Biblioteca"
        };

    public string SearchQuery
    {
        get => _searchQuery;
        set
        {
            if (_searchQuery != value)
            {
                _searchQuery = value;
                OnPropertyChanged();
                RefreshLibraryViews();
            }
        }
    }

    public Track? CurrentTrack
    {
        get => _currentTrack;
        private set
        {
            if (_currentTrack != null) _currentTrack.IsCurrentTrack = false;
            _currentTrack = value;
            if (_currentTrack != null) _currentTrack.IsCurrentTrack = true;

            OnPropertyChanged();
            OnPropertyChanged(nameof(HasCurrentTrack));
            ApplyDynamicTheme();
        }
    }

    public bool HasCurrentTrack => _currentTrack != null;

    public bool IsPlaying
    {
        get => _isPlaying;
        private set
        {
            _isPlaying = value;
            OnPropertyChanged();
        }
    }

    public long PositionMs
    {
        get => _positionMs;
        set
        {
            _positionMs = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(PositionFormatted));
            OnPropertyChanged(nameof(ProgressFraction));
        }
    }

    public long DurationMs
    {
        get => _durationMs;
        private set
        {
            _durationMs = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(DurationFormatted));
            OnPropertyChanged(nameof(ProgressFraction));
        }
    }

    public bool IsDraggingSlider
    {
        get => _isDraggingSlider;
        set => _isDraggingSlider = value;
    }

    public string PositionFormatted => Track.FormatTimeMs(_positionMs);
    public string DurationFormatted => Track.FormatTimeMs(_durationMs);
    public double ProgressFraction => _durationMs > 0 ? Math.Clamp((double)_positionMs / _durationMs, 0.0, 1.0) : 0.0;

    public bool IsScanning
    {
        get => _isScanning;
        private set { _isScanning = value; OnPropertyChanged(); }
    }

    public string ScanStatusText
    {
        get => _scanStatusText;
        private set { _scanStatusText = value; OnPropertyChanged(); }
    }

    public string SnackbarMessage
    {
        get => _snackbarMessage;
        private set
        {
            _snackbarMessage = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(HasSnackbar));
        }
    }

    public bool HasSnackbar => !string.IsNullOrWhiteSpace(_snackbarMessage);

    public bool IsLyricsPanelOpen
    {
        get => _isLyricsPanelOpen;
        set
        {
            _isLyricsPanelOpen = value;
            if (!value) IsLyricsFullscreen = false;
            OnPropertyChanged();
            if (value && _currentTrack != null && _currentLyrics?.Uri != _currentTrack.FilePath)
            {
                _ = LoadLyricsForCurrentTrackAsync();
            }
        }
    }

    public bool IsLyricsFullscreen
    {
        get => _isLyricsFullscreen;
        set
        {
            _isLyricsFullscreen = value;
            if (value) _isLyricsPanelOpen = true;
            OnPropertyChanged();
            OnPropertyChanged(nameof(IsLyricsPanelOpen));
        }
    }

    public bool ShowSyncedLyrics
    {
        get => _showSyncedLyrics;
        set { _showSyncedLyrics = value; OnPropertyChanged(); }
    }

    public bool IsLoadingLyrics
    {
        get => _isLoadingLyrics;
        private set { _isLoadingLyrics = value; OnPropertyChanged(); }
    }

    public Lyrics? CurrentLyrics
    {
        get => _currentLyrics;
        private set
        {
            _currentLyrics = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(HasAnyLyrics));
            OnPropertyChanged(nameof(CanToggleSyncedPlain));
        }
    }

    public bool HasAnyLyrics => _currentLyrics != null && (_currentLyrics.HasSynced || _currentLyrics.HasPlain);
    public bool CanToggleSyncedPlain => _currentLyrics != null && _currentLyrics.HasSynced && _currentLyrics.HasPlain;

    public bool IsQueuePanelOpen
    {
        get => _isQueuePanelOpen;
        set { _isQueuePanelOpen = value; OnPropertyChanged(); }
    }

    public bool IsSettingsPanelOpen
    {
        get => _isSettingsPanelOpen;
        set { _isSettingsPanelOpen = value; OnPropertyChanged(); }
    }

    public bool IsTrackInfoOpen
    {
        get => _isTrackInfoOpen;
        set { _isTrackInfoOpen = value; OnPropertyChanged(); }
    }

    public Track? InspectedTrack
    {
        get => _inspectedTrack;
        set { _inspectedTrack = value; OnPropertyChanged(); }
    }

    public string SleepTimerStatusText
    {
        get => _sleepTimerStatusText;
        private set
        {
            _sleepTimerStatusText = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(HasSleepTimer));
        }
    }

    public bool HasSleepTimer => !string.IsNullOrWhiteSpace(_sleepTimerStatusText);

    public int TotalTracksCount => _allTracks.Count;

    public void ApplyDynamicTheme()
    {
        _colorService.ApplyTheme(Settings, _currentTrack?.CoverArtBytes);
    }

    public async Task InitializeAsync()
    {
        if (Settings.ScanOnAppLaunch)
        {
            await ScanMusicLibraryAsync();
        }
    }

    public async Task ScanMusicLibraryAsync()
    {
        if (IsScanning) return;
        _scanCts?.Cancel();
        _scanCts = new CancellationTokenSource();

        IsScanning = true;
        ScanStatusText = "Escaneando biblioteca de músicas...";

        try
        {
            var progress = new Progress<(int Current, int Total, string CurrentFile)>(p =>
            {
                ScanStatusText = $"Escaneando ({p.Current}/{p.Total}): {p.CurrentFile}";
            });

            var scanned = await _scanner.ScanLibraryAsync(Settings, _playCounts, progress, _scanCts.Token);
            _allTracks = scanned;

            // Rehydrate custom playlists with matching Track instances
            var trackLookup = _allTracks.ToDictionary(t => t.FilePath, t => t, StringComparer.OrdinalIgnoreCase);
            foreach (var pl in _customPlaylists)
            {
                var resolved = new List<Track>();
                foreach (var path in pl.TrackPaths)
                {
                    if (trackLookup.TryGetValue(path, out var t))
                    {
                        resolved.Add(t);
                    }
                    else if (File.Exists(path))
                    {
                        var extTrack = _scanner.ReadTrackFromFile(path, _playCounts);
                        if (extTrack != null) resolved.Add(extTrack);
                    }
                }
                pl.Tracks = resolved;
            }

            RefreshLibraryViews();
            OnPropertyChanged(nameof(TotalTracksCount));

            // Restore last played track if nothing is currently loaded
            if (CurrentTrack == null && !string.IsNullOrWhiteSpace(Settings.LastPlayedTrackPath))
            {
                var last = _allTracks.FirstOrDefault(t =>
                    t.FilePath.Equals(Settings.LastPlayedTrackPath, StringComparison.OrdinalIgnoreCase));
                if (last != null)
                {
                    Queue.Clear();
                    foreach (var t in FilteredTracks) Queue.Add(t);
                    CurrentTrack = last;
                    DurationMs = last.DurationMs;
                    _audioPlayer.LoadAndPlay(last, Settings.LastPlayedPositionMs, autoPlay: false);
                }
            }

            ScanStatusText = $"{_allTracks.Count} faixas encontradas.";
        }
        catch (OperationCanceledException)
        {
            ScanStatusText = "Escaneamento cancelado.";
        }
        catch (Exception ex)
        {
            ScanStatusText = $"Erro ao escanear: {ex.Message}";
        }
        finally
        {
            IsScanning = false;
        }
    }

    public void AddFilesAndPlay(IEnumerable<string> filePaths)
    {
        Track? firstAdded = null;
        foreach (var path in filePaths)
        {
            var ext = Path.GetExtension(path);
            if (!MusicScannerService.SupportedExtensions.Contains(ext)) continue;

            var existing = _allTracks.FirstOrDefault(t => t.FilePath.Equals(path, StringComparison.OrdinalIgnoreCase));
            if (existing == null)
            {
                existing = _scanner.ReadTrackFromFile(path, _playCounts);
                if (existing != null)
                {
                    _allTracks.Add(existing);
                }
            }
            firstAdded ??= existing;
        }

        RefreshLibraryViews();
        OnPropertyChanged(nameof(TotalTracksCount));

        if (firstAdded != null)
        {
            PlayTrack(firstAdded, _allTracks);
        }
    }

    public void RefreshLibraryViews()
    {
        var query = _searchQuery?.Trim() ?? string.Empty;

        // 1. Tracks
        var sortedTracks = _allTracks
            .Where(t => t.MatchesFilter(query))
            .SortTracks(Settings.TrackSort, Settings.TrackSortOrder);

        ReplaceCollection(FilteredTracks, sortedTracks);

        // 2. Dynamic Playlists (Recently Added & Most Played, just like PlayerScreen.kt lines 282-292)
        var recentTracks = _allTracks
            .OrderByDescending(t => t.DateModified)
            .Take(50)
            .ToList();
        var mostPlayed = _allTracks
            .Where(t => t.PlayCount > 0)
            .OrderByDescending(t => t.PlayCount)
            .Take(50)
            .ToList();

        var dynList = new List<Playlist>
        {
            new()
            {
                Id = "dynamic-recent",
                Name = "Adicionadas Recentemente",
                Category = "Dinâmica",
                IsDynamic = true,
                IsUserEditable = false,
                SubtitleOverride = "Últimas músicas",
                Tracks = recentTracks
            },
            new()
            {
                Id = "dynamic-most-played",
                Name = "Mais Tocadas",
                Category = "Dinâmica",
                IsDynamic = true,
                IsUserEditable = false,
                SubtitleOverride = "Favoritas",
                Tracks = mostPlayed
            }
        };
        ReplaceCollection(DynamicPlaylists, dynList);

        // 3. Custom Playlists
        var sortedCustom = _customPlaylists
            .Where(p => p.MatchesFilter(query))
            .SortPlaylists(Settings.PlaylistSort, Settings.PlaylistSortOrder);
        ReplaceCollection(FilteredPlaylists, sortedCustom);

        // 4. Albums
        var albums = _allTracks
            .GroupBy(t => t.DisplayAlbum, StringComparer.CurrentCultureIgnoreCase)
            .Select(g => new Playlist
            {
                Name = g.Key,
                Category = "Álbum",
                IsUserEditable = false,
                SubtitleOverride = g.First().DisplayArtist,
                Tracks = g.SortTracks(TrackSort.TrackNumber, SortOrder.Ascending)
            })
            .Where(p => p.MatchesFilter(query))
            .SortPlaylists(Settings.PlaylistSort, Settings.PlaylistSortOrder);
        ReplaceCollection(FilteredAlbums, albums);

        // 5. Artists
        var artists = _allTracks
            .GroupBy(t => t.DisplayArtist, StringComparer.CurrentCultureIgnoreCase)
            .Select(g => new Playlist
            {
                Name = g.Key,
                Category = "Artista",
                IsUserEditable = false,
                SubtitleOverride = $"{g.Select(x => x.DisplayAlbum).Distinct(StringComparer.CurrentCultureIgnoreCase).Count()} álbuns",
                Tracks = g.SortTracks(Settings.TrackSort, Settings.TrackSortOrder)
            })
            .Where(p => p.MatchesFilter(query))
            .SortPlaylists(Settings.PlaylistSort, Settings.PlaylistSortOrder);
        ReplaceCollection(FilteredArtists, artists);

        // 6. Genres
        var genres = _allTracks
            .GroupBy(t => t.DisplayGenre, StringComparer.CurrentCultureIgnoreCase)
            .Select(g => new Playlist
            {
                Name = g.Key,
                Category = "Gênero",
                IsUserEditable = false,
                Tracks = g.SortTracks(Settings.TrackSort, Settings.TrackSortOrder)
            })
            .Where(p => p.MatchesFilter(query))
            .SortPlaylists(Settings.PlaylistSort, Settings.PlaylistSortOrder);
        ReplaceCollection(FilteredGenres, genres);

        // 7. Folders
        var folders = _allTracks
            .GroupBy(t => t.FolderPath, StringComparer.OrdinalIgnoreCase)
            .Select(g => new Playlist
            {
                Name = g.First().FolderName,
                Category = "Pasta",
                IsUserEditable = false,
                SubtitleOverride = g.Key,
                Tracks = g.SortTracks(Settings.TrackSort, Settings.TrackSortOrder)
            })
            .Where(p => p.MatchesFilter(query))
            .SortPlaylists(Settings.PlaylistSort, Settings.PlaylistSortOrder);
        ReplaceCollection(FilteredFolders, folders);

        RefreshOpenedCollectionTracks();
    }

    private void RefreshOpenedCollectionTracks()
    {
        if (_openedCollection == null)
        {
            OpenedCollectionTracks.Clear();
            return;
        }

        var query = _searchQuery?.Trim() ?? string.Empty;
        var list = _openedCollection.IsUserEditable
            ? _openedCollection.Tracks.Where(t => t.MatchesFilter(query)).ToList()
            : _openedCollection.Tracks.Where(t => t.MatchesFilter(query)).SortTracks(Settings.TrackSort, Settings.TrackSortOrder);

        ReplaceCollection(OpenedCollectionTracks, list);
    }

    public void PlayTrack(Track track, IEnumerable<Track>? sourceQueue = null)
    {
        if (sourceQueue != null)
        {
            Queue.Clear();
            foreach (var item in sourceQueue)
            {
                Queue.Add(item);
            }
        }
        else if (!Queue.Contains(track))
        {
            Queue.Add(track);
        }

        CurrentTrack = track;
        DurationMs = track.DurationMs;
        PositionMs = 0;

        track.PlayCount++;
        _playCounts[track.FilePath] = track.PlayCount;
        _persistence.SavePlayCounts(_playCounts);

        Settings.LastPlayedTrackPath = track.FilePath;
        Settings.LastPlayedPositionMs = 0;
        _persistence.SaveSettings(Settings);

        _audioPlayer.LoadAndPlay(track, 0, autoPlay: true);
        _ = LoadLyricsForCurrentTrackAsync();
    }

    public void PlayPlaylist(Playlist playlist, bool shuffle = false)
    {
        if (playlist.Tracks.Count == 0) return;

        var tracks = playlist.Tracks.ToList();
        if (shuffle)
        {
            Settings.PlaybackMode = PlaybackMode.Shuffle;
            var first = tracks[_random.Next(tracks.Count)];
            PlayTrack(first, tracks);
        }
        else
        {
            PlayTrack(tracks[0], tracks);
        }
    }

    public void PlayNextInQueue(Track track)
    {
        if (CurrentTrack == null || Queue.Count == 0)
        {
            PlayTrack(track, new[] { track });
            return;
        }

        int currentIdx = Queue.IndexOf(CurrentTrack);
        int existingIdx = Queue.IndexOf(track);
        if (existingIdx >= 0 && existingIdx != currentIdx)
        {
            Queue.RemoveAt(existingIdx);
            if (existingIdx < currentIdx) currentIdx--;
        }

        int insertIdx = Math.Clamp(currentIdx + 1, 0, Queue.Count);
        Queue.Insert(insertIdx, track);
        ShowSnackbar($"'{track.DisplayTitle}' tocará a seguir.");
    }

    public void AddToQueue(Track track)
    {
        if (CurrentTrack == null)
        {
            PlayTrack(track, new[] { track });
            return;
        }

        Queue.Add(track);
        ShowSnackbar($"'{track.DisplayTitle}' adicionada à fila.");
    }

    public void RemoveFromQueue(Track track)
    {
        Queue.Remove(track);
    }

    public void MoveQueueItem(int oldIndex, int newIndex)
    {
        if (oldIndex < 0 || oldIndex >= Queue.Count || newIndex < 0 || newIndex >= Queue.Count) return;
        Queue.Move(oldIndex, newIndex);
    }

    public void TogglePlayPause()
    {
        if (CurrentTrack == null)
        {
            if (FilteredTracks.Count > 0)
                PlayTrack(FilteredTracks[0], FilteredTracks);
            return;
        }
        _audioPlayer.TogglePlayPause();
    }

    public void SkipToNext()
    {
        if (Queue.Count == 0 || CurrentTrack == null) return;

        if (Settings.PlaybackMode == PlaybackMode.Shuffle && Queue.Count > 1)
        {
            var candidates = Queue.Where(t => t != CurrentTrack).ToList();
            var next = candidates[_random.Next(candidates.Count)];
            PlayTrack(next, null);
            return;
        }

        int idx = Queue.IndexOf(CurrentTrack);
        int nextIdx = (idx + 1) % Queue.Count;
        PlayTrack(Queue[nextIdx], null);
    }

    public void SkipToPrevious()
    {
        if (Queue.Count == 0 || CurrentTrack == null) return;

        if (PositionMs > 4000)
        {
            SeekTo(0);
            return;
        }

        int idx = Queue.IndexOf(CurrentTrack);
        int prevIdx = (idx - 1 + Queue.Count) % Queue.Count;
        PlayTrack(Queue[prevIdx], null);
    }

    public void CyclePlaybackMode()
    {
        Settings.PlaybackMode = Settings.PlaybackMode switch
        {
            PlaybackMode.Repeat => PlaybackMode.RepeatOne,
            PlaybackMode.RepeatOne => PlaybackMode.Shuffle,
            _ => PlaybackMode.Repeat
        };

        var label = Settings.PlaybackMode switch
        {
            PlaybackMode.Repeat => "Repetir fila",
            PlaybackMode.RepeatOne => "Repetir uma faixa",
            PlaybackMode.Shuffle => "Modo aleatório",
            _ => ""
        };
        ShowSnackbar(label);
    }

    public void SeekTo(long targetMs)
    {
        PositionMs = targetMs;
        _audioPlayer.SeekTo(targetMs);
        Settings.LastPlayedPositionMs = targetMs;
        UpdateActiveLyricLine(targetMs);
    }

    private void OnPlayerPositionChanged(long posMs, long durMs)
    {
        if (!_isDraggingSlider)
        {
            PositionMs = posMs;
        }
        if (durMs > 0 && durMs != _durationMs)
        {
            DurationMs = durMs;
        }
        UpdateActiveLyricLine(posMs);
    }

    private void OnPlayerPlaybackStateChanged(bool playing)
    {
        IsPlaying = playing;
    }

    private void OnPlayerTrackEnded()
    {
        if (_stopAfterCurrentTrack)
        {
            CancelSleepTimer();
            ShowSnackbar("Temporizador de sono concluído.");
            return;
        }

        if (CurrentTrack == null || Queue.Count == 0) return;

        if (Settings.PlaybackMode == PlaybackMode.RepeatOne)
        {
            PlayTrack(CurrentTrack, null);
        }
        else
        {
            SkipToNext();
        }
    }

    // Playlist Management
    public Playlist CreatePlaylist(string name, IEnumerable<Track>? initialTracks = null)
    {
        var trimmed = string.IsNullOrWhiteSpace(name) ? "Nova Playlist" : name.Trim();
        var list = initialTracks?.ToList() ?? new List<Track>();
        var playlist = new Playlist
        {
            Name = trimmed,
            IsUserEditable = true,
            Tracks = list,
            TrackPaths = list.Select(t => t.FilePath).ToList()
        };

        _customPlaylists.Add(playlist);
        _persistence.SaveCustomPlaylists(_customPlaylists);
        RefreshLibraryViews();
        ShowSnackbar($"Playlist '{trimmed}' criada.");
        return playlist;
    }

    public void RenamePlaylist(Playlist playlist, string newName)
    {
        if (!playlist.IsUserEditable || string.IsNullOrWhiteSpace(newName)) return;
        playlist.Name = newName.Trim();
        _persistence.SaveCustomPlaylists(_customPlaylists);
        RefreshLibraryViews();
        OnPropertyChanged(nameof(CurrentViewTitle));
        ShowSnackbar($"Playlist renomeada para '{playlist.Name}'.");
    }

    public void DeletePlaylist(Playlist playlist)
    {
        if (!playlist.IsUserEditable) return;
        _customPlaylists.Remove(playlist);
        if (OpenedCollection == playlist)
        {
            OpenedCollection = null;
        }
        _persistence.SaveCustomPlaylists(_customPlaylists);
        RefreshLibraryViews();
        ShowSnackbar($"Playlist '{playlist.Name}' excluída.");
    }

    public void AddTrackToPlaylist(Playlist playlist, Track track)
    {
        if (!playlist.IsUserEditable) return;
        if (playlist.Tracks.Any(t => t.FilePath.Equals(track.FilePath, StringComparison.OrdinalIgnoreCase)))
        {
            ShowSnackbar($"'{track.DisplayTitle}' já está em '{playlist.Name}'.");
            return;
        }

        var updated = playlist.Tracks.ToList();
        updated.Add(track);
        playlist.Tracks = updated;
        playlist.TrackPaths = updated.Select(t => t.FilePath).ToList();
        _persistence.SaveCustomPlaylists(_customPlaylists);
        RefreshLibraryViews();
        ShowSnackbar($"Adicionada a '{playlist.Name}'.");
    }

    public void RemoveTrackFromPlaylist(Playlist playlist, Track track)
    {
        if (!playlist.IsUserEditable) return;
        var updated = playlist.Tracks.Where(t => t != track).ToList();
        playlist.Tracks = updated;
        playlist.TrackPaths = updated.Select(t => t.FilePath).ToList();
        _persistence.SaveCustomPlaylists(_customPlaylists);
        RefreshLibraryViews();
    }

    public void MoveTrackInPlaylist(Playlist playlist, Track track, int direction)
    {
        if (!playlist.IsUserEditable) return;
        var list = playlist.Tracks.ToList();
        int idx = list.IndexOf(track);
        int target = idx + direction;
        if (idx < 0 || target < 0 || target >= list.Count) return;

        list.RemoveAt(idx);
        list.Insert(target, track);
        playlist.Tracks = list;
        playlist.TrackPaths = list.Select(t => t.FilePath).ToList();
        _persistence.SaveCustomPlaylists(_customPlaylists);
        RefreshOpenedCollectionTracks();
    }

    public void ImportM3u(string filePath)
    {
        var imported = _persistence.ImportM3uPlaylist(filePath, _allTracks);
        if (imported != null)
        {
            _customPlaylists.Add(imported);
            _persistence.SaveCustomPlaylists(_customPlaylists);
            RefreshLibraryViews();
            ShowSnackbar($"Playlist '{imported.Name}' importada ({imported.TrackCount} faixas).");
        }
        else
        {
            ShowSnackbar("Não foi possível importar a playlist M3U.");
        }
    }

    public void ExportM3u(Playlist playlist, string filePath)
    {
        if (_persistence.ExportM3uPlaylist(playlist, filePath))
            ShowSnackbar($"Playlist exportada para '{Path.GetFileName(filePath)}'.");
        else
            ShowSnackbar("Erro ao exportar playlist.");
    }

    public void GoToAlbum(Track track)
    {
        var album = FilteredAlbums.FirstOrDefault(a =>
            a.Name.Equals(track.DisplayAlbum, StringComparison.CurrentCultureIgnoreCase));
        if (album != null)
        {
            SelectedTab = LibraryTab.Albums;
            OpenedCollection = album;
        }
    }

    public void GoToArtist(Track track)
    {
        var artist = FilteredArtists.FirstOrDefault(a =>
            a.Name.Equals(track.DisplayArtist, StringComparison.CurrentCultureIgnoreCase));
        if (artist != null)
        {
            SelectedTab = LibraryTab.Artists;
            OpenedCollection = artist;
        }
    }

    // Lyrics Loading & Synchronization
    public async Task LoadLyricsForCurrentTrackAsync(bool forceRemote = false)
    {
        var track = CurrentTrack;
        if (track == null) return;

        _lyricsCts?.Cancel();
        _lyricsCts = new CancellationTokenSource();
        var token = _lyricsCts.Token;

        IsLoadingLyrics = true;
        CurrentLyrics = null;
        ActiveLyricLines.Clear();
        PlainLyricLines.Clear();
        _currentLyricLineIndex = -1;

        try
        {
            Lyrics? resolved = null;

            if (!forceRemote)
            {
                // 1. Check embedded tag or local .lrc file
                resolved = await Task.Run(() => _metadataService.ReadLocalLyrics(track), token);

                // 2. Check local cache
                if (resolved == null && _lyricsCache.TryGetValue(track.FilePath, out var cached))
                {
                    resolved = cached.ToDomainLyrics();
                }
            }

            // 3. Query LRCLIB
            if (resolved == null)
            {
                var (remoteLyrics, _) = await _lrclibService.GetLyricsAsync(track, token);
                if (remoteLyrics != null)
                {
                    resolved = remoteLyrics;
                    _lyricsCache[track.FilePath] = CachedLyricsEntry.FromDomainLyrics(remoteLyrics);
                    _persistence.SaveLyricsCache(_lyricsCache);
                }
            }

            if (token.IsCancellationRequested || CurrentTrack != track) return;

            ApplyLoadedLyrics(resolved, track.DurationMs);
        }
        catch (OperationCanceledException)
        {
            // Cancelled
        }
        finally
        {
            if (!token.IsCancellationRequested)
            {
                IsLoadingLyrics = false;
            }
        }
    }

    public void LoadLyricsFromExternalLrcFile(string lrcFilePath)
    {
        if (CurrentTrack == null || !File.Exists(lrcFilePath)) return;
        try
        {
            var text = File.ReadAllText(lrcFilePath);
            var lyrics = MetadataService.ParseLyricsText(CurrentTrack.FilePath, text, areFromRemote: false);
            _lyricsCache[CurrentTrack.FilePath] = CachedLyricsEntry.FromDomainLyrics(lyrics);
            _persistence.SaveLyricsCache(_lyricsCache);
            ApplyLoadedLyrics(lyrics, CurrentTrack.DurationMs);
            ShowSnackbar("Arquivo .LRC carregado com sucesso.");
        }
        catch (Exception ex)
        {
            ShowSnackbar($"Erro ao ler arquivo .LRC: {ex.Message}");
        }
    }

    private void ApplyLoadedLyrics(Lyrics? lyrics, long trackDurationMs)
    {
        CurrentLyrics = lyrics;
        ActiveLyricLines.Clear();
        PlainLyricLines.Clear();
        _currentLyricLineIndex = -1;

        if (lyrics == null) return;

        if (lyrics.Synced is { Count: > 0 })
        {
            var lines = LyricParser.ToLyricLines(lyrics.Synced, trackDurationMs > 0 ? trackDurationMs : long.MaxValue);
            foreach (var line in lines)
            {
                ActiveLyricLines.Add(line);
            }
            ShowSyncedLyrics = true;
        }
        else if (lyrics.Plain is { Count: > 0 })
        {
            ShowSyncedLyrics = false;
        }

        if (lyrics.Plain is { Count: > 0 })
        {
            foreach (var p in lyrics.Plain)
            {
                PlainLyricLines.Add(p);
            }
        }

        UpdateActiveLyricLine(_positionMs);
    }

    private void UpdateActiveLyricLine(long rawPositionMs)
    {
        if (ActiveLyricLines.Count == 0) return;

        long adjustedPos = rawPositionMs - Settings.LyricsSyncOffsetMs;
        int activeIdx = -1;

        for (int i = 0; i < ActiveLyricLines.Count; i++)
        {
            var line = ActiveLyricLines[i];
            long nextStart = i + 1 < ActiveLyricLines.Count ? ActiveLyricLines[i + 1].StartTimeMs : long.MaxValue;
            if (adjustedPos >= line.StartTimeMs && adjustedPos < nextStart)
            {
                activeIdx = i;
                break;
            }
        }

        for (int i = 0; i < ActiveLyricLines.Count; i++)
        {
            var line = ActiveLyricLines[i];
            bool isCurr = i == activeIdx;
            line.IsActive = isCurr;
            line.IsPassed = activeIdx > i;

            if (isCurr)
            {
                long dur = Math.Max(1L, line.EndTimeMs - line.StartTimeMs);
                line.LineProgress = Math.Clamp((double)(adjustedPos - line.StartTimeMs) / dur, 0.0, 1.0);

                if (line.HasWords)
                {
                    foreach (var w in line.Words)
                    {
                        if (adjustedPos >= w.EndTimeMs)
                        {
                            w.State = LyricWordState.Passed;
                            w.Progress = 1.0;
                        }
                        else if (adjustedPos >= w.StartTimeMs)
                        {
                            w.State = LyricWordState.Current;
                            long wDur = Math.Max(1L, w.EndTimeMs - w.StartTimeMs);
                            w.Progress = Math.Clamp((double)(adjustedPos - w.StartTimeMs) / wDur, 0.0, 1.0);
                        }
                        else
                        {
                            w.State = LyricWordState.Upcoming;
                            w.Progress = 0.0;
                        }
                    }
                }
            }
            else
            {
                line.LineProgress = line.IsPassed ? 1.0 : 0.0;
            }
        }

        if (activeIdx != _currentLyricLineIndex)
        {
            _currentLyricLineIndex = activeIdx;
            if (activeIdx >= 0)
            {
                ActiveLyricLineChanged?.Invoke(activeIdx);
            }
        }
    }

    // Track Info & Metadata Editing
    public void OpenTrackInfo(Track track)
    {
        InspectedTrack = track;
        MusicBrainzResults.Clear();
        IsTrackInfoOpen = true;
    }

    public bool SaveTrackMetadata(Track track, MetadataEditModel editModel)
    {
        bool wasCurrent = CurrentTrack == track;
        bool wasPlaying = IsPlaying;
        long savedPos = PositionMs;

        if (wasCurrent)
        {
            _audioPlayer.ReleaseFileLockForMetadataEdit();
        }

        var (success, error) = _metadataService.WriteMetadata(track, editModel);

        if (wasCurrent)
        {
            _audioPlayer.LoadAndPlay(track, savedPos, autoPlay: wasPlaying);
            ApplyDynamicTheme();
            _ = LoadLyricsForCurrentTrackAsync();
        }

        if (success)
        {
            RefreshLibraryViews();
            ShowSnackbar("Metadados salvos com sucesso no arquivo!");
        }
        else
        {
            ShowSnackbar(error ?? "Erro ao salvar metadados.");
        }

        return success;
    }

    public async Task SearchMusicBrainzAsync(string query, long durationMs)
    {
        MusicBrainzResults.Clear();
        ShowSnackbar("Buscando metadados no MusicBrainz...");
        var (results, error) = await _musicBrainzService.SearchMetadataAsync(
            query,
            durationMs,
            Settings.MatchDurationWhenSearchMetadata);

        if (!string.IsNullOrWhiteSpace(error))
        {
            ShowSnackbar(error);
            return;
        }

        foreach (var r in results)
        {
            MusicBrainzResults.Add(r);
        }

        ShowSnackbar(results.Count > 0
            ? $"{results.Count} resultados encontrados no MusicBrainz."
            : "Nenhum resultado encontrado no MusicBrainz.");
    }

    public async Task<byte[]?> DownloadMusicBrainzCoverArtAsync(MetadataSearchResult result)
    {
        return await _musicBrainzService.GetCoverArtBytesAsync(result);
    }

    public async Task PublishCurrentLyricsToLrclibAsync()
    {
        if (CurrentTrack == null || CurrentLyrics == null)
        {
            ShowSnackbar("Nenhuma letra carregada para publicar.");
            return;
        }

        var progress = new Progress<string>(msg => ShowSnackbar(msg));
        var (ok, message) = await _lrclibService.PublishLyricsAsync(CurrentTrack, CurrentLyrics, progress);
        ShowSnackbar(message);
    }

    // Sleep Timer
    public void StartSleepTimer(int minutes, bool stopAtEndOfTrack = false)
    {
        CancelSleepTimer();

        if (stopAtEndOfTrack)
        {
            _stopAfterCurrentTrack = true;
            SleepTimerStatusText = "Parar ao fim da faixa";
            ShowSnackbar("Temporizador: reprodução parará ao fim desta música.");
            return;
        }

        if (minutes <= 0) return;

        _sleepTimerEndUtc = DateTime.UtcNow.AddMinutes(minutes);
        _sleepTimer = new DispatcherTimer
        {
            Interval = TimeSpan.FromSeconds(1)
        };
        _sleepTimer.Tick += (_, _) =>
        {
            if (_sleepTimerEndUtc == null) return;
            var remaining = _sleepTimerEndUtc.Value - DateTime.UtcNow;
            if (remaining <= TimeSpan.Zero)
            {
                _audioPlayer.Pause();
                CancelSleepTimer();
                ShowSnackbar("Temporizador de sono: reprodução pausada.");
            }
            else
            {
                SleepTimerStatusText = $"{(int)remaining.TotalMinutes:D2}:{remaining.Seconds:D2}";
            }
        };
        _sleepTimer.Start();
        SleepTimerStatusText = $"{minutes:D2}:00";
        ShowSnackbar($"Temporizador de sono definido para {minutes} minutos.");
    }

    public void CancelSleepTimer()
    {
        _stopAfterCurrentTrack = false;
        _sleepTimerEndUtc = null;
        _sleepTimer?.Stop();
        _sleepTimer = null;
        SleepTimerStatusText = string.Empty;
    }

    public void ShowSnackbar(string message)
    {
        Application.Current?.Dispatcher.BeginInvoke(() =>
        {
            SnackbarMessage = message;
            _snackbarTimer?.Stop();
            _snackbarTimer = new DispatcherTimer
            {
                Interval = TimeSpan.FromSeconds(3.5)
            };
            _snackbarTimer.Tick += (_, _) =>
            {
                SnackbarMessage = string.Empty;
                _snackbarTimer?.Stop();
            };
            _snackbarTimer.Start();
        });
    }

    private static void ReplaceCollection<T>(ObservableCollection<T> target, IEnumerable<T> items)
    {
        target.Clear();
        foreach (var item in items)
        {
            target.Add(item);
        }
    }

    public void Dispose()
    {
        _lyricsCts?.Cancel();
        _scanCts?.Cancel();
        _audioPlayer.Dispose();
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    protected void OnPropertyChanged([CallerMemberName] string? name = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}
