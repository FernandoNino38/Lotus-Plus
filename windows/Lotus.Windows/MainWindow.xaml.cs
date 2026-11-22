using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using Microsoft.Win32;
using Lotus.Windows.Models;
using Lotus.Windows.ViewModels;

namespace Lotus.Windows;

public partial class MainWindow : Window
{
    private readonly MainViewModel _vm;
    private byte[]? _pendingEditedCoverBytes;

    public MainWindow()
    {
        InitializeComponent();
        _vm = new MainViewModel();
        DataContext = _vm;

        _vm.PropertyChanged += OnViewModelPropertyChanged;
        _vm.ActiveLyricLineChanged += OnActiveLyricLineChanged;

        Loaded += async (_, _) =>
        {
            SyncSettingsControls();
            UpdateTabVisibility();
            UpdatePlaybackControlsUi();
            await _vm.InitializeAsync();
            UpdateEmptyStateVisibility();
        };

        Closed += (_, _) => _vm.Dispose();
    }

    // Window Chrome Handlers
    private void MinimizeWindow_Click(object sender, RoutedEventArgs e)
    {
        WindowState = WindowState.Minimized;
    }

    private void MaximizeRestoreWindow_Click(object sender, RoutedEventArgs e)
    {
        WindowState = WindowState == WindowState.Maximized
            ? WindowState.Normal
            : WindowState.Maximized;
    }

    private void CloseWindow_Click(object sender, RoutedEventArgs e)
    {
        Close();
    }

