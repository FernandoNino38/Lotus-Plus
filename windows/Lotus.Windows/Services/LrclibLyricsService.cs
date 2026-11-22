using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;
using Lotus.Windows.Models;

namespace Lotus.Windows.Services;

public class LrclibLyricsService
{
    private const string BaseUrl = "https://lrclib.net/api";
    private const string UserAgent = "Lotus/1.2.1 ( dev.dn0ne@gmail.com )";
    private readonly HttpClient _httpClient;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    public LrclibLyricsService()
    {
        _httpClient = new HttpClient
        {
            Timeout = TimeSpan.FromSeconds(20)
        };
        _httpClient.DefaultRequestHeaders.UserAgent.TryParseAdd(UserAgent);
        _httpClient.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
    }

    public async Task<(Lyrics? Lyrics, string? ErrorMessage)> GetLyricsAsync(
        Track track,
        CancellationToken cancellationToken = default)
    {
        var title = track.Title ?? track.DisplayTitle;
        var artist = track.Artist;

        if (string.IsNullOrWhiteSpace(title) || string.IsNullOrWhiteSpace(artist))
        {
            return (null, "A faixa precisa ter título e artista para buscar letras no LRCLIB.");
        }

        try
        {
            var queryParams = new List<string>
            {
                $"track_name={Uri.EscapeDataString(title)}",
                $"artist_name={Uri.EscapeDataString(artist)}"
            };

            if (!string.IsNullOrWhiteSpace(track.Album))
            {
                queryParams.Add($"album_name={Uri.EscapeDataString(track.Album)}");
            }

            if (track.DurationMs > 0)
            {
                queryParams.Add($"duration={track.DurationMs / 1000}");
            }

            var getUrl = $"{BaseUrl}/get?{string.Join("&", queryParams)}";
            using var response = await _httpClient.GetAsync(getUrl, cancellationToken);

            if (response.StatusCode == HttpStatusCode.OK)
            {
                var body = await response.Content.ReadAsStringAsync(cancellationToken);
                var dto = JsonSerializer.Deserialize<LrclibDto>(body, JsonOptions);
                var parsed = ConvertDtoToLyrics(track.FilePath, dto);
                if (parsed != null) return (parsed, null);
            }

            // Fallback: try /api/search if exact match wasn't found
            var searchUrl = $"{BaseUrl}/search?track_name={Uri.EscapeDataString(title)}&artist_name={Uri.EscapeDataString(artist)}";
            using var searchResp = await _httpClient.GetAsync(searchUrl, cancellationToken);
            if (searchResp.StatusCode == HttpStatusCode.OK)
            {
                var body = await searchResp.Content.ReadAsStringAsync(cancellationToken);
                var list = JsonSerializer.Deserialize<List<LrclibDto>>(body, JsonOptions);
                var best = list?.FirstOrDefault(x => !string.IsNullOrWhiteSpace(x.SyncedLyrics))
                           ?? list?.FirstOrDefault(x => !string.IsNullOrWhiteSpace(x.PlainLyrics));
                var parsed = ConvertDtoToLyrics(track.FilePath, best);
                if (parsed != null) return (parsed, null);
            }

            return (null, "Letra não encontrada no LRCLIB.");
        }
        catch (TaskCanceledException)
        {
            return (null, "Tempo esgotado ao buscar letras no LRCLIB.");
        }
        catch (HttpRequestException)
        {
            return (null, "Sem conexão com a internet ou falha ao acessar o LRCLIB.");
        }
        catch (Exception ex)
        {
            return (null, $"Erro ao buscar letras: {ex.Message}");
        }
    }

    public async Task<(bool Success, string Message)> PublishLyricsAsync(
        Track track,
        Lyrics lyrics,
        IProgress<string>? statusProgress = null,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(track.Title) ||
            string.IsNullOrWhiteSpace(track.Artist) ||
            string.IsNullOrWhiteSpace(track.Album) ||
            lyrics.Plain == null)
        {
            return (false, "Título, Artista, Álbum e Letra são obrigatórios para publicar no LRCLIB.");
        }

