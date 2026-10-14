param([switch]$SpeechAudit)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$signingDirectory = Join-Path $projectRoot '.private'
$keyStore = Join-Path $signingDirectory 'nezabudka-release.jks'
$passwordFile = Join-Path $signingDirectory 'release-password.dpapi'
if (-not (Test-Path -LiteralPath $keyStore -PathType Leaf) -or
    -not (Test-Path -LiteralPath $passwordFile -PathType Leaf)) {
    throw 'Local release signing files are missing. Run tools/create-local-release-key.ps1 once.'
}

function ConvertTo-PlainText([Security.SecureString]$Value) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

$password = ConvertTo-PlainText (Get-Content -LiteralPath $passwordFile -Raw | ConvertTo-SecureString)
$env:NEZABUDKA_KEYSTORE_PATH = $keyStore
$env:NEZABUDKA_STORE_PASSWORD = $password
$env:NEZABUDKA_KEY_ALIAS = 'nezabudka'
$env:NEZABUDKA_KEY_PASSWORD = $password
try {
    Push-Location $projectRoot
    if ($SpeechAudit) {
        & .\gradlew.bat :speechAudit:assembleRelease -PincludeSpeechAudit=true --offline
    } else {
        & .\gradlew.bat :app:assembleRelease --offline
    }
    if ($LASTEXITCODE -ne 0) { throw "Release build failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
    Remove-Item Env:NEZABUDKA_KEYSTORE_PATH,Env:NEZABUDKA_STORE_PASSWORD,Env:NEZABUDKA_KEY_ALIAS,Env:NEZABUDKA_KEY_PASSWORD -ErrorAction SilentlyContinue
    $password = $null
}
