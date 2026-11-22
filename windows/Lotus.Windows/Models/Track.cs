using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.IO;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Text.Json.Serialization;
using System.Windows.Media.Imaging;

namespace Lotus.Windows.Models;

public class Track : INotifyPropertyChanged
{
    private string? _title;
    private string? _artist;
    private string? _album;
    private string? _albumArtist;
    private string? _genre;
    private string? _year;
    private string? _trackNumber;
    private string? _bitrate;
    private byte[]? _coverArtBytes;
    private BitmapImage? _coverArtImage;
    private bool _isPlaying;
    private int _playCount;

    public string FilePath { get; set; } = string.Empty;
    public int DurationMs { get; set; }
    public long Size { get; set; }
    public long DateModified { get; set; }

    public string? Title
    {
        get => _title;
        set { _title = value; OnPropertyChanged(); OnPropertyChanged(nameof(DisplayTitle)); }
    }

    public string? Artist
    {
        get => _artist;
        set { _artist = value; OnPropertyChanged(); OnPropertyChanged(nameof(DisplayArtist)); OnPropertyChanged(nameof(Subtitle)); }
    }

    public string? Album
    {
        get => _album;
        set { _album = value; OnPropertyChanged(); OnPropertyChanged(nameof(DisplayAlbum)); OnPropertyChanged(nameof(Subtitle)); }
    }

    public string? AlbumArtist
    {
        get => _albumArtist;
        set { _albumArtist = value; OnPropertyChanged(); }
    }

    public string? Genre
    {
        get => _genre;
        set { _genre = value; OnPropertyChanged(); OnPropertyChanged(nameof(DisplayGenre)); }
    }

    public string? Year
    {
        get => _year;
        set { _year = value; OnPropertyChanged(); }
    }

    public string? TrackNumber
    {
        get => _trackNumber;
        set { _trackNumber = value; OnPropertyChanged(); }
    }

    public string? Bitrate
    {
        get => _bitrate;
        set { _bitrate = value; OnPropertyChanged(); OnPropertyChanged(nameof(AudioQualityBadge)); }
    }

    public int SampleRate { get; set; }
    public int BitsPerSample { get; set; }

    public int PlayCount
    {
        get => _playCount;
        set { _playCount = value; OnPropertyChanged(); }
    }

    [JsonIgnore]
    public bool IsCurrentTrack
    {
        get => _isPlaying;
        set { _isPlaying = value; OnPropertyChanged(); }
    }

    [JsonIgnore]
    public byte[]? CoverArtBytes
    {
        get => _coverArtBytes;
        set
        {
            _coverArtBytes = value;
            _coverArtImage = null;
            OnPropertyChanged();
            OnPropertyChanged(nameof(CoverArtImage));
            OnPropertyChanged(nameof(HasCoverArt));
        }
    }

    [JsonIgnore]
    public bool HasCoverArt => _coverArtBytes is { Length: > 0 };

    [JsonIgnore]
    public BitmapImage? CoverArtImage
    {
        get
        {
            if (_coverArtImage == null && _coverArtBytes is { Length: > 0 })
            {
                try
                {
                    using var ms = new MemoryStream(_coverArtBytes);
                    var bmp = new BitmapImage();
                    bmp.BeginInit();
                    bmp.CacheOption = BitmapCacheOption.OnLoad;
                    bmp.DecodePixelWidth = 300;
                    bmp.StreamSource = ms;
                    bmp.EndInit();
                    bmp.Freeze();
                    _coverArtImage = bmp;
                }
                catch
                {
                    _coverArtImage = null;
                }
            }
            return _coverArtImage;
        }
    }

    public BitmapImage? GetHighResCoverArt()
    {
        if (_coverArtBytes is not { Length: > 0 }) return null;
        try
        {
            using var ms = new MemoryStream(_coverArtBytes);
            var bmp = new BitmapImage();
            bmp.BeginInit();
            bmp.CacheOption = BitmapCacheOption.OnLoad;
            bmp.StreamSource = ms;
            bmp.EndInit();
            bmp.Freeze();
            return bmp;
        }
        catch
        {
            return CoverArtImage;
        }
    }

    [JsonIgnore]
    public string DisplayTitle => !string.IsNullOrWhiteSpace(Title)
        ? Title!
        : Path.GetFileNameWithoutExtension(FilePath);

    [JsonIgnore]
    public string DisplayArtist => !string.IsNullOrWhiteSpace(Artist)
        ? Artist!
        : "Artista desconhecido";

    [JsonIgnore]
    public string DisplayAlbum => !string.IsNullOrWhiteSpace(Album)
        ? Album!
        : "Álbum desconhecido";

    [JsonIgnore]
    public string DisplayGenre => !string.IsNullOrWhiteSpace(Genre)
        ? Genre!
        : "Gênero desconhecido";

    [JsonIgnore]
    public string Subtitle => $"{DisplayArtist} • {DisplayAlbum}";

    [JsonIgnore]
    public string FolderPath => Path.GetDirectoryName(FilePath) ?? string.Empty;

    [JsonIgnore]
    public string FolderName
    {
        get
        {
            var dir = FolderPath;
            return string.IsNullOrEmpty(dir) ? "Raiz" : Path.GetFileName(dir);
        }
    }