        try
        {
            statusProgress?.Report("Solicitando desafio criptográfico ao LRCLIB...");
            using var challengeResp = await _httpClient.PostAsync($"{BaseUrl}/request-challenge", null, cancellationToken);
            if (!challengeResp.IsSuccessStatusCode)
            {
                return (false, "Falha ao solicitar desafio do LRCLIB.");
            }

            var challengeJson = await challengeResp.Content.ReadAsStringAsync(cancellationToken);
            var challenge = JsonSerializer.Deserialize<ChallengeDto>(challengeJson, JsonOptions);
            if (challenge == null || string.IsNullOrEmpty(challenge.Prefix) || string.IsNullOrEmpty(challenge.Target))
            {
                return (false, "Resposta de desafio inválida do LRCLIB.");
            }

            statusProgress?.Report("Resolvendo desafio Proof-of-Work SHA-256...");
            var nonce = await Task.Run(() => SolveChallenge(challenge.Prefix, challenge.Target, cancellationToken), cancellationToken);

            statusProgress?.Report("Enviando letra para o LRCLIB...");
            var payload = new PostLyricsDto
            {
                TrackName = track.Title,
                ArtistName = track.Artist,
                AlbumName = track.Album,
                Duration = track.DurationMs / 1000,
                PlainLyrics = string.Join("\n", lyrics.Plain),
                SyncedLyrics = lyrics.Synced != null ? LyricParser.SyncedLyricsToString(lyrics.Synced) : ""
            };

            var req = new HttpRequestMessage(HttpMethod.Post, $"{BaseUrl}/publish");
            req.Headers.Add("X-Publish-Token", $"{challenge.Prefix}:{nonce}");
            req.Content = new StringContent(
                JsonSerializer.Serialize(payload),
                Encoding.UTF8,
                "application/json");

            using var pubResp = await _httpClient.SendAsync(req, cancellationToken);
            if (pubResp.StatusCode == HttpStatusCode.Created || pubResp.IsSuccessStatusCode)
            {
                return (true, "Letra publicada com sucesso no LRCLIB!");
            }

            return (false, $"Falha ao publicar no LRCLIB (HTTP {(int)pubResp.StatusCode}).");
        }
        catch (Exception ex)
        {
            return (false, $"Erro ao publicar letra: {ex.Message}");
        }
    }

    private static Lyrics? ConvertDtoToLyrics(string uri, LrclibDto? dto)
    {
        if (dto == null) return null;
        if (string.IsNullOrWhiteSpace(dto.PlainLyrics) && string.IsNullOrWhiteSpace(dto.SyncedLyrics))
            return null;

        List<string>? plain = !string.IsNullOrWhiteSpace(dto.PlainLyrics)
            ? dto.PlainLyrics.Replace("\r\n", "\n").Split('\n').ToList()
            : null;

        List<(int, string)>? synced = null;
        if (!string.IsNullOrWhiteSpace(dto.SyncedLyrics))
        {
            try
            {
                synced = LyricParser.ParseSyncedLyrics(dto.SyncedLyrics);
                plain ??= synced.Select(s => s.Item2).ToList();
            }
            catch
            {
                synced = null;
            }
        }

        return new Lyrics
        {
            Uri = uri,
            AreFromRemote = true,
            Plain = plain,
            Synced = synced
        };
    }

    private static string SolveChallenge(string prefix, string targetHex, CancellationToken cancellationToken)
    {
        byte[] target = Convert.FromHexString(targetHex);
        long nonce = 0;

        while (true)
        {
            cancellationToken.ThrowIfCancellationRequested();
            byte[] input = Encoding.UTF8.GetBytes($"{prefix}{nonce}");
            byte[] hash = SHA256.HashData(input);

            if (VerifyNonce(hash, target))
                return nonce.ToString();

            nonce++;
        }
    }

    private static bool VerifyNonce(byte[] result, byte[] target)
    {
        if (result.Length != target.Length) return false;
        for (int i = 0; i < result.Length; i++)
        {
            if (result[i] > target[i]) return false;
            if (result[i] < target[i]) break;
        }
        return true;
    }

    private class LrclibDto
    {
        [JsonPropertyName("plainLyrics")]
        public string? PlainLyrics { get; set; }

        [JsonPropertyName("syncedLyrics")]
        public string? SyncedLyrics { get; set; }
    }

    private class ChallengeDto
    {
        [JsonPropertyName("prefix")]
        public string Prefix { get; set; } = string.Empty;

        [JsonPropertyName("target")]
        public string Target { get; set; } = string.Empty;
    }

    private class PostLyricsDto
    {
        [JsonPropertyName("trackName")]
        public string TrackName { get; set; } = string.Empty;

        [JsonPropertyName("artistName")]
        public string ArtistName { get; set; } = string.Empty;

        [JsonPropertyName("albumName")]
        public string AlbumName { get; set; } = string.Empty;

        [JsonPropertyName("duration")]
        public int Duration { get; set; }

        [JsonPropertyName("plainLyrics")]
        public string PlainLyrics { get; set; } = string.Empty;

        [JsonPropertyName("syncedLyrics")]
        public string SyncedLyrics { get; set; } = string.Empty;
    }
}
