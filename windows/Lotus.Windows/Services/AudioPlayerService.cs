using System;
using System.IO;
using System.Windows.Media;
using System.Windows.Threading;
using NAudio.Vorbis;
using NAudio.Wave;
using Lotus.Windows.Models;

namespace Lotus.Windows.Services;

public class AudioPlayerService : IDisposable
{
    private IWavePlayer? _waveOut;
    private WaveStream? _waveStream;
    private ISampleProvider? _sampleProvider;
    private MediaPlayer? _fallbackPlayer;
    private bool _usingFallback;
    private bool _isManualStop;

    private readonly DispatcherTimer _positionTimer;
    private float _volume = 0.85f;
    private bool _isMuted;

    public Track? CurrentTrack { get; private set; }
    public bool IsPlaying { get; private set; }

    public long PositionMs
    {
        get
        {
            try
            {
                if (_usingFallback && _fallbackPlayer != null)
                {
                    return (long)_fallbackPlayer.Position.TotalMilliseconds;
                }
                if (_waveStream != null)
                {
                    return (long)_waveStream.CurrentTime.TotalMilliseconds;
                }
            }
            catch
            {
                // Ignore
            }
            return 0L;
        }
    }

    public long DurationMs
    {
        get
        {
            try
            {
                if (_usingFallback && _fallbackPlayer?.NaturalDuration.HasTimeSpan == true)
                {
                    return (long)_fallbackPlayer.NaturalDuration.TimeSpan.TotalMilliseconds;
                }
                if (_waveStream != null)
                {
                    return (long)_waveStream.TotalTime.TotalMilliseconds;
                }
            }
            catch
            {
                // Ignore
            }
            return CurrentTrack?.DurationMs ?? 0L;
        }
    }

    public float Volume
    {
        get => _volume;
        set
        {
            _volume = Math.Clamp(value, 0f, 1f);
            ApplyVolume();
        }
    }

    public bool IsMuted
    {
        get => _isMuted;
        set
        {
            _isMuted = value;
            ApplyVolume();
        }
    }

    public event Action<long, long>? PositionChanged;
    public event Action<bool>? PlaybackStateChanged;
    public event Action? TrackEnded;
    public event Action<string>? PlaybackError;

    public AudioPlayerService()
    {
        _positionTimer = new DispatcherTimer
        {
            Interval = TimeSpan.FromMilliseconds(40)
        };
        _positionTimer.Tick += (_, _) =>
        {
            if (IsPlaying)
            {
                PositionChanged?.Invoke(PositionMs, DurationMs);
            }
        };
    }

    public void LoadAndPlay(Track track, long startPositionMs = 0, bool autoPlay = true)
    {
        StopInternal();

        if (!File.Exists(track.FilePath))
        {
            PlaybackError?.Invoke($"Arquivo não encontrado: {track.FilePath}");
            return;
        }

        CurrentTrack = track;
        _isManualStop = false;
        var ext = Path.GetExtension(track.FilePath).ToLowerInvariant();

        try
        {
            if (ext == ".ogg")
            {
                var vorbis = new VorbisWaveReader(track.FilePath);
                _waveStream = vorbis;
                _sampleProvider = vorbis;
            }
            else
            {
                var reader = new AudioFileReader(track.FilePath);
                _waveStream = reader;
                _sampleProvider = reader;
            }

            var waveOut = new WaveOutEvent
            {
                DesiredLatency = 120
            };
            waveOut.Init(_sampleProvider);
            waveOut.PlaybackStopped += OnWaveOutPlaybackStopped;
            _waveOut = waveOut;
            _usingFallback = false;

            ApplyVolume();

            if (startPositionMs > 0 && startPositionMs < DurationMs)
            {
                _waveStream.CurrentTime = TimeSpan.FromMilliseconds(startPositionMs);
            }

            if (track.DurationMs <= 0 && DurationMs > 0)
            {
                track.DurationMs = (int)DurationMs;
            }

            if (autoPlay)
            {
                _waveOut.Play();
                IsPlaying = true;
                _positionTimer.Start();
            }
            else
            {
                IsPlaying = false;
            }

            PlaybackStateChanged?.Invoke(IsPlaying);
            PositionChanged?.Invoke(PositionMs, DurationMs);
        }
        catch
        {
            // Fallback to Windows MediaPlayer for formats not supported by NAudio directly
            TryFallbackMediaPlayer(track, startPositionMs, autoPlay);
        }
    }

