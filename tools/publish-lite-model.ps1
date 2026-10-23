param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$api = 'https://api.github.com/repos/NikitaRe99/NeZabudKa-Android'
$tag = 'models-v4'
$model = Join-Path $projectRoot 'build/models/gigaam-v3-lite/gigaam-v3-ctc-lite-int8.onnx'
$expectedHash = '187509b2f8c21a1306ff2baad8d0347364f1fb3de97e06d268d5927019744ddc'
if ((Get-FileHash -LiteralPath $model -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedHash) {
    throw 'Unexpected Lite model: refusing to publish.'
}
$assets = @(
    @{ Path = $model; Name = 'gigaam-v3-ctc-lite-int8.onnx'; Type = 'application/octet-stream' },
    @{ Path = (Join-Path $projectRoot 'build/models/gigaam-v3-lite/LICENSE'); Name = 'GigaAM-Lite-LICENSE.txt'; Type = 'text/plain' },
    @{ Path = (Join-Path $projectRoot 'docs/gigaam/lite-results-2026-10-23.json'); Name = 'gigaam-v3-lite-results.json'; Type = 'application/json' },
    @{ Path = (Join-Path $projectRoot 'build/models/gigaam-v3-lite/README.md'); Name = 'GigaAM-v3-Lite-README.md'; Type = 'text/plain' }
)
foreach ($asset in $assets) { if (-not (Test-Path -LiteralPath $asset.Path -PathType Leaf)) { throw "Missing $($asset.Name)" } }
$notes = (Get-Content -LiteralPath (Join-Path $projectRoot 'docs/releases/models-v4.md') -Raw -Encoding UTF8) -replace '^# [^\r\n]+\r?\n\s*', ''
Push-Location $projectRoot
try {
    $safeGit = @('-c', "safe.directory=$($projectRoot.Replace('\','/'))")
    $head = (& git @safeGit rev-parse HEAD).Trim()
    $remoteHead = ((& git @safeGit ls-remote origin refs/heads/main) -split '\s+')[0]
    if ($head -ne $remoteHead) { throw 'Push the verified source commit to main before publishing model assets.' }
    $credentialText = "protocol=https`nhost=github.com`n`n" | git @safeGit credential fill
    $secretLine = $credentialText | Where-Object { $_ -like 'password=*' } | Select-Object -First 1
    if (-not $secretLine) { throw 'GitHub credential unavailable.' }
    $headers = @{ Authorization = "Bearer $($secretLine.Substring(9))"; Accept = 'application/vnd.github+json'; 'User-Agent' = 'NeZabudKa-model-publisher' }
    try {
        try { $release = Invoke-RestMethod -Uri "$api/releases/tags/$tag" -Headers $headers }
        catch {
            if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw }
            $body = @{ tag_name = $tag; target_commitish = $head; name = 'GigaAM v3 Lite INT8'; body = $notes
                draft = $true; prerelease = $false; make_latest = 'false' } | ConvertTo-Json
            $release = Invoke-RestMethod -Method Post -Uri "$api/releases" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
        }
        foreach ($asset in $assets) {
            $hash = (Get-FileHash -LiteralPath $asset.Path -Algorithm SHA256).Hash.ToLowerInvariant()
            $size = (Get-Item -LiteralPath $asset.Path).Length
            $remote = $release.assets | Where-Object { $_.name -eq $asset.Name } | Select-Object -First 1
            if (-not $remote) {
                if (-not $release.draft) { throw 'Published model release is incomplete; refusing to mutate it.' }
                $url = "https://uploads.github.com/repos/NikitaRe99/NeZabudKa-Android/releases/$($release.id)/assets?name=$([uri]::EscapeDataString($asset.Name))"
                $remote = Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType $asset.Type -InFile $asset.Path -TimeoutSec 900
            }
            if ($remote.size -ne $size -or $remote.digest -ne "sha256:$hash") { throw "Remote integrity check failed: $($asset.Name)" }
            Write-Host "Verified $($asset.Name): $size bytes"
        }
        if ($release.draft) {
            $body = @{ draft = $false; prerelease = $false; make_latest = 'false'; body = $notes } | ConvertTo-Json
            $release = Invoke-RestMethod -Method Patch -Uri "$api/releases/$($release.id)" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
        }
        Write-Host "Published $($release.html_url)"
    } finally {
        $headers = $null; $credentialText = $null; $secretLine = $null
    }
} finally { Pop-Location }
