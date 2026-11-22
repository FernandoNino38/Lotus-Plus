using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using Microsoft.Win32;
using Lotus.Windows.Models;

namespace Lotus.Windows.Services;

public class MaterialYouColorService
{
    // Default Lotus Olive-Green Seed Color (from Android Color.kt: 0xFF4C662B)
    private static readonly Color DefaultLotusSeed = Color.FromRgb(0x4C, 0x66, 0x2B);

    public Color ExtractSeedColorFromCoverArt(byte[]? coverArtBytes)
    {
        if (coverArtBytes is not { Length: > 0 })
            return GetSystemOrDefaultSeed();

        try
        {
            using var ms = new MemoryStream(coverArtBytes);
            var bmp = new BitmapImage();
            bmp.BeginInit();
            bmp.CacheOption = BitmapCacheOption.OnLoad;
            bmp.DecodePixelWidth = 48;
            bmp.DecodePixelHeight = 48;
            bmp.StreamSource = ms;
            bmp.EndInit();
            bmp.Freeze();

            var formatted = new FormatConvertedBitmap(bmp, PixelFormats.Bgra32, null, 0);
            int width = formatted.PixelWidth;
            int height = formatted.PixelHeight;
            int stride = width * 4;
            byte[] pixels = new byte[height * stride];
            formatted.CopyPixels(pixels, stride, 0);

            var buckets = new Dictionary<int, (int Count, long R, long G, long B, double SumChroma)>();

            for (int i = 0; i < pixels.Length; i += 4)
            {
                byte b = pixels[i];
                byte g = pixels[i + 1];
                byte r = pixels[i + 2];
                byte a = pixels[i + 3];
                if (a < 128) continue;

                RgbToHsl(r, g, b, out double h, out double s, out double l);
                if (l < 0.08 || l > 0.94) continue;

                int bucket = (int)(h / 10.0) % 36;
                double chroma = s * (1.0 - Math.Abs(2.0 * l - 1.0)) * 100.0;

                if (!buckets.TryGetValue(bucket, out var cur))
                    cur = (0, 0, 0, 0, 0);

                buckets[bucket] = (cur.Count + 1, cur.R + r, cur.G + g, cur.B + b, cur.SumChroma + chroma);
            }

            if (buckets.Count == 0)
                return DefaultLotusSeed;

            var swatches = buckets.Values
                .OrderByDescending(x => x.Count)
                .Select(x =>
                {
                    var avgColor = Color.FromRgb(
                        (byte)(x.R / x.Count),
                        (byte)(x.G / x.Count),
                        (byte)(x.B / x.Count));
                    double avgChroma = x.SumChroma / x.Count;
                    return (Color: avgColor, Population: x.Count, Chroma: avgChroma);
                })
                .ToList();

            var firstSwatch = swatches[0];
            var moreChromatic = swatches.FirstOrDefault(s =>
                (s.Chroma - firstSwatch.Chroma) >= 25.0 &&
                (double)s.Population / firstSwatch.Population >= 0.10);

            return moreChromatic.Population > 0 ? moreChromatic.Color : firstSwatch.Color;
        }
        catch
        {
            return DefaultLotusSeed;
        }
    }

    public Color GetSystemOrDefaultSeed()
    {
        try
        {
            return SystemParameters.WindowGlassColor;
        }
        catch
        {
            return DefaultLotusSeed;
        }
    }