    private void TryFallbackMediaPlayer(Track track, long startPositionMs, bool autoPlay)
    {
        try
        {
            CleanupNAudio();
            _usingFallback = true;
            _fallbackPlayer = new MediaPlayer();
            _fallbackPlayer.MediaEnded += (_, _) =>
            {
                IsPlaying = false;
                _positionTimer.Stop();
                PlaybackStateChanged?.Invoke(false);
                TrackEnded?.Invoke();
            };
            _fallbackPlayer.MediaFailed += (_, args) =>
            {
                IsPlaying = false;
                _positionTimer.Stop();
                PlaybackStateChanged?.Invoke(false);
                PlaybackError?.Invoke($"Não foi possível reproduzir '{track.DisplayTitle}': {args.ErrorException?.Message}");
            };
            _fallbackPlayer.Open(new Uri(track.FilePath));
            ApplyVolume();

            if (startPositionMs > 0)
            {
                _fallbackPlayer.Position = TimeSpan.FromMilliseconds(startPositionMs);
            }

            if (autoPlay)
            {
                _fallbackPlayer.Play();
                IsPlaying = true;
                _positionTimer.Start();
            }
            else
            {
                IsPlaying = false;
            }

            PlaybackStateChanged?.Invoke(IsPlaying);
            PositionChanged?.Invoke(PositionMs, DurationMs);
        }
        catch (Exception ex)
        {
            PlaybackError?.Invoke($"Erro ao reproduzir '{track.DisplayTitle}': {ex.Message}");
        }
    }

    private void OnWaveOutPlaybackStopped(object? sender, StoppedEventArgs e)
    {
        if (_isManualStop) return;

        System.Windows.Application.Current?.Dispatcher.BeginInvoke(() =>
        {
            if (_isManualStop) return;
            IsPlaying = false;
            _positionTimer.Stop();
            PlaybackStateChanged?.Invoke(false);
            TrackEnded?.Invoke();
        });
    }

    public void Play()
    {
        if (CurrentTrack == null) return;

        try
        {
            if (_usingFallback && _fallbackPlayer != null)
            {
                _fallbackPlayer.Play();
                IsPlaying = true;
                _positionTimer.Start();
                PlaybackStateChanged?.Invoke(true);
            }
            else if (_waveOut != null)
            {
                _waveOut.Play();
                IsPlaying = true;
                _positionTimer.Start();
                PlaybackStateChanged?.Invoke(true);
            }
        }
        catch (Exception ex)
        {
            PlaybackError?.Invoke(ex.Message);
        }
    }

    public void Pause()
    {
        try
        {
            if (_usingFallback && _fallbackPlayer != null)
            {
                _fallbackPlayer.Pause();
            }
            else if (_waveOut != null)
            {
                _waveOut.Pause();
            }

            IsPlaying = false;
            _positionTimer.Stop();
            PlaybackStateChanged?.Invoke(false);
        }
        catch
        {
            // Ignore
        }
    }

    public void TogglePlayPause()
    {
        if (IsPlaying) Pause();
        else Play();
    }

    public void SeekTo(long positionMs)
    {
        try
        {
            long clamped = Math.Clamp(positionMs, 0L, Math.Max(0L, DurationMs - 100));
            if (_usingFallback && _fallbackPlayer != null)
            {
                _fallbackPlayer.Position = TimeSpan.FromMilliseconds(clamped);
            }
            else if (_waveStream != null)
            {
                _waveStream.CurrentTime = TimeSpan.FromMilliseconds(clamped);
            }
            PositionChanged?.Invoke(clamped, DurationMs);
        }
        catch
        {
            // Ignore seek errors
        }
    }

    public void ReleaseFileLockForMetadataEdit()
    {
        StopInternal();
    }

    public void Stop()
    {
        StopInternal();
        CurrentTrack = null;
        PlaybackStateChanged?.Invoke(false);
        PositionChanged?.Invoke(0, 0);
    }

    private void StopInternal()
    {
        _isManualStop = true;
        _positionTimer.Stop();
        IsPlaying = false;
        CleanupNAudio();
        if (_fallbackPlayer != null)
        {
            try
            {
                _fallbackPlayer.Stop();
                _fallbackPlayer.Close();
            }
            catch
            {
                // Ignore
            }
            _fallbackPlayer = null;
        }
    }

    private void CleanupNAudio()
    {
        if (_waveOut != null)
        {
            try
            {
                _waveOut.PlaybackStopped -= OnWaveOutPlaybackStopped;
                _waveOut.Stop();
                _waveOut.Dispose();
            }
            catch
            {
                // Ignore
            }
            _waveOut = null;
        }

        if (_waveStream != null)
        {
            try
            {
                _waveStream.Dispose();
            }
            catch
            {
                // Ignore
            }
            _waveStream = null;
        }
        _sampleProvider = null;
    }

    private void ApplyVolume()
    {
        float effective = _isMuted ? 0f : _volume;
        try
        {
            if (_waveStream is AudioFileReader afr)
            {
                afr.Volume = effective;
            }
            else if (_waveOut != null)
            {
                _waveOut.Volume = effective;
            }

            if (_fallbackPlayer != null)
            {
                _fallbackPlayer.Volume = effective;
            }
        }
        catch
        {
            // Ignore
        }
    }

    public void Dispose()
    {
        StopInternal();
    }
}
