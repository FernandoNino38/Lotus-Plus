using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;
using Lotus.Windows.Models;

namespace Lotus.Windows.Services;

public class MusicBrainzMetadataService
{
    private const string MusicBrainzEndpoint = "https://musicbrainz.org/ws/2";
    private const string CoverArtArchiveEndpoint = "https://coverartarchive.org";
    private const string UserAgent = "Lotus/1.2.1 ( dev.dn0ne@gmail.com )";

    private readonly HttpClient _httpClient;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    public MusicBrainzMetadataService()
    {
        var handler = new HttpClientHandler
        {
            AllowAutoRedirect = true
        };
        _httpClient = new HttpClient(handler)
        {
            Timeout = TimeSpan.FromSeconds(25)
        };
        _httpClient.DefaultRequestHeaders.UserAgent.TryParseAdd(UserAgent);
        _httpClient.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
    }

    public async Task<(List<MetadataSearchResult> Results, string? ErrorMessage)> SearchMetadataAsync(
        string query,
        long trackDurationMs,
        bool matchDuration,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(query))
            return (new List<MetadataSearchResult>(), "Digite uma consulta para pesquisar no MusicBrainz.");

        try
        {
            // Respect MusicBrainz rate limit (1 req/sec as in MusicBrainzMetadataProvider.kt)
            await Task.Delay(600, cancellationToken);

            var fullQuery = query.Trim();
            if (matchDuration && trackDurationMs > 5000)
            {
                fullQuery += $" AND dur:[{trackDurationMs - 5000} TO {trackDurationMs + 5000}]";
            }

            var url = $"{MusicBrainzEndpoint}/recording?fmt=json&query={Uri.EscapeDataString(fullQuery)}";
            using var response = await _httpClient.GetAsync(url, cancellationToken);

            if (!response.IsSuccessStatusCode)
            {
                return (new List<MetadataSearchResult>(), $"MusicBrainz retornou HTTP {(int)response.StatusCode}.");
            }

            var json = await response.Content.ReadAsStringAsync(cancellationToken);
            var dto = JsonSerializer.Deserialize<SearchResultDto>(json, JsonOptions);
            if (dto?.Recordings == null)
            {
                return (new List<MetadataSearchResult>(), null);
            }

            var results = new List<MetadataSearchResult>();
            foreach (var rec in dto.Recordings)
            {
                var artist = rec.ArtistCredit != null
                    ? string.Concat(rec.ArtistCredit.Select(a => a.Name + (a.JoinPhrase ?? "")))
                    : "";
                var genres = rec.Tags?.Select(t => t.Name).Where(n => !string.IsNullOrWhiteSpace(n)).ToList();

                if (rec.Releases == null) continue;
                foreach (var rel in rec.Releases.Where(r => r.ArtistCredit != null && r.Media is { Count: > 0 }))
                {
                    var albumArtist = string.Concat(rel.ArtistCredit!.Select(a => a.Name + (a.JoinPhrase ?? "")));
                    var trackNum = rel.Media!.FirstOrDefault()?.Tracks?.FirstOrDefault()?.Number;

                    results.Add(new MetadataSearchResult
                    {
                        Id = rec.Id,
                        Title = rec.Title,
                        Artist = artist,
                        AlbumId = rel.Id,
                        Album = rel.Title,
                        AlbumArtist = string.IsNullOrWhiteSpace(albumArtist) ? artist : albumArtist,
                        TrackNumber = trackNum,
                        Year = rec.FirstReleaseDate,
                        Genres = genres,
                        Description = rec.Disambiguation,
                        AlbumDescription = rel.Disambiguation
                    });
                }
            }

            return (results, null);
        }
        catch (TaskCanceledException)
        {
            return (new List<MetadataSearchResult>(), "Tempo limite esgotado na busca do MusicBrainz.");
        }
        catch (HttpRequestException)
        {
            return (new List<MetadataSearchResult>(), "Erro de rede ao conectar ao MusicBrainz.");
        }
        catch (Exception ex)
        {
            return (new List<MetadataSearchResult>(), $"Erro ao buscar no MusicBrainz: {ex.Message}");
        }
    }

    public async Task<byte[]?> GetCoverArtBytesAsync(
        MetadataSearchResult searchResult,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(searchResult.AlbumId)) return null;

        try
        {
            var url = $"{CoverArtArchiveEndpoint}/release/{Uri.EscapeDataString(searchResult.AlbumId)}/front";
            using var response = await _httpClient.GetAsync(url, cancellationToken);
            if (response.IsSuccessStatusCode)
            {
                return await response.Content.ReadAsByteArrayAsync(cancellationToken);
            }
        }
        catch
        {
            // Cover art optional
        }

        return null;
    }

    private class SearchResultDto
    {
        [JsonPropertyName("recordings")]
        public List<RecordingDto>? Recordings { get; set; }
    }

    private class RecordingDto
    {
        [JsonPropertyName("id")]
        public string Id { get; set; } = string.Empty;

        [JsonPropertyName("title")]
        public string Title { get; set; } = string.Empty;

        [JsonPropertyName("artist-credit")]
        public List<ArtistCreditDto>? ArtistCredit { get; set; }

        [JsonPropertyName("disambiguation")]
        public string? Disambiguation { get; set; }

        [JsonPropertyName("first-release-date")]
        public string? FirstReleaseDate { get; set; }

        [JsonPropertyName("releases")]
        public List<ReleaseDto>? Releases { get; set; }

        [JsonPropertyName("tags")]
        public List<TagDto>? Tags { get; set; }
    }

    private class ArtistCreditDto
    {
        [JsonPropertyName("name")]
        public string Name { get; set; } = string.Empty;

        [JsonPropertyName("joinphrase")]
        public string? JoinPhrase { get; set; }
    }

    private class ReleaseDto
    {
        [JsonPropertyName("id")]
        public string Id { get; set; } = string.Empty;

        [JsonPropertyName("title")]
        public string Title { get; set; } = string.Empty;

        [JsonPropertyName("artist-credit")]
        public List<ArtistCreditDto>? ArtistCredit { get; set; }

        [JsonPropertyName("media")]
        public List<MediaDto>? Media { get; set; }

        [JsonPropertyName("disambiguation")]
        public string? Disambiguation { get; set; }
    }

    private class MediaDto
    {
        [JsonPropertyName("track")]
        public List<MediaTrackDto>? Tracks { get; set; }
    }

    private class MediaTrackDto
    {
        [JsonPropertyName("number")]
        public string? Number { get; set; }
    }

    private class TagDto
    {
        [JsonPropertyName("name")]
        public string Name { get; set; } = string.Empty;
    }
}
