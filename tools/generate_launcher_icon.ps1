param(
    [string]$InputPath = '',
    [string]$ResourceRoot = (Join-Path $PSScriptRoot '..\app\src\main\res')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if ([string]::IsNullOrWhiteSpace($InputPath)) {
    $InputPath = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '..') -Filter '*.png' -File |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if ([string]::IsNullOrWhiteSpace($InputPath)) {
    throw 'No PNG launcher icon source was found in the project root.'
}

function New-RoundedRectanglePath {
    param(
        [float]$X,
        [float]$Y,
        [float]$Width,
        [float]$Height,
        [float]$Diameter
    )

    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc($X, $Y, $Diameter, $Diameter, 180, 90)
    $path.AddArc($X + $Width - $Diameter, $Y, $Diameter, $Diameter, 270, 90)
    $path.AddArc(
        $X + $Width - $Diameter,
        $Y + $Height - $Diameter,
        $Diameter,
        $Diameter,
        0,
        90
    )
    $path.AddArc($X, $Y + $Height - $Diameter, $Diameter, $Diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

$source = [System.Drawing.Bitmap]::FromFile((Resolve-Path -LiteralPath $InputPath))
try {
    if ($source.Width -ne $source.Height) {
        throw "Launcher icon source must be square, got $($source.Width)x$($source.Height)."
    }

    $clean = New-Object System.Drawing.Bitmap(
        $source.Width,
        $source.Height,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($clean)
        try {
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

            # The supplied artwork already contains a rounded-square border. Clip to that
            # border so its near-black canvas corners do not appear in Android launchers.
            $inset = [float]($source.Width * 0.03)
            $diameter = [float]($source.Width * 0.383)
            $path = New-RoundedRectanglePath `
                -X $inset `
                -Y $inset `
                -Width ($source.Width - 2 * $inset) `
                -Height ($source.Height - 2 * $inset) `
                -Diameter $diameter
            try {
                $graphics.SetClip($path)
                $graphics.DrawImage($source, 0, 0, $source.Width, $source.Height)
            } finally {
                $path.Dispose()
            }
        } finally {
            $graphics.Dispose()
        }

        $sizes = [ordered]@{
            'mdpi' = 48
            'hdpi' = 72
            'xhdpi' = 96
            'xxhdpi' = 144
            'xxxhdpi' = 192
        }
        foreach ($entry in $sizes.GetEnumerator()) {
            $directory = Join-Path $ResourceRoot "mipmap-$($entry.Key)"
            New-Item -ItemType Directory -Path $directory -Force | Out-Null
            $target = New-Object System.Drawing.Bitmap(
                $entry.Value,
                $entry.Value,
                [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
            )
            try {
                $targetGraphics = [System.Drawing.Graphics]::FromImage($target)
                try {
                    $targetGraphics.Clear([System.Drawing.Color]::Transparent)
                    $targetGraphics.CompositingQuality =
                        [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                    $targetGraphics.InterpolationMode =
                        [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                    $targetGraphics.SmoothingMode =
                        [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                    $targetGraphics.PixelOffsetMode =
                        [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                    $targetGraphics.DrawImage($clean, 0, 0, $entry.Value, $entry.Value)
                } finally {
                    $targetGraphics.Dispose()
                }
                $output = Join-Path $directory 'ic_launcher.png'
                $target.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
                Write-Output "$($entry.Key): $output ($($entry.Value)x$($entry.Value))"
            } finally {
                $target.Dispose()
            }
        }
    } finally {
        $clean.Dispose()
    }
} finally {
    $source.Dispose()
}