    [JsonIgnore]
    public string Format => Path.GetExtension(FilePath).TrimStart('.').ToUpperInvariant();

    [JsonIgnore]
    public bool IsLossless => Format is "FLAC" or "WAV" or "ALAC" or "AIFF" or "APE" or "WV";

    [JsonIgnore]
    public string AudioQualityBadge
    {
        get
        {
            var fmt = string.IsNullOrEmpty(Format) ? "AUDIO" : Format;
            var parts = new List<string> { fmt };
            if (IsLossless)
            {
                parts.Add("LOSSLESS");
            }
            if (!string.IsNullOrWhiteSpace(Bitrate))
            {
                parts.Add($"{Bitrate} kbps");
            }
            if (SampleRate > 0)
            {
                parts.Add($"{SampleRate / 1000.0:0.#} kHz");
            }
            return string.Join(" • ", parts);
        }
    }

    [JsonIgnore]
    public string DurationFormatted => FormatTimeMs(DurationMs);

    [JsonIgnore]
    public string SizeFormatted
    {
        get
        {
            if (Size >= 1024 * 1024)
                return $"{Size / (1024.0 * 1024.0):0.00} MB";
            if (Size >= 1024)
                return $"{Size / 1024.0:0.0} KB";
            return $"{Size} B";
        }
    }

    public static string FormatTimeMs(long ms)
    {
        if (ms < 0) ms = 0;
        var ts = TimeSpan.FromMilliseconds(ms);
        return ts.TotalHours >= 1
            ? $"{(int)ts.TotalHours}:{ts.Minutes:D2}:{ts.Seconds:D2}"
            : $"{ts.Minutes:D2}:{ts.Seconds:D2}";
    }

    public bool MatchesFilter(string query)
    {
        if (string.IsNullOrWhiteSpace(query)) return true;
        var q = query.Trim();
        return DisplayTitle.Contains(q, StringComparison.OrdinalIgnoreCase)
               || DisplayArtist.Contains(q, StringComparison.OrdinalIgnoreCase)
               || DisplayAlbum.Contains(q, StringComparison.OrdinalIgnoreCase)
               || DisplayGenre.Contains(q, StringComparison.OrdinalIgnoreCase)
               || (AlbumArtist?.Contains(q, StringComparison.OrdinalIgnoreCase) ?? false)
               || (Year?.Contains(q, StringComparison.OrdinalIgnoreCase) ?? false)
               || FolderName.Contains(q, StringComparison.OrdinalIgnoreCase);
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    protected void OnPropertyChanged([CallerMemberName] string? name = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}

public static class TrackSortExtensions
{
    public static List<Track> SortTracks(this IEnumerable<Track> tracks, TrackSort sort, SortOrder order)
    {
        IOrderedEnumerable<Track> ordered = sort switch
        {
            TrackSort.Title => order == SortOrder.Ascending
                ? tracks.OrderBy(t => t.DisplayTitle, StringComparer.CurrentCultureIgnoreCase)
                : tracks.OrderByDescending(t => t.DisplayTitle, StringComparer.CurrentCultureIgnoreCase),
            TrackSort.Album => order == SortOrder.Ascending
                ? tracks.OrderBy(t => t.DisplayAlbum, StringComparer.CurrentCultureIgnoreCase).ThenBy(ParseTrackNum)
                : tracks.OrderByDescending(t => t.DisplayAlbum, StringComparer.CurrentCultureIgnoreCase).ThenBy(ParseTrackNum),
            TrackSort.Artist => order == SortOrder.Ascending
                ? tracks.OrderBy(t => t.DisplayArtist, StringComparer.CurrentCultureIgnoreCase).ThenBy(t => t.DisplayTitle)
                : tracks.OrderByDescending(t => t.DisplayArtist, StringComparer.CurrentCultureIgnoreCase).ThenBy(t => t.DisplayTitle),
            TrackSort.Genre => order == SortOrder.Ascending
                ? tracks.OrderBy(t => t.DisplayGenre, StringComparer.CurrentCultureIgnoreCase)
                : tracks.OrderByDescending(t => t.DisplayGenre, StringComparer.CurrentCultureIgnoreCase),
            TrackSort.Year => order == SortOrder.Ascending
                ? tracks.OrderBy(t => t.Year ?? "")
                : tracks.OrderByDescending(t => t.Year ?? ""),
            TrackSort.TrackNumber => order == SortOrder.Ascending
                ? tracks.OrderBy(ParseTrackNum)
                : tracks.OrderByDescending(ParseTrackNum),
            TrackSort.DateModified => order == SortOrder.Ascending
                ? tracks.OrderBy(t => t.DateModified)
                : tracks.OrderByDescending(t => t.DateModified),
            _ => tracks.OrderBy(t => t.DisplayTitle, StringComparer.CurrentCultureIgnoreCase)
        };

        return ordered.ToList();
    }

    private static int ParseTrackNum(Track t)
    {
        if (string.IsNullOrWhiteSpace(t.TrackNumber)) return int.MaxValue;
        var part = t.TrackNumber.Split('/')[0].Trim();
        return int.TryParse(part, out var n) ? n : int.MaxValue;
    }
}
