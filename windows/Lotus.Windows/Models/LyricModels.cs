using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Text;
using System.Text.RegularExpressions;

namespace Lotus.Windows.Models;

public class Lyrics
{
    public string Uri { get; set; } = string.Empty;
    public bool AreFromRemote { get; set; } = true;
    public List<string>? Plain { get; set; }
    public List<(int TimestampMs, string Text)>? Synced { get; set; }
    public List<LyricLine>? Lines { get; set; }

    public List<LyricLine> ResolvedLines =>
        Lines ?? (Synced != null ? LyricParser.ToLyricLines(Synced) : new List<LyricLine>());

    public bool HasSynced => (Synced is { Count: > 0 }) || (Lines is { Count: > 0 });
    public bool HasPlain => Plain is { Count: > 0 };
}

public class LyricWord : INotifyPropertyChanged
{
    private LyricWordState _state = LyricWordState.Upcoming;
    private double _progress;

    public long StartTimeMs { get; set; }
    public long EndTimeMs { get; set; }
    public string Text { get; set; } = string.Empty;
    public bool TrailingSpace { get; set; }

    public string DisplayText => TrailingSpace ? Text + " " : Text;

    public LyricWordState State
    {
        get => _state;
        set
        {
            if (_state != value)
            {
                _state = value;
                OnPropertyChanged();
            }
        }
    }