    private void OnViewModelPropertyChanged(object? sender, System.ComponentModel.PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(MainViewModel.SelectedTab) ||
            e.PropertyName == nameof(MainViewModel.HasOpenedCollection))
        {
            UpdateTabVisibility();
        }
        else if (e.PropertyName == nameof(MainViewModel.IsPlaying))
        {
            UpdatePlaybackControlsUi();
        }
        else if (e.PropertyName == nameof(MainViewModel.TotalTracksCount))
        {
            UpdateEmptyStateVisibility();
        }
    }

    private void OnActiveLyricLineChanged(int activeIndex)
    {
        Dispatcher.BeginInvoke(() =>
        {
            if (activeIndex < 0 || activeIndex >= _vm.ActiveLyricLines.Count) return;
            var item = _vm.ActiveLyricLines[activeIndex];
            if (_vm.IsLyricsPanelOpen && SyncedLyricsListBox.IsVisible)
            {
                SyncedLyricsListBox.ScrollIntoView(item);
            }
            if (_vm.IsLyricsFullscreen && FullscreenLyricsListBox.IsVisible)
            {
                FullscreenLyricsListBox.ScrollIntoView(item);
            }
        });
    }

    private void UpdateEmptyStateVisibility()
    {
        EmptyLibraryCard.Visibility = _vm.TotalTracksCount == 0
            ? Visibility.Visible
            : Visibility.Collapsed;
    }

    private void UpdatePlaybackControlsUi()
    {
        var playGeom = (Geometry)FindResource(_vm.IsPlaying ? "IconPause" : "IconPlay");
        PlayPausePath.Data = playGeom;
        FullscreenPlayPausePath.Data = playGeom;

        var modeGeomKey = _vm.Settings.PlaybackMode switch
        {
            PlaybackMode.Repeat => "IconRepeat",
            PlaybackMode.RepeatOne => "IconRepeatOne",
            PlaybackMode.Shuffle => "IconShuffle",
            _ => "IconRepeat"
        };
        PlaybackModePath.Data = (Geometry)FindResource(modeGeomKey);
    }

    private void UpdateNavButtonsHighlight()
    {
        var activeStyle = (Style)FindResource("ActiveNavButtonStyle");
        var normalStyle = (Style)FindResource("NavButtonStyle");

        NavTracksBtn.Style = _vm.SelectedTab == LibraryTab.Tracks ? activeStyle : normalStyle;
        NavPlaylistsBtn.Style = _vm.SelectedTab == LibraryTab.Playlists ? activeStyle : normalStyle;
        NavAlbumsBtn.Style = _vm.SelectedTab == LibraryTab.Albums ? activeStyle : normalStyle;
        NavArtistsBtn.Style = _vm.SelectedTab == LibraryTab.Artists ? activeStyle : normalStyle;
        NavGenresBtn.Style = _vm.SelectedTab == LibraryTab.Genres ? activeStyle : normalStyle;
        NavFoldersBtn.Style = _vm.SelectedTab == LibraryTab.Folders ? activeStyle : normalStyle;
    }

    private void UpdateTabVisibility()
    {
        UpdateNavButtonsHighlight();

        if (_vm.HasOpenedCollection)
        {
            TracksTabPanel.Visibility = Visibility.Collapsed;
            CollectionsTabPanel.Visibility = Visibility.Collapsed;
            OpenedCollectionPanel.Visibility = Visibility.Visible;
            return;
        }

        OpenedCollectionPanel.Visibility = Visibility.Collapsed;

        if (_vm.SelectedTab == LibraryTab.Tracks)
        {
            TracksTabPanel.Visibility = Visibility.Visible;
            CollectionsTabPanel.Visibility = Visibility.Collapsed;
        }
        else
        {
            TracksTabPanel.Visibility = Visibility.Collapsed;
            CollectionsTabPanel.Visibility = Visibility.Visible;
            DynamicPlaylistsSection.Visibility = _vm.SelectedTab == LibraryTab.Playlists
                ? Visibility.Visible
                : Visibility.Collapsed;

            CollectionsItemsControl.ItemsSource = _vm.SelectedTab switch
            {
                LibraryTab.Playlists => _vm.FilteredPlaylists,
                LibraryTab.Albums => _vm.FilteredAlbums,
                LibraryTab.Artists => _vm.FilteredArtists,
                LibraryTab.Genres => _vm.FilteredGenres,
                LibraryTab.Folders => _vm.FilteredFolders,
                _ => _vm.FilteredPlaylists
            };
        }
    }

    private void SyncSettingsControls()
    {
        ExtraFoldersListBox.ItemsSource = null;
        ExtraFoldersListBox.ItemsSource = _vm.Settings.ExtraScanFolders;

        AppearanceCombo.SelectedIndex = _vm.Settings.Appearance switch
        {
            AppAppearance.System => 0,
            AppAppearance.Light => 1,
            _ => 2
        };

        PaletteStyleCombo.SelectedIndex = _vm.Settings.PaletteStyle switch
        {
            PaletteStyle.TonalSpot => 0,
            PaletteStyle.Vibrant => 1,
            PaletteStyle.Expressive => 2,
            PaletteStyle.Neutral => 3,
            PaletteStyle.Monochrome => 4,
            _ => 0
        };
    }

    // Navigation & Tabs
    private void NavTab_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string tagStr &&
            Enum.TryParse<LibraryTab>(tagStr, out var tab))
        {
            _vm.SelectedTab = tab;
        }
    }

    private void CloseOpenedCollection_Click(object sender, RoutedEventArgs e)
    {
        _vm.OpenedCollection = null;
    }

    private void ClearSearch_Click(object sender, RoutedEventArgs e)
    {
        _vm.SearchQuery = string.Empty;
    }

    private void TrackSortCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_vm == null) return;
        if (TrackSortCombo.SelectedItem is ComboBoxItem item &&
            item.Tag is string tag &&
            Enum.TryParse<TrackSort>(tag, out var sort))
        {
            _vm.Settings.TrackSort = sort;
        }
    }

    private void ToggleSortOrder_Click(object sender, RoutedEventArgs e)
    {
        _vm.Settings.TrackSortOrder = _vm.Settings.TrackSortOrder == SortOrder.Ascending
            ? SortOrder.Descending
            : SortOrder.Ascending;
        _vm.Settings.PlaylistSortOrder = _vm.Settings.TrackSortOrder;
        SortOrderIcon.Text = _vm.Settings.TrackSortOrder == SortOrder.Ascending ? "↑" : "↓";
    }

    private void LocateCurrentTrack_Click(object sender, RoutedEventArgs e)
    {
        if (_vm.CurrentTrack == null) return;
        _vm.SelectedTab = LibraryTab.Tracks;
        TracksListBox.SelectedItem = _vm.CurrentTrack;
        TracksListBox.ScrollIntoView(_vm.CurrentTrack);
    }

    // Track & Collection Interactions
    private void TracksListBox_MouseDoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (TracksListBox.SelectedItem is Track track)
        {
            _vm.PlayTrack(track, _vm.FilteredTracks);
        }
    }

    private void PlayTrackItem_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { Tag: Track track })
        {
            _vm.PlayTrack(track, _vm.FilteredTracks);
        }
    }

    private void PlayNextItem_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { Tag: Track track })
        {
            _vm.PlayNextInQueue(track);
        }
    }

    private void AddToQueueItem_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { Tag: Track track })
        {
            _vm.AddToQueue(track);
        }
    }

    private void AddTrackToPlaylistMenu_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not FrameworkElement { Tag: Track track } fe) return;

        var menu = new ContextMenu();
        var createItem = new MenuItem { Header = "+ Criar Nova Playlist com esta faixa..." };
        createItem.Click += (_, _) =>
        {
            var name = PromptInputDialog("Nova Playlist", "Digite o nome da nova playlist:", "Minha Playlist");
            if (!string.IsNullOrWhiteSpace(name))
            {
                _vm.CreatePlaylist(name, new[] { track });
            }
        };
        menu.Items.Add(createItem);

        if (_vm.FilteredPlaylists.Count > 0)
        {
            menu.Items.Add(new Separator());
            foreach (var pl in _vm.FilteredPlaylists)
            {
                var item = new MenuItem { Header = $"Adicionar a '{pl.Name}' ({pl.TrackCountText})" };
                item.Click += (_, _) => _vm.AddTrackToPlaylist(pl, track);
                menu.Items.Add(item);
            }
        }

        menu.Items.Add(new Separator());
        var goToAlbum = new MenuItem { Header = $"Ir para Álbum: {track.DisplayAlbum}" };
        goToAlbum.Click += (_, _) => _vm.GoToAlbum(track);
        menu.Items.Add(goToAlbum);

        var goToArtist = new MenuItem { Header = $"Ir para Artista: {track.DisplayArtist}" };
        goToArtist.Click += (_, _) => _vm.GoToArtist(track);
        menu.Items.Add(goToArtist);

        menu.PlacementTarget = fe;
        menu.IsOpen = true;
    }

    private void EditTrackInfoItem_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { Tag: Track track })
        {
            PopulateAndOpenTrackInfo(track);
        }
    }

    private void CollectionCard_Click(object sender, MouseButtonEventArgs e)
    {
        if (sender is FrameworkElement { Tag: Playlist playlist })
        {
            _vm.OpenedCollection = playlist;
        }
    }

    private void PlayCollectionCard_Click(object sender, RoutedEventArgs e)
    {
        e.Handled = true;
        if (sender is FrameworkElement { Tag: Playlist playlist })
        {
            _vm.PlayPlaylist(playlist, shuffle: false);
        }
    }

    private void PlayOpenedCollection_Click(object sender, RoutedEventArgs e)
    {
        if (_vm.OpenedCollection != null)
            _vm.PlayPlaylist(_vm.OpenedCollection, shuffle: false);
    }

    private void ShuffleOpenedCollection_Click(object sender, RoutedEventArgs e)
    {
        if (_vm.OpenedCollection != null)
        {
            _vm.PlayPlaylist(_vm.OpenedCollection, shuffle: true);
            UpdatePlaybackControlsUi();
        }
    }

    private void CollectionTracksListBox_MouseDoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (sender is ListBox { SelectedItem: Track track })
        {
            _vm.PlayTrack(track, _vm.OpenedCollectionTracks);
        }
    }

    private void PlayFromOpenedCollection_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { Tag: Track track })
        {
            _vm.PlayTrack(track, _vm.OpenedCollectionTracks);
        }
    }

    private void MoveTrackUpInPlaylist_Click(object sender, RoutedEventArgs e)
    {
        if (_vm.OpenedCollection is { IsUserEditable: true } pl &&
            sender is FrameworkElement { Tag: Track track })
        {
            _vm.MoveTrackInPlaylist(pl, track, -1);
        }
    }

    private void MoveTrackDownInPlaylist_Click(object sender, RoutedEventArgs e)
    {
        if (_vm.OpenedCollection is { IsUserEditable: true } pl &&
            sender is FrameworkElement { Tag: Track track })
        {
            _vm.MoveTrackInPlaylist(pl, track, 1);
        }
    }

    private void RemoveTrackFromOpenedPlaylist_Click(object sender, RoutedEventArgs e)
    {
        if (_vm.OpenedCollection is { IsUserEditable: true } pl &&
            sender is FrameworkElement { Tag: Track track })
        {
            _vm.RemoveTrackFromPlaylist(pl, track);
        }
    }

    private void CreateNewPlaylist_Click(object sender, RoutedEventArgs e)
    {
        var name = PromptInputDialog("Criar Nova Playlist", "Digite o nome da playlist:", "Favoritas");
        if (!string.IsNullOrWhiteSpace(name))
        {
            var created = _vm.CreatePlaylist(name);
            _vm.SelectedTab = LibraryTab.Playlists;
            _vm.OpenedCollection = created;
        }
    }

    private void RenameOpenedPlaylist_Click(object sender, RoutedEventArgs e)
    {
        if (_vm.OpenedCollection is not { IsUserEditable: true } pl) return;
        var newName = PromptInputDialog("Renomear Playlist", "Novo nome da playlist:", pl.Name);
        if (!string.IsNullOrWhiteSpace(newName))
        {
            _vm.RenamePlaylist(pl, newName);
        }
    }

    private void DeleteOpenedPlaylist_Click(object sender, RoutedEventArgs e)
    {
        if (_vm.OpenedCollection is not { IsUserEditable: true } pl) return;
        var res = MessageBox.Show(
            this,
            $"Tem certeza que deseja excluir a playlist '{pl.Name}'?",
            "Excluir Playlist",
            MessageBoxButton.YesNo,
            MessageBoxImage.Question);

        if (res == MessageBoxResult.Yes)
        {
            _vm.DeletePlaylist(pl);
        }
    }

    private void ImportM3u_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new OpenFileDialog
        {
            Title = "Importar Playlist M3U / M3U8",
            Filter = "Playlists M3U (*.m3u;*.m3u8)|*.m3u;*.m3u8|Todos os arquivos (*.*)|*.*"
        };
        if (dlg.ShowDialog(this) == true)
        {
            _vm.ImportM3u(dlg.FileName);
        }
    }

    private void ExportOpenedPlaylist_Click(object sender, RoutedEventArgs e)
    {
        if (_vm.OpenedCollection == null) return;
        var dlg = new SaveFileDialog
        {
            Title = "Exportar Playlist para M3U8",
            FileName = $"{_vm.OpenedCollection.Name}.m3u8",
            Filter = "Playlist M3U8 (*.m3u8)|*.m3u8|Playlist M3U (*.m3u)|*.m3u"
        };
        if (dlg.ShowDialog(this) == true)
        {
            _vm.ExportM3u(_vm.OpenedCollection, dlg.FileName);
        }
    }

    // Playback Transport Controls & WavingSeekBar
    private void PlayPause_Click(object sender, RoutedEventArgs e) => _vm.TogglePlayPause();
    private void NextTrack_Click(object sender, RoutedEventArgs e) => _vm.SkipToNext();
    private void PreviousTrack_Click(object sender, RoutedEventArgs e) => _vm.SkipToPrevious();

    private void CyclePlaybackMode_Click(object sender, RoutedEventArgs e)
    {
        _vm.CyclePlaybackMode();
        UpdatePlaybackControlsUi();
    }

    private void WavingSeekBar_SeekCompleted(object? sender, double targetMs)
    {
        _vm.SeekTo((long)targetMs);
    }

    // Lyrics & Queue Panels
    private void ToggleLyricsPanel_Click(object sender, RoutedEventArgs e)
    {
        _vm.IsLyricsPanelOpen = !_vm.IsLyricsPanelOpen;
    }

    private void CloseLyricsPanel_Click(object sender, RoutedEventArgs e)
    {
        _vm.IsLyricsPanelOpen = false;
    }

    private void ToggleFullscreenLyrics_Click(object sender, RoutedEventArgs e)
    {
        _vm.IsLyricsFullscreen = !_vm.IsLyricsFullscreen;
    }

    private void SelectSyncedLyricsMode_Click(object sender, RoutedEventArgs e)
    {
        _vm.ShowSyncedLyrics = true;
    }

    private void SelectPlainLyricsMode_Click(object sender, RoutedEventArgs e)
    {
        _vm.ShowSyncedLyrics = false;
    }

    private void SyncedLyricsListBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (sender is ListBox { SelectedItem: LyricLine line })
        {
            _vm.SeekTo(line.StartTimeMs);
        }
    }

    private void LyricsOffsetMinus_Click(object sender, RoutedEventArgs e)
    {
        _vm.Settings.LyricsSyncOffsetMs -= 100;
    }

    private void LyricsOffsetPlus_Click(object sender, RoutedEventArgs e)
    {
        _vm.Settings.LyricsSyncOffsetMs += 100;
    }

    private void LoadExternalLrc_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new OpenFileDialog
        {
            Title = "Selecionar arquivo de letra (.LRC ou .TXT)",
            Filter = "Arquivos de Letra (*.lrc;*.txt)|*.lrc;*.txt|Todos os arquivos (*.*)|*.*"
        };
        if (dlg.ShowDialog(this) == true)
        {
            _vm.LoadLyricsFromExternalLrcFile(dlg.FileName);
        }
    }

    private async void RefetchLrclib_Click(object sender, RoutedEventArgs e)
    {
        await _vm.LoadLyricsForCurrentTrackAsync(forceRemote: true);
    }

    private async void PublishLrclib_Click(object sender, RoutedEventArgs e)
    {
        await _vm.PublishCurrentLyricsToLrclibAsync();
    }

    private void ToggleQueuePanel_Click(object sender, RoutedEventArgs e)
    {
        _vm.IsQueuePanelOpen = !_vm.IsQueuePanelOpen;
    }

    private void CloseQueuePanel_Click(object sender, RoutedEventArgs e)
    {
        _vm.IsQueuePanelOpen = false;
    }

    private void QueueListBox_MouseDoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (sender is ListBox { SelectedItem: Track track })
        {
            _vm.PlayTrack(track, null);
        }
    }

    private void RemoveQueueItem_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { Tag: Track track })
        {
            _vm.RemoveFromQueue(track);
        }
    }

    // Sleep Timer Menu
    private void OpenSleepTimerDialog_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not FrameworkElement fe) return;

        var menu = new ContextMenu();
        int[] presets = { 5, 15, 30, 45, 60 };
        foreach (var mins in presets)
        {
            var m = mins;
            var item = new MenuItem { Header = $"Parar reprodução em {m} minutos" };
            item.Click += (_, _) => _vm.StartSleepTimer(m);
            menu.Items.Add(item);
        }

        var endOfTrackItem = new MenuItem { Header = "Parar ao final da faixa atual" };
        endOfTrackItem.Click += (_, _) => _vm.StartSleepTimer(0, stopAtEndOfTrack: true);
        menu.Items.Add(endOfTrackItem);

        if (_vm.HasSleepTimer)
        {
            menu.Items.Add(new Separator());
            var cancelItem = new MenuItem { Header = "Cancelar Temporizador de Sono" };
            cancelItem.Click += (_, _) => _vm.CancelSleepTimer();
            menu.Items.Add(cancelItem);
        }

        menu.PlacementTarget = fe;
        menu.IsOpen = true;
    }

    // Track Info & Metadata Editor + MusicBrainz
    private void CurrentTrackQualityBadge_Click(object sender, MouseButtonEventArgs e)
    {
        if (_vm.CurrentTrack != null)
        {
            PopulateAndOpenTrackInfo(_vm.CurrentTrack);
        }
    }

    private void EditCurrentTrack_Click(object sender, RoutedEventArgs e)
    {
        if (_vm.CurrentTrack != null)
        {
            PopulateAndOpenTrackInfo(_vm.CurrentTrack);
        }
    }

    private void PopulateAndOpenTrackInfo(Track track)
    {
        _pendingEditedCoverBytes = null;
        EditTitleBox.Text = track.Title ?? track.DisplayTitle;
        EditArtistBox.Text = track.Artist ?? string.Empty;
        EditAlbumBox.Text = track.Album ?? string.Empty;
        EditAlbumArtistBox.Text = track.AlbumArtist ?? string.Empty;
        EditGenreBox.Text = track.Genre ?? string.Empty;
        EditYearBox.Text = track.Year ?? string.Empty;
        EditTrackNumBox.Text = track.TrackNumber ?? string.Empty;
        EditCoverPreview.Source = track.CoverArtImage;

        var localLyrics = new Services.MetadataService().ReadLocalLyrics(track);
        if (localLyrics?.Synced != null)
            EditLyricsBox.Text = LyricParser.SyncedLyricsToString(localLyrics.Synced);
        else if (localLyrics?.Plain != null)
            EditLyricsBox.Text = string.Join(Environment.NewLine, localLyrics.Plain);
        else
            EditLyricsBox.Text = string.Empty;

        MusicBrainzQueryBox.Text = $"{track.DisplayTitle} {track.Artist}".Trim();
        _vm.OpenTrackInfo(track);
    }

    private void PickNewCoverArt_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new OpenFileDialog
        {
            Title = "Escolher Imagem de Capa do Álbum",
            Filter = "Imagens (*.jpg;*.jpeg;*.png;*.webp)|*.jpg;*.jpeg;*.png;*.webp"
        };
        if (dlg.ShowDialog(this) == true)
        {
            try
            {
                _pendingEditedCoverBytes = File.ReadAllBytes(dlg.FileName);
                using var ms = new MemoryStream(_pendingEditedCoverBytes);
                var bmp = new BitmapImage();
                bmp.BeginInit();
                bmp.CacheOption = BitmapCacheOption.OnLoad;
                bmp.StreamSource = ms;
                bmp.EndInit();
                bmp.Freeze();
                EditCoverPreview.Source = bmp;
            }
            catch (Exception ex)
            {
                _vm.ShowSnackbar($"Erro ao carregar imagem: {ex.Message}");
            }
        }
    }

    private async void SearchMusicBrainz_Click(object sender, RoutedEventArgs e)
    {
        if (_vm.InspectedTrack == null) return;
        await _vm.SearchMusicBrainzAsync(MusicBrainzQueryBox.Text, _vm.InspectedTrack.DurationMs);
    }

    private async void MusicBrainzResults_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (sender is not ListBox { SelectedItem: MetadataSearchResult result }) return;

        EditTitleBox.Text = result.Title;
        EditArtistBox.Text = result.Artist;
        EditAlbumBox.Text = result.Album;
        EditAlbumArtistBox.Text = result.AlbumArtist;
        if (!string.IsNullOrWhiteSpace(result.Year))
            EditYearBox.Text = result.Year;
        if (!string.IsNullOrWhiteSpace(result.TrackNumber))
            EditTrackNumBox.Text = result.TrackNumber;
        if (result.Genres is { Count: > 0 })
            EditGenreBox.Text = result.Genres[0];

        _vm.ShowSnackbar("Baixando capa do álbum no Cover Art Archive...");
        var coverBytes = await _vm.DownloadMusicBrainzCoverArtAsync(result);
        if (coverBytes is { Length: > 0 })
        {
            _pendingEditedCoverBytes = coverBytes;
            using var ms = new MemoryStream(coverBytes);
            var bmp = new BitmapImage();
            bmp.BeginInit();
            bmp.CacheOption = BitmapCacheOption.OnLoad;
            bmp.StreamSource = ms;
            bmp.EndInit();
            bmp.Freeze();
            EditCoverPreview.Source = bmp;
            _vm.ShowSnackbar("Metadados e capa carregados do MusicBrainz!");
        }
        else
        {
            _vm.ShowSnackbar("Metadados preenchidos (capa não disponível para este lançamento).");
        }
    }

    private void SaveTrackMetadata_Click(object sender, RoutedEventArgs e)
    {
        if (_vm.InspectedTrack == null) return;

        var editModel = new MetadataEditModel
        {
            Title = EditTitleBox.Text,
            Artist = EditArtistBox.Text,
            Album = EditAlbumBox.Text,
            AlbumArtist = EditAlbumArtistBox.Text,
            Genre = EditGenreBox.Text,
            Year = EditYearBox.Text,
            TrackNumber = EditTrackNumBox.Text,
            CoverArtBytes = _pendingEditedCoverBytes,
            Lyrics = string.IsNullOrWhiteSpace(EditLyricsBox.Text) ? null : EditLyricsBox.Text
        };

        if (_vm.SaveTrackMetadata(_vm.InspectedTrack, editModel))
        {
            _vm.IsTrackInfoOpen = false;
        }
    }

    private void CloseTrackInfo_Click(object sender, RoutedEventArgs e)
    {
        _vm.IsTrackInfoOpen = false;
    }

    private void OpenFileLocation_Click(object sender, RoutedEventArgs e)
    {
        if (_vm.InspectedTrack == null || !File.Exists(_vm.InspectedTrack.FilePath)) return;
        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = "explorer.exe",
                Arguments = $"/select,\"{_vm.InspectedTrack.FilePath}\"",
                UseShellExecute = true
            });
        }
        catch
        {
            // Ignore
        }
    }

    // File & Folder Operations + Drag & Drop
    private void OpenAudioFiles_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new OpenFileDialog
        {
            Title = "Abrir Arquivos de Áudio",
            Multiselect = true,
            Filter = "Arquivos de Áudio (*.mp3;*.flac;*.ogg;*.wav;*.m4a;*.aac;*.wma;*.opus)|*.mp3;*.flac;*.ogg;*.wav;*.m4a;*.aac;*.wma;*.opus|Todos os arquivos (*.*)|*.*"
        };

        if (dlg.ShowDialog(this) == true && dlg.FileNames.Length > 0)
        {
            _vm.AddFilesAndPlay(dlg.FileNames);
            UpdateEmptyStateVisibility();
        }
    }

    private async void AddScanFolder_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new OpenFolderDialog
        {
            Title = "Selecione uma pasta com músicas para adicionar à biblioteca do Lotus"
        };

        if (dlg.ShowDialog(this) == true && !string.IsNullOrWhiteSpace(dlg.FolderName))
        {
            if (!_vm.Settings.ExtraScanFolders.Contains(dlg.FolderName, StringComparer.OrdinalIgnoreCase))
            {
                var updated = _vm.Settings.ExtraScanFolders.ToList();
                updated.Add(dlg.FolderName);
                _vm.Settings.ExtraScanFolders = updated;
                SyncSettingsControls();
                await _vm.ScanMusicLibraryAsync();
                UpdateEmptyStateVisibility();
            }
        }
    }

    private async void RemoveSelectedScanFolder_Click(object sender, RoutedEventArgs e)
    {
        if (ExtraFoldersListBox.SelectedItem is string folder)
        {
            var updated = _vm.Settings.ExtraScanFolders
                .Where(f => !f.Equals(folder, StringComparison.OrdinalIgnoreCase))
                .ToList();
            _vm.Settings.ExtraScanFolders = updated;
            SyncSettingsControls();
            await _vm.ScanMusicLibraryAsync();
            UpdateEmptyStateVisibility();
        }
    }

    private async void RescanLibrary_Click(object sender, RoutedEventArgs e)
    {
        await _vm.ScanMusicLibraryAsync();
        UpdateEmptyStateVisibility();
    }

    private void Window_Drop(object sender, DragEventArgs e)
    {
        if (!e.Data.GetDataPresent(DataFormats.FileDrop)) return;
        if (e.Data.GetData(DataFormats.FileDrop) is not string[] droppedPaths) return;

        var audioFiles = new List<string>();
        foreach (var p in droppedPaths)
        {
            if (Directory.Exists(p))
            {
                try
                {
                    audioFiles.AddRange(Directory.EnumerateFiles(p, "*.*", SearchOption.AllDirectories)
                        .Where(f => Services.MusicScannerService.SupportedExtensions.Contains(Path.GetExtension(f))));
                }
                catch
                {
                    // Ignore
                }
            }
            else if (File.Exists(p))
            {
                var ext = Path.GetExtension(p);
                if (ext.Equals(".lrc", StringComparison.OrdinalIgnoreCase))
                {
                    _vm.LoadLyricsFromExternalLrcFile(p);
                    return;
                }
                if (ext.Equals(".m3u", StringComparison.OrdinalIgnoreCase) ||
                    ext.Equals(".m3u8", StringComparison.OrdinalIgnoreCase))
                {
                    _vm.ImportM3u(p);
                    return;
                }
                audioFiles.Add(p);
            }
        }

        if (audioFiles.Count > 0)
        {
            _vm.AddFilesAndPlay(audioFiles);
            UpdateEmptyStateVisibility();
        }
    }

    // Settings Modal
    private void OpenSettings_Click(object sender, RoutedEventArgs e)
    {
        SyncSettingsControls();
        _vm.IsSettingsPanelOpen = true;
    }

    private void CloseSettings_Click(object sender, RoutedEventArgs e)
    {
        _vm.IsSettingsPanelOpen = false;
    }

    private void AppearanceCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_vm == null) return;
        if (AppearanceCombo.SelectedItem is ComboBoxItem { Tag: string tag } &&
            Enum.TryParse<AppAppearance>(tag, out var app))
        {
            _vm.Settings.Appearance = app;
        }
    }

    private void PaletteStyleCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_vm == null) return;
        if (PaletteStyleCombo.SelectedItem is ComboBoxItem { Tag: string tag } &&
            Enum.TryParse<PaletteStyle>(tag, out var style))
        {
            _vm.Settings.PaletteStyle = style;
        }
    }

    // Keyboard Shortcuts
    private void Window_PreviewKeyDown(object sender, KeyEventArgs e)
    {
        if (e.OriginalSource is TextBox)
        {
            if (e.Key == Key.Escape)
            {
                Keyboard.ClearFocus();
            }
            return;
        }

        if (e.Key == Key.Escape)
        {
            if (_vm.IsLyricsFullscreen)
            {
                _vm.IsLyricsFullscreen = false;
                e.Handled = true;
            }
            else if (_vm.IsTrackInfoOpen)
            {
                _vm.IsTrackInfoOpen = false;
                e.Handled = true;
            }
            else if (_vm.IsSettingsPanelOpen)
            {
                _vm.IsSettingsPanelOpen = false;
                e.Handled = true;
            }
            else if (_vm.HasOpenedCollection)
            {
                _vm.OpenedCollection = null;
                e.Handled = true;
            }
        }
        else if (e.Key == Key.Space)
        {
            _vm.TogglePlayPause();
            e.Handled = true;
        }
        else if (e.Key == Key.Right && Keyboard.Modifiers == ModifierKeys.Control)
        {
            _vm.SkipToNext();
            e.Handled = true;
        }
        else if (e.Key == Key.Left && Keyboard.Modifiers == ModifierKeys.Control)
        {
            _vm.SkipToPrevious();
            e.Handled = true;
        }
        else if (e.Key == Key.L && Keyboard.Modifiers == ModifierKeys.Control)
        {
            _vm.IsLyricsPanelOpen = !_vm.IsLyricsPanelOpen;
            e.Handled = true;
        }
        else if (e.Key == Key.F11)
        {
            _vm.IsLyricsFullscreen = !_vm.IsLyricsFullscreen;
            e.Handled = true;
        }
    }

    private string? PromptInputDialog(string title, string prompt, string defaultValue)
    {
        var win = new Window
        {
            Title = title,
            Width = 400,
            Height = 200,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Owner = this,
            ResizeMode = ResizeMode.NoResize,
            Background = (Brush)FindResource("SurfaceContainerBrush"),
            Foreground = (Brush)FindResource("OnSurfaceBrush")
        };

        var panel = new StackPanel { Margin = new Thickness(24, 20, 24, 20) };
        panel.Children.Add(new TextBlock
        {
            Text = prompt,
            FontWeight = FontWeights.SemiBold,
            FontSize = 14,
            Margin = new Thickness(0, 0, 0, 10)
        });

        var box = new TextBox
        {
            Text = defaultValue,
            Style = (Style)FindResource("ModernTextBoxStyle"),
            Margin = new Thickness(0, 0, 0, 16)
        };
        panel.Children.Add(box);

        var btnRow = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            HorizontalAlignment = HorizontalAlignment.Right
        };

        var cancelBtn = new Button
        {
            Content = "Cancelar",
            Style = (Style)FindResource("TonalButtonStyle"),
            Margin = new Thickness(0, 0, 8, 0)
        };
        cancelBtn.Click += (_, _) => win.DialogResult = false;

        var okBtn = new Button
        {
            Content = "Confirmar",
            Style = (Style)FindResource("FilledButtonStyle"),
            IsDefault = true
        };
        okBtn.Click += (_, _) => win.DialogResult = true;

        btnRow.Children.Add(cancelBtn);
        btnRow.Children.Add(okBtn);
        panel.Children.Add(btnRow);

        win.Content = panel;
        win.Loaded += (_, _) =>
        {
            box.Focus();
            box.SelectAll();
        };

        return win.ShowDialog() == true ? box.Text.Trim() : null;
    }
}
