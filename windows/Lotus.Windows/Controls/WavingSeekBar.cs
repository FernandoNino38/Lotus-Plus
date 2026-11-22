using System;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media;

namespace Lotus.Windows.Controls;

/// <summary>
/// Custom WPF port of Lotus Android's WavingSeekBar.kt:
/// Renders an animated sine wave for the elapsed portion while playing,
/// a rounded-square rotating handle, and smooth click/drag seeking.
/// </summary>
public class WavingSeekBar : FrameworkElement
{
    public static readonly DependencyProperty MinimumProperty =
        DependencyProperty.Register(nameof(Minimum), typeof(double), typeof(WavingSeekBar),
            new FrameworkPropertyMetadata(0.0, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty MaximumProperty =
        DependencyProperty.Register(nameof(Maximum), typeof(double), typeof(WavingSeekBar),
            new FrameworkPropertyMetadata(100.0, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty ValueProperty =
        DependencyProperty.Register(nameof(Value), typeof(double), typeof(WavingSeekBar),
            new FrameworkPropertyMetadata(0.0, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty IsPlayingProperty =
        DependencyProperty.Register(nameof(IsPlaying), typeof(bool), typeof(WavingSeekBar),
            new FrameworkPropertyMetadata(false, OnIsPlayingChanged));

    public static readonly DependencyProperty ActiveBrushProperty =
        DependencyProperty.Register(nameof(ActiveBrush), typeof(Brush), typeof(WavingSeekBar),
            new FrameworkPropertyMetadata(Brushes.LightGreen, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty InactiveBrushProperty =
        DependencyProperty.Register(nameof(InactiveBrush), typeof(Brush), typeof(WavingSeekBar),
            new FrameworkPropertyMetadata(Brushes.DimGray, FrameworkPropertyMetadataOptions.AffectsRender));

    private double _phaseOffset;
    private double _handleAngle;
    private double _waveAmplitudeFactor;
    private bool _isDragging;
    private double _dragFraction;
    private bool _isSubscribedToRendering;

    public event EventHandler<double>? SeekCompleted;

    public double Minimum
    {
        get => (double)GetValue(MinimumProperty);
        set => SetValue(MinimumProperty, value);
    }

    public double Maximum
    {
        get => (double)GetValue(MaximumProperty);
        set => SetValue(MaximumProperty, value);
    }

    public double Value
    {
        get => (double)GetValue(ValueProperty);
        set => SetValue(ValueProperty, value);
    }

    public bool IsPlaying
    {
        get => (bool)GetValue(IsPlayingProperty);
        set => SetValue(IsPlayingProperty, value);
    }

    public Brush ActiveBrush
    {
        get => (Brush)GetValue(ActiveBrushProperty);
        set => SetValue(ActiveBrushProperty, value);
    }

    public Brush InactiveBrush
    {
        get => (Brush)GetValue(InactiveBrushProperty);
        set => SetValue(InactiveBrushProperty, value);
    }

    public bool IsDragging => _isDragging;

    public WavingSeekBar()
    {
        Height = 28;
        Cursor = Cursors.Hand;
        Focusable = false;
        Loaded += (_, _) => UpdateRenderingSubscription();
        Unloaded += (_, _) => UnsubscribeRendering();
    }

    private static void OnIsPlayingChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
    {
        if (d is WavingSeekBar bar)
        {
            bar.UpdateRenderingSubscription();
            bar.InvalidateVisual();
        }
    }

    private void UpdateRenderingSubscription()
    {
        if (IsLoaded && (IsPlaying || _waveAmplitudeFactor > 0.01))
        {
            if (!_isSubscribedToRendering)
            {
                CompositionTarget.Rendering += OnCompositionTargetRendering;
                _isSubscribedToRendering = true;
            }
        }
    }

    private void UnsubscribeRendering()
    {
        if (_isSubscribedToRendering)
        {
            CompositionTarget.Rendering -= OnCompositionTargetRendering;
            _isSubscribedToRendering = false;
        }
    }

    private void OnCompositionTargetRendering(object? sender, EventArgs e)
    {
        bool activeWave = IsPlaying && !_isDragging;
        double targetAmp = activeWave ? 1.0 : 0.0;
        _waveAmplitudeFactor += (targetAmp - _waveAmplitudeFactor) * 0.12;

        if (IsPlaying)
        {
            _phaseOffset += 0.14;
            _handleAngle = (_handleAngle + 1.4) % 360.0;
        }

        if (!IsPlaying && _waveAmplitudeFactor <= 0.01)
        {
            _waveAmplitudeFactor = 0.0;
            UnsubscribeRendering();
        }

        InvalidateVisual();
    }

    protected override void OnMouseLeftButtonDown(MouseButtonEventArgs e)
    {
        base.OnMouseLeftButtonDown(e);
        CaptureMouse();
        _isDragging = true;
        UpdateDragFromPoint(e.GetPosition(this));
        e.Handled = true;
    }

    protected override void OnMouseMove(MouseEventArgs e)
    {
        base.OnMouseMove(e);
        if (_isDragging)
        {
            UpdateDragFromPoint(e.GetPosition(this));
        }
    }

    protected override void OnMouseLeftButtonUp(MouseButtonEventArgs e)
    {
        base.OnMouseLeftButtonUp(e);
        if (_isDragging)
        {
            UpdateDragFromPoint(e.GetPosition(this));
            _isDragging = false;
            ReleaseMouseCapture();

            double range = Math.Max(0.0, Maximum - Minimum);
            double newValue = Minimum + _dragFraction * range;
            SetCurrentValue(ValueProperty, newValue);
            SeekCompleted?.Invoke(this, newValue);
            UpdateRenderingSubscription();
            InvalidateVisual();
            e.Handled = true;
        }
    }

    private void UpdateDragFromPoint(Point pt)
    {
        double pad = 10.0;
        double usableWidth = Math.Max(1.0, ActualWidth - pad * 2);
        _dragFraction = Math.Clamp((pt.X - pad) / usableWidth, 0.0, 1.0);
        InvalidateVisual();
    }

    protected override void OnRender(DrawingContext dc)
    {
        base.OnRender(dc);

        double width = ActualWidth;
        double height = ActualHeight;
        if (width <= 20 || height <= 4) return;

        // Transparent hit-test background
        dc.DrawRectangle(Brushes.Transparent, null, new Rect(0, 0, width, height));

        double pad = 10.0;
        double usableWidth = width - pad * 2;
        double centerY = height / 2.0;

        double range = Maximum - Minimum;
        double fraction = _isDragging
            ? _dragFraction
            : (range > 0 ? Math.Clamp((Value - Minimum) / range, 0.0, 1.0) : 0.0);

        double activeEndX = pad + usableWidth * fraction;

        var activePen = new Pen(ActiveBrush, 3.2)
        {
            StartLineCap = PenLineCap.Round,
            EndLineCap = PenLineCap.Round,
            LineJoin = PenLineJoin.Round
        };
        var inactivePen = new Pen(InactiveBrush, 3.2)
        {
            StartLineCap = PenLineCap.Round,
            EndLineCap = PenLineCap.Round
        };

        // 1. Draw Inactive Remaining Segment
        if (activeEndX < pad + usableWidth)
        {
            dc.DrawLine(inactivePen, new Point(activeEndX, centerY), new Point(pad + usableWidth, centerY));
        }

        // 2. Draw Active Segment (Sine Wave when playing, smooth line when paused)
        if (activeEndX > pad + 1)
        {
            double waveAmp = 3.2 * _waveAmplitudeFactor;
            double waveLength = 7.5;

            if (waveAmp > 0.15)
            {
                var geom = new StreamGeometry();
                using (var ctx = geom.Open())
                {
                    double startY = centerY + waveAmp * Math.Cos(-_phaseOffset);
                    ctx.BeginFigure(new Point(pad, startY), false, false);

                    for (double x = pad + 2; x <= activeEndX; x += 2.0)
                    {
                        double y = centerY + waveAmp * Math.Cos(((x - pad) / waveLength) - _phaseOffset);
                        ctx.LineTo(new Point(x, y), true, true);
                    }
                }
                geom.Freeze();
                dc.DrawGeometry(null, activePen, geom);
            }
            else
            {
                dc.DrawLine(activePen, new Point(pad, centerY), new Point(activeEndX, centerY));
            }
        }

        // 3. Draw Rotating Rounded-Square Handle (port of SeekBarHandle in WavingSeekBar.kt)
        double handleSize = _isDragging ? 18.0 : 15.0;
        double cornerRadius = handleSize / 3.0;
        dc.PushTransform(new TranslateTransform(activeEndX, centerY));
        dc.PushTransform(new RotateTransform(_handleAngle));
        dc.DrawRoundedRectangle(
            ActiveBrush,
            null,
            new Rect(-handleSize / 2.0, -handleSize / 2.0, handleSize, handleSize),
            cornerRadius,
            cornerRadius);
        dc.Pop();
        dc.Pop();
    }
}
