using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Lotus.Windows.Models;

namespace Lotus.Windows.Services;

public class MusicScannerService
{
    public static readonly HashSet<string> SupportedExtensions = new(StringComparer.OrdinalIgnoreCase)
    {
        ".mp3", ".flac", ".ogg", ".wav", ".m4a", ".aac", ".wma", ".opus", ".aiff", ".aif", ".wv", ".ape"
    };

    public async Task<List<Track>> ScanLibraryAsync(
        AppSettings settings,
        Dictionary<string, int> playCounts,
        IProgress<(int Current, int Total, string CurrentFile)>? progress = null,
        CancellationToken cancellationToken = default)
    {
        return await Task.Run(() =>
        {
            var roots = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

            if (settings.ScanMusicFolder)
            {
                var musicDir = Environment.GetFolderPath(Environment.SpecialFolder.MyMusic);
                if (!string.IsNullOrWhiteSpace(musicDir) && Directory.Exists(musicDir))
                {
                    roots.Add(musicDir);
                }
            }

            foreach (var folder in settings.ExtraScanFolders)
            {
                if (!string.IsNullOrWhiteSpace(folder) && Directory.Exists(folder))
                {
                    roots.Add(folder);
                }
            }

            var excluded = settings.ExcludedScanFolders
                .Where(f => !string.IsNullOrWhiteSpace(f))
                .Select(f => Path.GetFullPath(f).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar))
                .ToList();

            var audioFiles = new List<string>();
            foreach (var root in roots)
            {
                CollectAudioFilesRecursive(root, excluded, audioFiles, cancellationToken);
            }

            audioFiles = audioFiles.Distinct(StringComparer.OrdinalIgnoreCase).ToList();
            var tracks = new List<Track>(audioFiles.Count);
            int total = audioFiles.Count;

            for (int i = 0; i < total; i++)
            {
                cancellationToken.ThrowIfCancellationRequested();
                var filePath = audioFiles[i];
                progress?.Report((i + 1, total, Path.GetFileName(filePath)));

                var track = ReadTrackFromFile(filePath, playCounts);
                if (track == null) continue;

                // Filter out short tracks (< 30s) when IgnoreShortTracks is enabled
                if (settings.IgnoreShortTracks && track.DurationMs > 0 && track.DurationMs < 30_000)
                {
                    continue;
                }

                tracks.Add(track);
            }

            return tracks;
        }, cancellationToken);
    }

    private static void CollectAudioFilesRecursive(
        string directory,
        List<string> excludedFolders,
        List<string> results,
        CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();

        try
        {
            var normalizedDir = Path.GetFullPath(directory)
                .TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);

            if (excludedFolders.Any(ex =>
                    normalizedDir.Equals(ex, StringComparison.OrdinalIgnoreCase) ||
                    normalizedDir.StartsWith(ex + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase)))
            {
                return;
            }

            foreach (var file in Directory.EnumerateFiles(directory))
            {
                var ext = Path.GetExtension(file);
                if (SupportedExtensions.Contains(ext))
                {
                    results.Add(file);
                }
            }

            foreach (var subDir in Directory.EnumerateDirectories(directory))
            {
                CollectAudioFilesRecursive(subDir, excludedFolders, results, cancellationToken);
            }
        }
        catch (UnauthorizedAccessException)
        {
            // Skip protected system folders
        }
        catch (IOException)
        {
            // Skip inaccessible directories
        }
    }

    public Track? ReadTrackFromFile(string filePath, Dictionary<string, int>? playCounts = null)
    {
        try
        {
            var info = new FileInfo(filePath);
            if (!info.Exists) return null;

            var track = new Track
            {
                FilePath = info.FullName,
                Size = info.Length,
                DateModified = new DateTimeOffset(info.LastWriteTimeUtc).ToUnixTimeSeconds(),
                PlayCount = playCounts != null && playCounts.TryGetValue(info.FullName, out var c) ? c : 0
            };

            try
            {
                using var tfile = TagLib.File.Create(info.FullName);
                var tag = tfile.Tag;
                var props = tfile.Properties;

                track.Title = NullIfBlank(tag.Title) ?? Path.GetFileNameWithoutExtension(info.Name);
                track.Artist = NullIfBlank(tag.JoinedPerformers) ?? NullIfBlank(tag.FirstAlbumArtist);
                track.Album = NullIfBlank(tag.Album);
                track.AlbumArtist = NullIfBlank(tag.FirstAlbumArtist) ?? NullIfBlank(tag.JoinedAlbumArtists);
                track.Genre = NullIfBlank(tag.FirstGenre) ?? NullIfBlank(tag.JoinedGenres);
                track.Year = tag.Year > 0 ? tag.Year.ToString() : null;
                track.TrackNumber = tag.Track > 0 ? tag.Track.ToString() : null;

                if (props != null)
                {
                    track.DurationMs = (int)Math.Max(0, props.Duration.TotalMilliseconds);
                    track.Bitrate = props.AudioBitrate > 0 ? props.AudioBitrate.ToString() : null;
                    track.SampleRate = props.AudioSampleRate;
                    track.BitsPerSample = props.BitsPerSample;
                }

                if (tag.Pictures is { Length: > 0 })
                {
                    var pic = tag.Pictures.FirstOrDefault(p => p.Type == TagLib.PictureType.FrontCover)
                              ?? tag.Pictures[0];
                    if (pic.Data is { Count: > 0 })
                    {
                        track.CoverArtBytes = pic.Data.Data;
                    }
                }
            }
            catch
            {
                // Fallback if TagLib cannot parse a file header
                track.Title = Path.GetFileNameWithoutExtension(info.Name);
            }

            // Also check for folder cover image (cover.jpg / folder.jpg) if no embedded artwork
            if (track.CoverArtBytes == null && info.DirectoryName != null)
            {
                var folderArt = FindFolderCoverArt(info.DirectoryName);
                if (folderArt != null)
                {
                    track.CoverArtBytes = folderArt;
                }
            }

            return track;
        }
        catch
        {
            return null;
        }
    }

    private static byte[]? FindFolderCoverArt(string directory)
    {
        string[] candidateNames = { "cover.jpg", "cover.png", "folder.jpg", "folder.png", "front.jpg", "album.jpg" };
        foreach (var candidate in candidateNames)
        {
            var path = Path.Combine(directory, candidate);
            if (File.Exists(path))
            {
                try
                {
                    return File.ReadAllBytes(path);
                }
                catch
                {
                    // Ignore
                }
            }
        }
        return null;
    }

    private static string? NullIfBlank(string? value) =>
        string.IsNullOrWhiteSpace(value) ? null : value.Trim();
}
