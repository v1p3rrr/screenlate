param(
    [string]$Out,
    [string]$LinesFile,
    [int]$Pitch = 160,
    [string]$Font = "Nirmala UI",
    [string]$Culture = "mr-IN"
)
# Renders lines of text into a 1280x2856 PNG, as scripts/text-image.py does, shaped by WPF and with the letter forms of
# -Culture (Marathi's own श and ल under mr-IN in fonts that have them). Each line of the UTF-8 file -LinesFile is a line
# of the image:
#   pwsh scripts/text-image-wpf.ps1 -Out testdata/ocr/NAME.png -LinesFile lines.txt [-Pitch 96] [-Font "Nirmala UI"] [-Culture mr-IN]
Add-Type -AssemblyName PresentationCore, PresentationFramework, WindowsBase
$lines = Get-Content -Encoding utf8 $LinesFile | Where-Object { $_ -ne "" }
$dv = New-Object System.Windows.Media.DrawingVisual
$dc = $dv.RenderOpen()
$dc.DrawRectangle([System.Windows.Media.Brushes]::White, $null, (New-Object System.Windows.Rect 0, 0, 1280, 2856))
$typeface = New-Object System.Windows.Media.Typeface $Font
$culture = [Globalization.CultureInfo]::GetCultureInfo($Culture)
$y = 600
foreach ($line in $lines) {
    $text = New-Object System.Windows.Media.FormattedText($line, $culture, [System.Windows.FlowDirection]::LeftToRight, $typeface, 72, [System.Windows.Media.Brushes]::Black, 1.0)
    $dc.DrawText($text, (New-Object System.Windows.Point 80, $y))
    $y += $Pitch
}
$dc.Close()
$bitmap = New-Object System.Windows.Media.Imaging.RenderTargetBitmap 1280, 2856, 96, 96, ([System.Windows.Media.PixelFormats]::Pbgra32)
$bitmap.Render($dv)
$encoder = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
$encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($bitmap))
$stream = [IO.File]::Create($Out)
$encoder.Save($stream)
$stream.Close()