    public double Progress
    {
        get => _progress;
        set
        {
            if (Math.Abs(_progress - value) > 0.01)
            {
                _progress = value;
                OnPropertyChanged();
            }
        }
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    protected void OnPropertyChanged([CallerMemberName] string? name = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}

public class ClusteredWord
{
    public List<LyricWord> Words { get; set; } = new();
    public bool TrailingSpace { get; set; } = true;
}

public class LyricLine : INotifyPropertyChanged
{
    private bool _isActive;
    private bool _isPassed;
    private double _lineProgress;

    public int Index { get; set; }
    public long StartTimeMs { get; set; }
    public long EndTimeMs { get; set; }
    public string Text { get; set; } = string.Empty;
    public List<LyricWord> Words { get; set; } = new();
    public bool IsInstrumental { get; set; }

    public bool HasWords => Words.Count > 0;
    public string DisplayText => IsInstrumental ? "•••" : Text;
    public string TimestampText => Track.FormatTimeMs(StartTimeMs);

    public bool IsActive
    {
        get => _isActive;
        set
        {
            if (_isActive != value)
            {
                _isActive = value;
                OnPropertyChanged();
            }
        }
    }

    public bool IsPassed
    {
        get => _isPassed;
        set
        {
            if (_isPassed != value)
            {
                _isPassed = value;
                OnPropertyChanged();
            }
        }
    }

    public double LineProgress
    {
        get => _lineProgress;
        set
        {
            if (Math.Abs(_lineProgress - value) > 0.01)
            {
                _lineProgress = value;
                OnPropertyChanged();
            }
        }
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    protected void OnPropertyChanged([CallerMemberName] string? name = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}

public static class LyricParser
{
    private static readonly Regex LineTagRegex = new(@"^\[(\d+):(\d+)(?:\.(\d+))?\](.*)$", RegexOptions.Compiled);
    private static readonly Regex InlineTagRegex = new(@"[<(\[](\d+):(\d+)\.(\d+)[>)\]]", RegexOptions.Compiled);
    private static readonly Regex KugouTagRegex = new(@"<(\d+),(\d+)(?:,\d+)?>", RegexOptions.Compiled);
    private static readonly Regex StripTagRegex = new(@"<[^>]+>", RegexOptions.Compiled);

    public static List<(int TimestampMs, string Text)> ParseSyncedLyrics(string rawLrc)
    {
        if (string.IsNullOrWhiteSpace(rawLrc))
            throw new ArgumentException("Empty lyrics string.");

        var result = new List<(int TimestampMs, string Text)>();
        var lines = rawLrc.Replace("\r\n", "\n").Split('\n');

        foreach (var raw in lines)
        {
            var trimmed = raw.Trim();
            var match = LineTagRegex.Match(trimmed);
            if (!match.Success) continue;

            if (!int.TryParse(match.Groups[1].Value, out var minutes)) continue;
            if (!int.TryParse(match.Groups[2].Value, out var seconds)) continue;

            var fracStr = match.Groups[3].Success ? match.Groups[3].Value : "0";
            int millis = 0;
            if (fracStr.Length == 1) millis = int.Parse(fracStr) * 100;
            else if (fracStr.Length == 2) millis = int.Parse(fracStr) * 10;
            else if (fracStr.Length >= 3) int.TryParse(fracStr[..3], out millis);

            var timestamp = minutes * 60_000 + seconds * 1_000 + millis;
            var content = match.Groups[4].Value.Trim();
            result.Add((timestamp, content));
        }

        if (result.Count == 0)
            throw new ArgumentException("Synced lines not found.");

        return result;
    }

    public static string SyncedLyricsToString(IEnumerable<(int TimestampMs, string Text)> synced)
    {
        var sb = new StringBuilder();
        foreach (var (ms, text) in synced)
        {
            var mins = (ms / 60000).ToString("D2");
            var secs = ((ms % 60000) / 1000).ToString("D2");
            var centis = ((ms % 1000) / 10).ToString("D2");
            sb.AppendLine($"[{mins}:{secs}.{centis}] {text}");
        }
        return sb.ToString();
    }

    public static List<ClusteredWord> ClusterSyncedWords(List<LyricWord> words)
    {
        if (words.Count == 0) return new List<ClusteredWord>();

        var clusters = new List<ClusteredWord>();
        var current = new List<LyricWord>();

        for (int i = 0; i < words.Count; i++)
        {
            var word = words[i];
            current.Add(word);
            bool hasTrailingSpace = word.TrailingSpace || word.Text.EndsWith(' ') || word.Text.EndsWith('\t');
            bool isLast = i == words.Count - 1;

            if (hasTrailingSpace || isLast)
            {
                clusters.Add(new ClusteredWord
                {
                    Words = current.ToList(),
                    TrailingSpace = hasTrailingSpace
                });
                current.Clear();
            }
        }

        if (current.Count > 0)
        {
            clusters.Add(new ClusteredWord
            {
                Words = current.ToList(),
                TrailingSpace = false
            });
        }

        return clusters;
    }

    public static long ResolveLineEndTimeMs(LyricLine line, long? nextLineStartMs, long trackDurationMs = long.MaxValue)
    {
        var lastWordEnd = line.Words.LastOrDefault()?.EndTimeMs;
        if (lastWordEnd.HasValue && lastWordEnd.Value > line.StartTimeMs)
        {
            return nextLineStartMs.HasValue
                ? Math.Min(lastWordEnd.Value + 400L, nextLineStartMs.Value)
                : lastWordEnd.Value + 800L;
        }

        if (nextLineStartMs.HasValue)
        {
            var durationUntilNext = nextLineStartMs.Value - line.StartTimeMs;
            if (durationUntilNext > 7000L)
            {
                var estimated = Math.Clamp(line.Text.Length * 85L, 3000L, 6500L);
                return Math.Min(line.StartTimeMs + estimated, nextLineStartMs.Value - 500L);
            }
            return Math.Max(line.StartTimeMs + 800L, nextLineStartMs.Value - 150L);
        }

        var lastEstimated = Math.Clamp(line.Text.Length * 85L, 3000L, 6000L);
        return Math.Min(line.StartTimeMs + lastEstimated, trackDurationMs);
    }

    public static (string CleanText, List<LyricWord> Words) ParseLineWords(
        long lineStartTimeMs,
        string rawLine,
        long? nextLineStartMs)
    {
        if (InlineTagRegex.IsMatch(rawLine))
        {
            var words = new List<LyricWord>();
            var matches = InlineTagRegex.Matches(rawLine);
            var sb = new StringBuilder();

            for (int i = 0; i < matches.Count; i++)
            {
                var m = matches[i];
                long mins = long.TryParse(m.Groups[1].Value, out var mv) ? mv : 0L;
                long secs = long.TryParse(m.Groups[2].Value, out var sv) ? sv : 0L;
                long centis = long.TryParse(m.Groups[3].Value, out var cv) ? cv : 0L;
                long wordStart = mins * 60000L + secs * 1000L + centis * 10L;

                int textStart = m.Index + m.Length;
                int textEnd = i < matches.Count - 1 ? matches[i + 1].Index : rawLine.Length;
                string chunk = textStart < textEnd ? rawLine.Substring(textStart, textEnd - textStart) : string.Empty;

                long nextWordStart;
                if (i < matches.Count - 1)
                {
                    var nm = matches[i + 1];
                    long nmin = long.TryParse(nm.Groups[1].Value, out var nmv) ? nmv : 0L;
                    long nsec = long.TryParse(nm.Groups[2].Value, out var nsv) ? nsv : 0L;
                    long ncen = long.TryParse(nm.Groups[3].Value, out var ncv) ? ncv : 0L;
                    nextWordStart = nmin * 60000L + nsec * 1000L + ncen * 10L;
                }
                else
                {
                    nextWordStart = nextLineStartMs ?? (wordStart + 800L);
                }

                long wordEnd = Math.Max(wordStart + 100L, Math.Min(wordStart + 2000L, nextWordStart));
                bool hasTrailingSpace = chunk.EndsWith(' ');
                string cleanWord = chunk.Trim();

                if (!string.IsNullOrEmpty(cleanWord))
                {
                    sb.Append(cleanWord).Append(hasTrailingSpace ? " " : "");
                    words.Add(new LyricWord
                    {
                        StartTimeMs = wordStart,
                        EndTimeMs = wordEnd,
                        Text = cleanWord,
                        TrailingSpace = hasTrailingSpace
                    });
                }
            }

            string fullClean = sb.ToString().Trim();
            if (words.Count > 0)
                return (fullClean, words);
        }
        else if (KugouTagRegex.IsMatch(rawLine))
        {
            var words = new List<LyricWord>();
            var matches = KugouTagRegex.Matches(rawLine);
            var sb = new StringBuilder();

            for (int i = 0; i < matches.Count; i++)
            {
                var m = matches[i];
                long offsetMs = long.TryParse(m.Groups[1].Value, out var ov) ? ov : 0L;
                long durationMs = long.TryParse(m.Groups[2].Value, out var dv) ? dv : 300L;
                long wordStart = lineStartTimeMs + offsetMs;
                long wordEnd = wordStart + durationMs;

                int textStart = m.Index + m.Length;
                int textEnd = i < matches.Count - 1 ? matches[i + 1].Index : rawLine.Length;
                string chunk = textStart < textEnd ? rawLine.Substring(textStart, textEnd - textStart) : string.Empty;

                bool hasTrailingSpace = chunk.EndsWith(' ');
                string cleanWord = chunk.Trim();

                if (!string.IsNullOrEmpty(cleanWord))
                {
                    sb.Append(cleanWord).Append(hasTrailingSpace ? " " : "");
                    words.Add(new LyricWord
                    {
                        StartTimeMs = wordStart,
                        EndTimeMs = wordEnd,
                        Text = cleanWord,
                        TrailingSpace = hasTrailingSpace
                    });
                }
            }

            string fullClean = sb.ToString().Trim();
            if (words.Count > 0)
                return (fullClean, words);
        }

        string cleanLine = StripTagRegex.Replace(rawLine, "").Trim();
        return (cleanLine, new List<LyricWord>());
    }

    public static List<LyricLine> ToLyricLines(
        List<(int TimestampMs, string Text)> synced,
        long trackDurationMs = long.MaxValue)
    {
        if (synced.Count == 0) return new List<LyricLine>();

        var result = new List<LyricLine>();
        var sorted = synced.OrderBy(x => x.TimestampMs).ToList();

        long firstTime = sorted[0].TimestampMs;
        if (firstTime > 4000L)
        {
            result.Add(new LyricLine
            {
                StartTimeMs = 0L,
                EndTimeMs = firstTime - 400L,
                Text = "",
                IsInstrumental = true
            });
        }

        for (int i = 0; i < sorted.Count; i++)
        {
            var (timeInt, rawText) = sorted[i];
            long startTime = timeInt;
            long? nextTime = i + 1 < sorted.Count ? sorted[i + 1].TimestampMs : null;

            var (cleanText, words) = ParseLineWords(startTime, rawText, nextTime);
            bool isInstrumental = string.IsNullOrWhiteSpace(cleanText);

            var line = new LyricLine
            {
                StartTimeMs = startTime,
                Text = cleanText,
                Words = words,
                IsInstrumental = isInstrumental
            };
            line.EndTimeMs = ResolveLineEndTimeMs(line, nextTime, trackDurationMs);
            result.Add(line);

            if (nextTime.HasValue && (nextTime.Value - line.EndTimeMs) > 7000L && !isInstrumental)
            {
                result.Add(new LyricLine
                {
                    StartTimeMs = line.EndTimeMs + 400L,
                    EndTimeMs = nextTime.Value - 400L,
                    Text = "",
                    IsInstrumental = true
                });
            }
        }

        for (int i = 0; i < result.Count; i++)
        {
            result[i].Index = i;
        }

        return result;
    }
}
