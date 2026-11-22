using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Text.Json.Serialization;
using System.Windows.Media.Imaging;

namespace Lotus.Windows.Models;

public class Playlist : INotifyPropertyChanged
{
    private string _name = string.Empty;
    private List<Track> _tracks = new();

    public string Id { get; set; } = Guid.NewGuid().ToString("N");

    public string Name
    {
        get => _name;
        set { _name = value; OnPropertyChanged(); }
    }

    public bool IsUserEditable { get; set; } = true;
    public bool IsDynamic { get; set; }
    public string Category { get; set; } = "Playlist";
    public string? SubtitleOverride { get; set; }

    public List<string> TrackPaths { get; set; } = new();

    [JsonIgnore]
    public List<Track> Tracks
    {
        get => _tracks;
        set
        {
            _tracks = value ?? new List<Track>();
            OnPropertyChanged();
            OnPropertyChanged(nameof(TrackCount));
            OnPropertyChanged(nameof(TrackCountText));
            OnPropertyChanged(nameof(TotalDurationText));
            OnPropertyChanged(nameof(CoverArtImage));
            OnPropertyChanged(nameof(HasCoverArt));
            OnPropertyChanged(nameof(Subtitle));
        }
    }

    [JsonIgnore]
    public int TrackCount => _tracks.Count;

    [JsonIgnore]
    public string TrackCountText => _tracks.Count == 1 ? "1 faixa" : $"{_tracks.Count} faixas";

    [JsonIgnore]
    public string TotalDurationText
    {
        get
        {
            var totalMs = _tracks.Sum(t => (long)t.DurationMs);
            var ts = TimeSpan.FromMilliseconds(totalMs);
            if (ts.TotalHours >= 1)
                return $"{(int)ts.TotalHours}h {ts.Minutes}m";
            return $"{ts.Minutes} min";
        }
    }

    [JsonIgnore]
    public string Subtitle => !string.IsNullOrWhiteSpace(SubtitleOverride)
        ? $"{SubtitleOverride} • {TrackCountText}"
        : $"{TrackCountText} • {TotalDurationText}";

    [JsonIgnore]
    public BitmapImage? CoverArtImage => _tracks.FirstOrDefault(t => t.HasCoverArt)?.CoverArtImage;

    [JsonIgnore]
    public bool HasCoverArt => CoverArtImage != null;

    public bool MatchesFilter(string query)
    {
        if (string.IsNullOrWhiteSpace(query)) return true;
        var q = query.Trim();
        return Name.Contains(q, StringComparison.OrdinalIgnoreCase)
               || (SubtitleOverride?.Contains(q, StringComparison.OrdinalIgnoreCase) ?? false)
               || _tracks.Any(t => t.MatchesFilter(q));
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    protected void OnPropertyChanged([CallerMemberName] string? name = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}

public static class PlaylistSortExtensions
{
    public static List<Playlist> SortPlaylists(this IEnumerable<Playlist> playlists, PlaylistSort sort, SortOrder order)
    {
        return (sort, order) switch
        {
            (PlaylistSort.Title, SortOrder.Ascending) => playlists.OrderBy(p => p.Name, StringComparer.CurrentCultureIgnoreCase).ToList(),
            (PlaylistSort.Title, SortOrder.Descending) => playlists.OrderByDescending(p => p.Name, StringComparer.CurrentCultureIgnoreCase).ToList(),
            (PlaylistSort.TrackCount, SortOrder.Ascending) => playlists.OrderBy(p => p.TrackCount).ThenBy(p => p.Name).ToList(),
            (PlaylistSort.TrackCount, SortOrder.Descending) => playlists.OrderByDescending(p => p.TrackCount).ThenBy(p => p.Name).ToList(),
            _ => playlists.ToList()
        };
    }
}
