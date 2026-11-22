using System;
using System.IO;
using System.Linq;
using Lotus.Windows.Models;
using TagLib;

namespace Lotus.Windows.Services;

public class MetadataService
{
    public Lyrics? ReadLocalLyrics(Track track)
    {
        try
        {
            // 1. Check for sidecar .lrc file first
            var lrcPath = Path.ChangeExtension(track.FilePath, ".lrc");
            if (System.IO.File.Exists(lrcPath))
            {
                var lrcText = System.IO.File.ReadAllText(lrcPath);
                if (!string.IsNullOrWhiteSpace(lrcText))
                {
                    return ParseLyricsText(track.FilePath, lrcText, areFromRemote: false);
                }
            }

            // 2. Check embedded tag lyrics (port of LyricsReaderImpl.kt)
            if (System.IO.File.Exists(track.FilePath))
            {
                using var tfile = TagLib.File.Create(track.FilePath);
                var lyricsText = tfile.Tag?.Lyrics;
                if (!string.IsNullOrWhiteSpace(lyricsText))
                {
                    return ParseLyricsText(track.FilePath, lyricsText, areFromRemote: false);
                }
            }
        }
        catch
        {
            // Ignore read errors
        }

        return null;
    }

    public static Lyrics ParseLyricsText(string uri, string rawText, bool areFromRemote)
    {
        var normalized = rawText.Replace("\r\n", "\n");
        var plainLines = normalized.Split('\n').ToList();
        System.Collections.Generic.List<(int, string)>? synced = null;

        try
        {
            synced = LyricParser.ParseSyncedLyrics(normalized);
            // If synced, also strip timestamps for the plain view
            plainLines = synced.Select(s => s.Item2).ToList();
        }
        catch
        {
            synced = null;
        }

        return new Lyrics
        {
            Uri = uri,
            AreFromRemote = areFromRemote,
            Plain = plainLines,
            Synced = synced
        };
    }

    public (bool Success, string? ErrorMessage) WriteMetadata(Track track, MetadataEditModel metadata)
    {
        try
        {
            if (!System.IO.File.Exists(track.FilePath))
                return (false, "Arquivo de áudio não encontrado no disco.");

            using (var tfile = TagLib.File.Create(track.FilePath))
            {
                var tag = tfile.Tag;

                if (metadata.Title != null)
                    tag.Title = metadata.Title.Trim();

                if (metadata.Artist != null)
                    tag.Performers = string.IsNullOrWhiteSpace(metadata.Artist)
                        ? Array.Empty<string>()
                        : new[] { metadata.Artist.Trim() };

                if (metadata.Album != null)
                    tag.Album = metadata.Album.Trim();

                if (metadata.AlbumArtist != null)
                    tag.AlbumArtists = string.IsNullOrWhiteSpace(metadata.AlbumArtist)
                        ? Array.Empty<string>()
                        : new[] { metadata.AlbumArtist.Trim() };

                if (metadata.Genre != null)
                    tag.Genres = string.IsNullOrWhiteSpace(metadata.Genre)
                        ? Array.Empty<string>()
                        : new[] { metadata.Genre.Trim() };

                if (metadata.Year != null)
                {
                    var yStr = metadata.Year.Trim();
                    if (yStr.Length >= 4 && uint.TryParse(yStr[..4], out var yVal))
                        tag.Year = yVal;
                    else if (uint.TryParse(yStr, out var yShort))
                        tag.Year = yShort;
                }

                if (metadata.TrackNumber != null)
                {
                    var tStr = metadata.TrackNumber.Split('/')[0].Trim();
                    if (uint.TryParse(tStr, out var tVal))
                        tag.Track = tVal;
                }

                if (metadata.CoverArtBytes is { Length: > 0 })
                {
                    string mime = DetectMimeType(metadata.CoverArtBytes);
                    var pic = new Picture(new ByteVector(metadata.CoverArtBytes))
                    {
                        Type = PictureType.FrontCover,
                        MimeType = mime,
                        Description = "Front Cover"
                    };
                    tag.Pictures = new IPicture[] { pic };
                }

                if (metadata.Lyrics != null)
                {
                    tag.Lyrics = metadata.Lyrics;
                }

                tfile.Save();
            }

            // Update Track in-memory properties immediately
            if (metadata.Title != null) track.Title = metadata.Title;
            if (metadata.Artist != null) track.Artist = metadata.Artist;
            if (metadata.Album != null) track.Album = metadata.Album;
            if (metadata.AlbumArtist != null) track.AlbumArtist = metadata.AlbumArtist;
            if (metadata.Genre != null) track.Genre = metadata.Genre;
            if (metadata.Year != null) track.Year = metadata.Year;
            if (metadata.TrackNumber != null) track.TrackNumber = metadata.TrackNumber;
            if (metadata.CoverArtBytes is { Length: > 0 }) track.CoverArtBytes = metadata.CoverArtBytes;

            return (true, null);
        }
        catch (IOException ex)
        {
            return (false, $"Não foi possível gravar o arquivo (pode estar em uso pelo player): {ex.Message}");
        }
        catch (Exception ex)
        {
            return (false, $"Erro ao salvar metadados: {ex.Message}");
        }
    }

    private static string DetectMimeType(byte[] bytes)
    {
        if (bytes.Length >= 4 && bytes[0] == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47)
            return "image/png";
        return "image/jpeg";
    }
}
