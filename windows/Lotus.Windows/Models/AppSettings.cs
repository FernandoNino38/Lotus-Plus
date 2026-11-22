using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace Lotus.Windows.Models;

public class AppSettings : INotifyPropertyChanged
{
    private AppAppearance _appearance = AppAppearance.Dark;
    private bool _amoledDarkTheme;
    private bool _useDynamicColor = true;
    private bool _useAlbumArtColor = true;
    private PaletteStyle _paletteStyle = PaletteStyle.TonalSpot;
    private bool _useDarkPaletteOnLyricsSheet = true;

    private double _lyricsFontSize = 24.0;
    private int _lyricsFontWeight = 600;
    private int _lyricsAlignment = 0; // 0 = Left, 1 = Center, 2 = Right
    private long _lyricsSyncOffsetMs;

    private bool _scanMusicFolder = true;
    private List<string> _extraScanFolders = new();
    private List<string> _excludedScanFolders = new();
    private bool _ignoreShortTracks = true;
    private bool _scanOnAppLaunch = true;

    private bool _matchDurationWhenSearchMetadata = true;
    private bool _replaceSearchWithFilter;
    private bool _gridPlaylists = true;
    private LibraryTab _defaultTab = LibraryTab.Tracks;

    private TrackSort _trackSort = TrackSort.Title;
    private SortOrder _trackSortOrder = SortOrder.Ascending;
    private PlaylistSort _playlistSort = PlaylistSort.Title;
    private SortOrder _playlistSortOrder = SortOrder.Ascending;

    private float _volume = 0.85f;
    private PlaybackMode _playbackMode = PlaybackMode.Repeat;
    private string? _lastPlayedTrackPath;
    private long _lastPlayedPositionMs;

    public AppAppearance Appearance
    {
        get => _appearance;
        set { _appearance = value; OnPropertyChanged(); }
    }

    public bool AmoledDarkTheme
    {
        get => _amoledDarkTheme;
        set { _amoledDarkTheme = value; OnPropertyChanged(); }
    }

    public bool UseDynamicColor
    {
        get => _useDynamicColor;
        set { _useDynamicColor = value; OnPropertyChanged(); }
    }

    public bool UseAlbumArtColor
    {
        get => _useAlbumArtColor;
        set { _useAlbumArtColor = value; OnPropertyChanged(); }
    }

    public PaletteStyle PaletteStyle
    {
        get => _paletteStyle;
        set { _paletteStyle = value; OnPropertyChanged(); }
    }

    public bool UseDarkPaletteOnLyricsSheet
    {
        get => _useDarkPaletteOnLyricsSheet;
        set { _useDarkPaletteOnLyricsSheet = value; OnPropertyChanged(); }
    }

    public double LyricsFontSize
    {
        get => _lyricsFontSize;
        set { _lyricsFontSize = Math.Clamp(value, 14.0, 44.0); OnPropertyChanged(); }
    }

    public int LyricsFontWeight
    {
        get => _lyricsFontWeight;
        set { _lyricsFontWeight = Math.Clamp(value, 300, 900); OnPropertyChanged(); }
    }

    public int LyricsAlignment
    {
        get => _lyricsAlignment;
        set { _lyricsAlignment = Math.Clamp(value, 0, 2); OnPropertyChanged(); }
    }

    public long LyricsSyncOffsetMs
    {
        get => _lyricsSyncOffsetMs;
        set { _lyricsSyncOffsetMs = Math.Clamp(value, -10000L, 10000L); OnPropertyChanged(); }
    }

    public bool ScanMusicFolder
    {
        get => _scanMusicFolder;
        set { _scanMusicFolder = value; OnPropertyChanged(); }
    }

    public List<string> ExtraScanFolders
    {
        get => _extraScanFolders;
        set { _extraScanFolders = value ?? new(); OnPropertyChanged(); }
    }

    public List<string> ExcludedScanFolders
    {
        get => _excludedScanFolders;
        set { _excludedScanFolders = value ?? new(); OnPropertyChanged(); }
    }

    public bool IgnoreShortTracks
    {
        get => _ignoreShortTracks;
        set { _ignoreShortTracks = value; OnPropertyChanged(); }
    }

    public bool ScanOnAppLaunch
    {
        get => _scanOnAppLaunch;
        set { _scanOnAppLaunch = value; OnPropertyChanged(); }
    }

    public bool MatchDurationWhenSearchMetadata
    {
        get => _matchDurationWhenSearchMetadata;
        set { _matchDurationWhenSearchMetadata = value; OnPropertyChanged(); }
    }

    public bool ReplaceSearchWithFilter
    {
        get => _replaceSearchWithFilter;
        set { _replaceSearchWithFilter = value; OnPropertyChanged(); }
    }

    public bool GridPlaylists
    {
        get => _gridPlaylists;
        set { _gridPlaylists = value; OnPropertyChanged(); }
    }

    public LibraryTab DefaultTab
    {
        get => _defaultTab;
        set { _defaultTab = value; OnPropertyChanged(); }
    }

    public TrackSort TrackSort
    {
        get => _trackSort;
        set { _trackSort = value; OnPropertyChanged(); }
    }

    public SortOrder TrackSortOrder
    {
        get => _trackSortOrder;
        set { _trackSortOrder = value; OnPropertyChanged(); }
    }

    public PlaylistSort PlaylistSort
    {
        get => _playlistSort;
        set { _playlistSort = value; OnPropertyChanged(); }
    }

    public SortOrder PlaylistSortOrder
    {
        get => _playlistSortOrder;
        set { _playlistSortOrder = value; OnPropertyChanged(); }
    }

    public float Volume
    {
        get => _volume;
        set { _volume = Math.Clamp(value, 0f, 1f); OnPropertyChanged(); }
    }

    public PlaybackMode PlaybackMode
    {
        get => _playbackMode;
        set { _playbackMode = value; OnPropertyChanged(); }
    }

    public string? LastPlayedTrackPath
    {
        get => _lastPlayedTrackPath;
        set { _lastPlayedTrackPath = value; OnPropertyChanged(); }
    }

    public long LastPlayedPositionMs
    {
        get => _lastPlayedPositionMs;
        set { _lastPlayedPositionMs = value; OnPropertyChanged(); }
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    protected void OnPropertyChanged([CallerMemberName] string? name = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}
