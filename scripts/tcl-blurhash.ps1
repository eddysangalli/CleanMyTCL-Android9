# BlurHash for a Home background (algorithm from github.com/woltapp/blurhash, MIT): crops each photo to 16:9,
# darkens the top like the bundled photo and prints "<file> <hash>". How to use it: docs/PERSONALIZATION.md.
param([string[]]$Images, [int]$Cx = 4, [int]$Cy = 3)
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System; using System.Drawing; using System.Text;
public static class BlurHash {
    const string Chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~";
    static double ToLinear(int v) { double c = v / 255.0; return c <= 0.04045 ? c / 12.92 : Math.Pow((c + 0.055) / 1.055, 2.4); }
    static int ToSrgb(double v) { v = Math.Max(0, Math.Min(1, v)); return (int)Math.Round((v <= 0.0031308 ? v * 12.92 : 1.055 * Math.Pow(v, 1 / 2.4) - 0.055) * 255); }
    static double SignPow(double v, double e) { return Math.Sign(v) * Math.Pow(Math.Abs(v), e); }
    static void B83(StringBuilder sb, int value, int length) {
        for (int i = 1; i <= length; i++) { int d = (value / (int)Math.Pow(83, length - i)) % 83; sb.Append(Chars[d]); }
    }
    public static string Encode(Bitmap img, int cx, int cy) {
        int w = img.Width, h = img.Height;
        double[,] lr = new double[w, h], lg = new double[w, h], lb = new double[w, h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) { Color c = img.GetPixel(x, y); lr[x, y] = ToLinear(c.R); lg[x, y] = ToLinear(c.G); lb[x, y] = ToLinear(c.B); }
        double[][] f = new double[cx * cy][];
        for (int j = 0; j < cy; j++) for (int i = 0; i < cx; i++) {
            double norm = (i == 0 && j == 0) ? 1 : 2, r = 0, g = 0, b = 0;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                double basis = norm * Math.Cos(Math.PI * i * x / w) * Math.Cos(Math.PI * j * y / h);
                r += basis * lr[x, y]; g += basis * lg[x, y]; b += basis * lb[x, y];
            }
            double s = 1.0 / (w * h); f[j * cx + i] = new double[] { r * s, g * s, b * s };
        }
        var sb = new StringBuilder();
        B83(sb, (cx - 1) + (cy - 1) * 9, 1);
        double maxAc = 0; for (int k = 1; k < f.Length; k++) for (int c = 0; c < 3; c++) maxAc = Math.Max(maxAc, Math.Abs(f[k][c]));
        int qMax = f.Length > 1 ? Math.Max(0, Math.Min(82, (int)Math.Floor(maxAc * 166 - 0.5))) : 0;
        double maxVal = f.Length > 1 ? (qMax + 1) / 166.0 : 1;
        B83(sb, qMax, 1);
        B83(sb, (ToSrgb(f[0][0]) << 16) + (ToSrgb(f[0][1]) << 8) + ToSrgb(f[0][2]), 4);
        for (int k = 1; k < f.Length; k++) {
            int qr = Math.Max(0, Math.Min(18, (int)Math.Floor(SignPow(f[k][0] / maxVal, 0.5) * 9 + 9.5)));
            int qg = Math.Max(0, Math.Min(18, (int)Math.Floor(SignPow(f[k][1] / maxVal, 0.5) * 9 + 9.5)));
            int qb = Math.Max(0, Math.Min(18, (int)Math.Floor(SignPow(f[k][2] / maxVal, 0.5) * 9 + 9.5)));
            B83(sb, qr * 19 * 19 + qg * 19 + qb, 2);
        }
        return sb.ToString();
    }
}
'@
foreach ($p in (Resolve-Path $Images)) {
    $src = [Drawing.Image]::FromFile($p)
    # Centre 16:9 crop, drawn small: the hash only keeps low frequencies.
    $s = [Math]::Max(64 / $src.Width, 36 / $src.Height); $sw = 64 / $s; $sh = 36 / $s
    $small = New-Object Drawing.Bitmap 64, 36
    $g = [Drawing.Graphics]::FromImage($small); $g.InterpolationMode = 'HighQualityBicubic'
    $g.DrawImage($src, (New-Object Drawing.RectangleF 0, 0, 64, 36),
        (New-Object Drawing.RectangleF (($src.Width - $sw) / 2), (($src.Height - $sh) / 2), $sw, $sh), [Drawing.GraphicsUnit]::Pixel)
    # Same scrim as the bundled photo: 55% black at the top fading out by 45% of the height (header text is white).
    $scrim = New-Object Drawing.Drawing2D.LinearGradientBrush (New-Object Drawing.Rectangle 0, 0, 64, 17),
        ([Drawing.Color]::FromArgb(140, 0, 0, 0)), ([Drawing.Color]::FromArgb(0, 0, 0, 0)), 90
    $g.FillRectangle($scrim, 0, 0, 64, 17); $scrim.Dispose(); $g.Dispose(); $src.Dispose()
    "{0} {1}" -f [IO.Path]::GetFileNameWithoutExtension($p), [BlurHash]::Encode($small, $Cx, $Cy)
    $small.Dispose()
}
