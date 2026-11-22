namespace Lotus.Windows.Models;

public enum LibraryTab
{
    Playlists = 0,
    Tracks = 1,
    Albums = 2,
    Artists = 3,
    Genres = 4,
    Folders = 5
}

public enum SortOrder
{
    Ascending = 0,
    Descending = 1
}

public enum TrackSort
{
    Title = 0,
    Album = 1,
    Artist = 2,
    Genre = 3,
    Year = 4,
    TrackNumber = 5,
    DateModified = 6
}

public enum PlaylistSort
{
    Title = 0,
    TrackCount = 1
}

public enum PlaybackMode
{
    Repeat = 0,
    RepeatOne = 1,
    Shuffle = 2
}

public enum AppAppearance
{
    System = 0,
    Light = 1,
    Dark = 2
}

public enum PaletteStyle
{
    TonalSpot = 0,
    Neutral = 1,
    Vibrant = 2,
    Expressive = 3,
    Monochrome = 4
}

public enum LyricWordState
{
    Inactive,
    Upcoming,
    Current,
    Passed
}
