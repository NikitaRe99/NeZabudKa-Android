param([Parameter(Mandatory = $true)][string]$Version)

$ErrorActionPreference = 'Stop'
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw 'Expected a version such as 0.2.54.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$tag = "v$Version"
$api = 'https://api.github.com/repos/NikitaRe99/NeZabudKa-Android'
$notesPath = Join-Path $projectRoot "docs/releases/$Version.md"
if (-not (Test-Path -LiteralPath $notesPath -PathType Leaf)) { throw "Missing release notes: $notesPath" }
$releaseNotes = (Get-Content -LiteralPath $notesPath -Raw -Encoding UTF8) -replace '^# [^\r\n]+\r?\n\s*', ''
if ([string]::IsNullOrWhiteSpace($releaseNotes)) { throw 'Release notes are empty.' }
$files = @('arm64-v8a', 'armeabi-v7a', 'x86_64') | ForEach-Object {
    Join-Path $projectRoot "android/app/build/outputs/apk/release/app-$_-release.apk"
}
foreach ($file in $files) { if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing signed release APK: $file" } }

Push-Location $projectRoot
try {
    $safeGit = @('-c', "safe.directory=$($projectRoot.Replace('\','/'))")
    $head = (& git @safeGit rev-parse HEAD).Trim()
    $tagHead = (& git @safeGit rev-parse "refs/tags/$tag^{commit}" 2>$null).Trim()
    if ($LASTEXITCODE -ne 0 -or $tagHead -ne $head) { throw "Tag $tag must point to the current commit." }
    $remoteTag = & git @safeGit ls-remote --tags origin "refs/tags/$tag"
    if (-not $remoteTag) { throw "Push tag $tag before publishing." }

    $credentialText = "protocol=https`nhost=github.com`n`n" | git @safeGit credential fill
    $secretLine = $credentialText | Where-Object { $_ -like 'password=*' } | Select-Object -First 1
    if (-not $secretLine) { throw 'GitHub credential unavailable.' }
    $token = $secretLine.Substring(9)
    $headers = @{ Authorization = "Bearer $token"; Accept = 'application/vnd.github+json'; 'User-Agent' = 'NeZabudKa-publisher' }
    try {
        try { $release = Invoke-RestMethod -Uri "$api/releases/tags/$tag" -Headers $headers }
        catch {
            if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw }
            $body = @{
                tag_name = $tag
                target_commitish = $head
                name = "НеЗабудьКа $Version"
                body = $releaseNotes
                draft = $true
                prerelease = $false
                make_latest = 'false'
            } | ConvertTo-Json
            $release = Invoke-RestMethod -Method Post -Uri "$api/releases" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
        }
        if (-not $release.draft) { throw "Release $tag is already published." }
        foreach ($file in $files) {
            $name = [IO.Path]::GetFileName($file)
            $hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
            $size = (Get-Item -LiteralPath $file).Length
            $asset = $release.assets | Where-Object { $_.name -eq $name } | Select-Object -First 1
            if (-not $asset) {
                $uploadUrl = "https://uploads.github.com/repos/NikitaRe99/NeZabudKa-Android/releases/$($release.id)/assets?name=$([uri]::EscapeDataString($name))"
                $asset = Invoke-RestMethod -Method Post -Uri $uploadUrl -Headers $headers -ContentType 'application/vnd.android.package-archive' -InFile $file
            }
            if ($asset.size -ne $size -or $asset.digest -ne "sha256:$hash") {
                throw "Uploaded asset failed integrity check: $name"
            }
            Write-Host "Verified $name ($size bytes)"
        }
        $publishBody = @{ draft = $false; prerelease = $false; make_latest = 'true'; body = $releaseNotes } | ConvertTo-Json
        $published = Invoke-RestMethod -Method Patch -Uri "$api/releases/$($release.id)" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($publishBody))
        Write-Host "Published $($published.html_url)"
    } finally {
        $headers = $null
        $token = $null
        $secretLine = $null
        $credentialText = $null
    }
} finally {
    Pop-Location
}
