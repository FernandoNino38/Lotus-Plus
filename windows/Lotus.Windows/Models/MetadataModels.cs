using System.Collections.Generic;

namespace Lotus.Windows.Models;

public class MetadataEditModel
{
    public string? Title { get; set; }
    public string? Artist { get; set; }
    public string? Album { get; set; }
    public string? AlbumArtist { get; set; }
    public string? Genre { get; set; }
    public string? Year { get; set; }
    public string? TrackNumber { get; set; }
    public byte[]? CoverArtBytes { get; set; }
    public string? Lyrics { get; set; }
}

public class MetadataSearchResult
{
    public string Id { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string Artist { get; set; } = string.Empty;
    public string AlbumId { get; set; } = string.Empty;
    public string Album { get; set; } = string.Empty;
    public string AlbumArtist { get; set; } = string.Empty;
    public string? TrackNumber { get; set; }
    public string? Year { get; set; }
    public List<string>? Genres { get; set; }
    public string? Description { get; set; }
    public string? AlbumDescription { get; set; }

    public string DisplaySummary =>
        $"{Artist} • {Album}" + (!string.IsNullOrWhiteSpace(Year) ? $" ({Year})" : "");

    public string GenresDisplay => Genres is { Count: > 0 } ? string.Join(", ", Genres) : "";
}
