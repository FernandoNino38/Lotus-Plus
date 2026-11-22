using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using Lotus.Windows.Models;

namespace Lotus.Windows.Services;

public class PersistenceService
{
    private readonly string _appDataDir;
    private readonly string _settingsPath;
    private readonly string _playlistsPath;
    private readonly string _playStatsPath;
    private readonly string _lyricsCachePath;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        PropertyNameCaseInsensitive = true
    };

    public PersistenceService()
    {
        _appDataDir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Lotus");
        Directory.CreateDirectory(_appDataDir);

        _settingsPath = Path.Combine(_appDataDir, "settings.json");
        _playlistsPath = Path.Combine(_appDataDir, "playlists.json");
        _playStatsPath = Path.Combine(_appDataDir, "play_stats.json");
        _lyricsCachePath = Path.Combine(_appDataDir, "lyrics_cache.json");
    }

    public AppSettings LoadSettings()
    {
        try
        {
            if (File.Exists(_settingsPath))
            {
                var json = File.ReadAllText(_settingsPath);
                return JsonSerializer.Deserialize<AppSettings>(json, JsonOptions) ?? new AppSettings();
            }
        }
        catch
        {
            // Ignore corrupt settings file
        }
        return new AppSettings();
    }

    public void SaveSettings(AppSettings settings)
    {
        try
        {
            var json = JsonSerializer.Serialize(settings, JsonOptions);
            File.WriteAllText(_settingsPath, json);
        }
        catch
        {
            // Ignore write errors
        }
    }

    public List<Playlist> LoadCustomPlaylists()
    {
        try
        {
            if (File.Exists(_playlistsPath))
            {
                var json = File.ReadAllText(_playlistsPath);
                return JsonSerializer.Deserialize<List<Playlist>>(json, JsonOptions) ?? new List<Playlist>();
            }
        }
        catch
        {
            // Ignore
        }
        return new List<Playlist>();
    }

    public void SaveCustomPlaylists(IEnumerable<Playlist> playlists)
    {
        try
        {
            var list = playlists.Select(p => new Playlist
            {
                Id = p.Id,
                Name = p.Name,
                IsUserEditable = true,
                IsDynamic = false,
                Category = "Playlist",
                TrackPaths = p.Tracks.Select(t => t.FilePath).ToList()
            }).ToList();

            var json = JsonSerializer.Serialize(list, JsonOptions);
            File.WriteAllText(_playlistsPath, json);
        }
        catch
        {
            // Ignore
        }
    }

    public Dictionary<string, int> LoadPlayCounts()
    {
        try
        {
            if (File.Exists(_playStatsPath))
            {
                var json = File.ReadAllText(_playStatsPath);
                return JsonSerializer.Deserialize<Dictionary<string, int>>(json, JsonOptions)
                       ?? new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);
            }
        }
        catch
        {
            // Ignore
        }
        return new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);
    }

    public void SavePlayCounts(Dictionary<string, int> counts)
    {
        try
        {
            var json = JsonSerializer.Serialize(counts, JsonOptions);
            File.WriteAllText(_playStatsPath, json);
        }
        catch
        {
            // Ignore
        }
    }

    public Dictionary<string, CachedLyricsEntry> LoadLyricsCache()
    {
        try
        {
            if (File.Exists(_lyricsCachePath))
            {
                var json = File.ReadAllText(_lyricsCachePath);
                return JsonSerializer.Deserialize<Dictionary<string, CachedLyricsEntry>>(json, JsonOptions)
                       ?? new Dictionary<string, CachedLyricsEntry>(StringComparer.OrdinalIgnoreCase);
            }
        }
        catch
        {
            // Ignore
        }
        return new Dictionary<string, CachedLyricsEntry>(StringComparer.OrdinalIgnoreCase);
    }

    public void SaveLyricsCache(Dictionary<string, CachedLyricsEntry> cache)
    {
        try
        {
            var json = JsonSerializer.Serialize(cache, JsonOptions);
            File.WriteAllText(_lyricsCachePath, json);
        }
        catch
        {
            // Ignore
        }
    }

    public Playlist? ImportM3uPlaylist(string m3uFilePath, IReadOnlyList<Track> allTracks)
    {
        try
        {
            if (!File.Exists(m3uFilePath)) return null;
            var baseDir = Path.GetDirectoryName(m3uFilePath) ?? string.Empty;
            var lines = File.ReadAllLines(m3uFilePath);
            var trackMap = allTracks.ToDictionary(t => t.FilePath, t => t, StringComparer.OrdinalIgnoreCase);
            var fileNameMap = allTracks
                .GroupBy(t => Path.GetFileName(t.FilePath), StringComparer.OrdinalIgnoreCase)
                .ToDictionary(g => g.Key, g => g.First(), StringComparer.OrdinalIgnoreCase);

            var matchedTracks = new List<Track>();
            foreach (var raw in lines)
            {
                var line = raw.Trim();
                if (string.IsNullOrEmpty(line) || line.StartsWith('#')) continue;

                string fullPath = Path.IsPathRooted(line)
                    ? line
                    : Path.GetFullPath(Path.Combine(baseDir, line));

                if (trackMap.TryGetValue(fullPath, out var exactTrack))
                {
                    matchedTracks.Add(exactTrack);
                }
                else if (fileNameMap.TryGetValue(Path.GetFileName(line), out var byNameTrack))
                {
                    matchedTracks.Add(byNameTrack);
                }
            }

            return new Playlist
            {
                Name = Path.GetFileNameWithoutExtension(m3uFilePath),
                IsUserEditable = true,
                Tracks = matchedTracks,
                TrackPaths = matchedTracks.Select(t => t.FilePath).ToList()
            };
        }
        catch
        {
            return null;
        }
    }

    public bool ExportM3uPlaylist(Playlist playlist, string targetPath)
    {
        try
        {
            var sb = new StringBuilder();
            sb.AppendLine("#EXTM3U");
            foreach (var track in playlist.Tracks)
            {
                sb.AppendLine($"#EXTINF:{track.DurationMs / 1000},{track.DisplayArtist} - {track.DisplayTitle}");
                sb.AppendLine(track.FilePath);
            }
            File.WriteAllText(targetPath, sb.ToString(), Encoding.UTF8);
            return true;
        }
        catch
        {
            return false;
        }
    }
}

public class CachedLyricsEntry
{
    public string Uri { get; set; } = string.Empty;
    public bool AreFromRemote { get; set; } = true;
    public List<string>? Plain { get; set; }
    public string? RawSyncedLrc { get; set; }

    public Lyrics ToDomainLyrics()
    {
        List<(int, string)>? synced = null;
        if (!string.IsNullOrWhiteSpace(RawSyncedLrc))
        {
            try
            {
                synced = LyricParser.ParseSyncedLyrics(RawSyncedLrc);
            }
            catch
            {
                synced = null;
            }
        }

        return new Lyrics
        {
            Uri = Uri,
            AreFromRemote = AreFromRemote,
            Plain = Plain,
            Synced = synced
        };
    }

    public static CachedLyricsEntry FromDomainLyrics(Lyrics lyrics)
    {
        return new CachedLyricsEntry
        {
            Uri = lyrics.Uri,
            AreFromRemote = lyrics.AreFromRemote,
            Plain = lyrics.Plain,
            RawSyncedLrc = lyrics.Synced != null ? LyricParser.SyncedLyricsToString(lyrics.Synced) : null
        };
    }
}