    public bool IsSystemDarkTheme()
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize");
            var val = key?.GetValue("AppsUseLightTheme");
            if (val is int i) return i == 0;
        }
        catch
        {
            // Fallback
        }
        return true;
    }

    public void ApplyTheme(AppSettings settings, byte[]? currentTrackCoverArt = null)
    {
        var resources = Application.Current?.Resources;
        if (resources == null) return;

        bool isDark = settings.Appearance switch
        {
            AppAppearance.Light => false,
            AppAppearance.Dark => true,
            _ => IsSystemDarkTheme()
        };

        Color seed;
        if (settings.UseAlbumArtColor && currentTrackCoverArt is { Length: > 0 })
        {
            seed = ExtractSeedColorFromCoverArt(currentTrackCoverArt);
        }
        else if (settings.UseDynamicColor)
        {
            seed = GetSystemOrDefaultSeed();
        }
        else
        {
            seed = DefaultLotusSeed;
        }

        RgbToHsl(seed.R, seed.G, seed.B, out double h, out double s, out _);

        double primarySat = settings.PaletteStyle switch
        {
            PaletteStyle.Vibrant => Math.Clamp(s * 1.35, 0.65, 0.95),
            PaletteStyle.Expressive => Math.Clamp(s * 1.25, 0.55, 0.90),
            PaletteStyle.Neutral => Math.Clamp(s * 0.25, 0.06, 0.18),
            PaletteStyle.Monochrome => 0.0,
            _ => Math.Clamp(s, 0.42, 0.80) // TonalSpot
        };

        double surfaceSat = settings.PaletteStyle switch
        {
            PaletteStyle.Vibrant => Math.Clamp(primarySat * 0.30, 0.10, 0.24),
            PaletteStyle.Expressive => Math.Clamp(primarySat * 0.25, 0.08, 0.20),
            PaletteStyle.Neutral => 0.04,
            PaletteStyle.Monochrome => 0.0,
            _ => Math.Clamp(primarySat * 0.22, 0.06, 0.16)
        };

        double secondaryHue = settings.PaletteStyle == PaletteStyle.Expressive
            ? (h + 45.0) % 360.0
            : (h + 18.0) % 360.0;

        double tertiaryHue = (h + 65.0) % 360.0;

        if (isDark)
        {
            bool amoled = settings.AmoledDarkTheme;

            Color primary = HslToRgb(h, primarySat, 0.74);
            Color onPrimary = HslToRgb(h, primarySat, 0.12);
            Color primaryContainer = HslToRgb(h, primarySat * 0.85, 0.24);
            Color onPrimaryContainer = HslToRgb(h, primarySat, 0.90);

            Color secondary = HslToRgb(secondaryHue, primarySat * 0.75, 0.74);
            Color secondaryContainer = HslToRgb(secondaryHue, primarySat * 0.65, 0.26);
            Color onSecondaryContainer = HslToRgb(secondaryHue, primarySat * 0.85, 0.92);

            Color background = amoled ? Color.FromRgb(0x00, 0x00, 0x00) : HslToRgb(h, surfaceSat, 0.065);
            Color surface = amoled ? Color.FromRgb(0x05, 0x05, 0x05) : HslToRgb(h, surfaceSat, 0.085);
            Color surfaceContainerLow = amoled ? Color.FromRgb(0x0A, 0x0A, 0x0A) : HslToRgb(h, surfaceSat, 0.105);
            Color surfaceContainer = amoled ? Color.FromRgb(0x11, 0x11, 0x11) : HslToRgb(h, surfaceSat, 0.13);
            Color surfaceContainerHigh = amoled ? Color.FromRgb(0x18, 0x18, 0x18) : HslToRgb(h, surfaceSat, 0.165);
            Color surfaceContainerHighest = amoled ? Color.FromRgb(0x22, 0x22, 0x22) : HslToRgb(h, surfaceSat, 0.21);

            Color onSurface = HslToRgb(h, surfaceSat * 0.4, 0.93);
            Color onSurfaceVariant = HslToRgb(h, surfaceSat * 0.5, 0.70);
            Color outline = HslToRgb(h, surfaceSat * 0.5, 0.34);
            Color outlineVariant = HslToRgb(h, surfaceSat * 0.5, 0.19);

            Color activeRow = Color.FromArgb(0x38, primary.R, primary.G, primary.B);
            Color lyricsContainer = amoled
                ? Color.FromRgb(0x02, 0x02, 0x02)
                : HslToRgb(h, Math.Max(primarySat * 0.58, 0.28), 0.11);
            Color lyricsContent = HslToRgb(h, primarySat * 0.35, 0.95);
            Color lyricsAccent = HslToRgb(h, Math.Min(primarySat * 1.15, 0.96), 0.78);

            SetBrush(resources, "PrimaryBrush", primary);
            SetBrush(resources, "OnPrimaryBrush", onPrimary);
            SetBrush(resources, "PrimaryContainerBrush", primaryContainer);
            SetBrush(resources, "OnPrimaryContainerBrush", onPrimaryContainer);
            SetBrush(resources, "SecondaryBrush", secondary);
            SetBrush(resources, "SecondaryContainerBrush", secondaryContainer);
            SetBrush(resources, "OnSecondaryContainerBrush", onSecondaryContainer);
            SetBrush(resources, "BackgroundBrush", background);
            SetBrush(resources, "SurfaceBrush", surface);
            SetBrush(resources, "SurfaceContainerLowBrush", surfaceContainerLow);
            SetBrush(resources, "SurfaceContainerBrush", surfaceContainer);
            SetBrush(resources, "SurfaceContainerHighBrush", surfaceContainerHigh);
            SetBrush(resources, "SurfaceContainerHighestBrush", surfaceContainerHighest);
            SetBrush(resources, "OnSurfaceBrush", onSurface);
            SetBrush(resources, "OnSurfaceVariantBrush", onSurfaceVariant);
            SetBrush(resources, "OutlineBrush", outline);
            SetBrush(resources, "OutlineVariantBrush", outlineVariant);
            SetBrush(resources, "ActiveRowBrush", activeRow);
            SetBrush(resources, "LyricsContainerBrush", lyricsContainer);
            SetBrush(resources, "LyricsContentBrush", lyricsContent);
            SetBrush(resources, "LyricsAccentBrush", lyricsAccent);

            // Ambient radial glow for workspace & player bar
            Color glowStart = Color.FromArgb(amoled ? (byte)0x28 : (byte)0x38, primary.R, primary.G, primary.B);
            Color glowMid = Color.FromArgb(0x14, secondary.R, secondary.G, secondary.B);
            SetAmbientBrushes(resources, glowStart, glowMid, lyricsContainer, HslToRgb(tertiaryHue, primarySat * 0.65, 0.16));
        }
        else
        {
            Color primary = HslToRgb(h, primarySat, 0.34);
            Color onPrimary = Colors.White;
            Color primaryContainer = HslToRgb(h, primarySat * 0.85, 0.86);
            Color onPrimaryContainer = HslToRgb(h, primarySat, 0.12);

            Color secondary = HslToRgb(secondaryHue, primarySat * 0.70, 0.38);
            Color secondaryContainer = HslToRgb(secondaryHue, primarySat * 0.75, 0.88);
            Color onSecondaryContainer = HslToRgb(secondaryHue, primarySat * 0.85, 0.14);

            Color background = HslToRgb(h, surfaceSat, 0.975);
            Color surface = HslToRgb(h, surfaceSat, 0.96);
            Color surfaceContainerLow = HslToRgb(h, surfaceSat, 0.94);
            Color surfaceContainer = HslToRgb(h, surfaceSat, 0.91);
            Color surfaceContainerHigh = HslToRgb(h, surfaceSat, 0.875);
            Color surfaceContainerHighest = HslToRgb(h, surfaceSat, 0.835);

            Color onSurface = HslToRgb(h, surfaceSat * 0.5, 0.11);
            Color onSurfaceVariant = HslToRgb(h, surfaceSat * 0.6, 0.34);
            Color outline = HslToRgb(h, surfaceSat * 0.5, 0.55);
            Color outlineVariant = HslToRgb(h, surfaceSat * 0.5, 0.80);

            Color activeRow = Color.FromArgb(0x2E, primary.R, primary.G, primary.B);

            bool darkLyrics = settings.UseDarkPaletteOnLyricsSheet;
            Color lyricsContainer = darkLyrics
                ? HslToRgb(h, Math.Max(primarySat * 0.55, 0.25), 0.13)
                : HslToRgb(h, Math.Max(primarySat * 0.55, 0.25), 0.89);
            Color lyricsContent = darkLyrics
                ? HslToRgb(h, primarySat * 0.35, 0.95)
                : HslToRgb(h, primarySat * 0.65, 0.14);
            Color lyricsAccent = darkLyrics
                ? HslToRgb(h, Math.Min(primarySat * 1.15, 0.95), 0.78)
                : primary;

            SetBrush(resources, "PrimaryBrush", primary);
            SetBrush(resources, "OnPrimaryBrush", onPrimary);
            SetBrush(resources, "PrimaryContainerBrush", primaryContainer);
            SetBrush(resources, "OnPrimaryContainerBrush", onPrimaryContainer);
            SetBrush(resources, "SecondaryBrush", secondary);
            SetBrush(resources, "SecondaryContainerBrush", secondaryContainer);
            SetBrush(resources, "OnSecondaryContainerBrush", onSecondaryContainer);
            SetBrush(resources, "BackgroundBrush", background);
            SetBrush(resources, "SurfaceBrush", surface);
            SetBrush(resources, "SurfaceContainerLowBrush", surfaceContainerLow);
            SetBrush(resources, "SurfaceContainerBrush", surfaceContainer);
            SetBrush(resources, "SurfaceContainerHighBrush", surfaceContainerHigh);
            SetBrush(resources, "SurfaceContainerHighestBrush", surfaceContainerHighest);
            SetBrush(resources, "OnSurfaceBrush", onSurface);
            SetBrush(resources, "OnSurfaceVariantBrush", onSurfaceVariant);
            SetBrush(resources, "OutlineBrush", outline);
            SetBrush(resources, "OutlineVariantBrush", outlineVariant);
            SetBrush(resources, "ActiveRowBrush", activeRow);
            SetBrush(resources, "LyricsContainerBrush", lyricsContainer);
            SetBrush(resources, "LyricsContentBrush", lyricsContent);
            SetBrush(resources, "LyricsAccentBrush", lyricsAccent);

            Color glowStart = Color.FromArgb(0x28, primary.R, primary.G, primary.B);
            Color glowMid = Color.FromArgb(0x12, secondary.R, secondary.G, secondary.B);
            SetAmbientBrushes(resources, glowStart, glowMid, lyricsContainer,
                darkLyrics ? HslToRgb(tertiaryHue, primarySat * 0.6, 0.18) : HslToRgb(tertiaryHue, primarySat * 0.6, 0.84));
        }
    }

    private static void SetAmbientBrushes(
        ResourceDictionary resources,
        Color glowStart,
        Color glowMid,
        Color meshBase,
        Color meshAccent)
    {
        var radial = new RadialGradientBrush
        {
            Center = new Point(0.25, 0.15),
            GradientOrigin = new Point(0.25, 0.15),
            RadiusX = 0.85,
            RadiusY = 0.85,
            GradientStops = new GradientStopCollection
            {
                new(glowStart, 0.0),
                new(glowMid, 0.55),
                new(Colors.Transparent, 1.0)
            }
        };
        radial.Freeze();
        resources["AmbientGlowBrush"] = radial;

        var lyricsMesh = new LinearGradientBrush
        {
            StartPoint = new Point(0, 0),
            EndPoint = new Point(1, 1),
            GradientStops = new GradientStopCollection
            {
                new(meshBase, 0.0),
                new(meshAccent, 0.52),
                new(meshBase, 1.0)
            }
        };
        lyricsMesh.Freeze();
        resources["LyricsMeshBrush"] = lyricsMesh;
    }

    private static void SetBrush(ResourceDictionary resources, string key, Color color)
    {
        var brush = new SolidColorBrush(color);
        brush.Freeze();
        resources[key] = brush;
        resources[key + "Color"] = color;
    }

    private static void RgbToHsl(byte r, byte g, byte b, out double h, out double s, out double l)
    {
        double rd = r / 255.0;
        double gd = g / 255.0;
        double bd = b / 255.0;

        double max = Math.Max(rd, Math.Max(gd, bd));
        double min = Math.Min(rd, Math.Min(gd, bd));
        l = (max + min) / 2.0;

        if (Math.Abs(max - min) < 0.0001)
        {
            h = 0;
            s = 0;
        }
        else
        {
            double d = max - min;
            s = l > 0.5 ? d / (2.0 - max - min) : d / (max + min);

            if (Math.Abs(max - rd) < 0.0001)
                h = (gd - bd) / d + (gd < bd ? 6.0 : 0.0);
            else if (Math.Abs(max - gd) < 0.0001)
                h = (bd - rd) / d + 2.0;
            else
                h = (rd - gd) / d + 4.0;

            h *= 60.0;
        }
    }

    private static Color HslToRgb(double h, double s, double l)
    {
        h = ((h % 360.0) + 360.0) % 360.0;
        s = Math.Clamp(s, 0.0, 1.0);
        l = Math.Clamp(l, 0.0, 1.0);

        double c = (1.0 - Math.Abs(2.0 * l - 1.0)) * s;
        double x = c * (1.0 - Math.Abs((h / 60.0) % 2.0 - 1.0));
        double m = l - c / 2.0;

        double r1, g1, b1;
        if (h < 60) { r1 = c; g1 = x; b1 = 0; }
        else if (h < 120) { r1 = x; g1 = c; b1 = 0; }
        else if (h < 180) { r1 = 0; g1 = c; b1 = x; }
        else if (h < 240) { r1 = 0; g1 = x; b1 = c; }
        else if (h < 300) { r1 = x; g1 = 0; b1 = c; }
        else { r1 = c; g1 = 0; b1 = x; }

        byte r = (byte)Math.Clamp((int)Math.Round((r1 + m) * 255.0), 0, 255);
        byte g = (byte)Math.Clamp((int)Math.Round((g1 + m) * 255.0), 0, 255);
        byte b = (byte)Math.Clamp((int)Math.Round((b1 + m) * 255.0), 0, 255);
        return Color.FromRgb(r, g, b);
    }
}
