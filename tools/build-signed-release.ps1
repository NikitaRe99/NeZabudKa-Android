param(
    [Parameter(Mandatory = $true)]
    [string]$KeyStorePath,
    [Parameter(Mandatory = $true)]
    [string]$KeyAlias
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$gradleWrapper = Join-Path $projectRoot "gradlew.bat"

if (-not (Test-Path -LiteralPath $KeyStorePath -PathType Leaf)) {
    throw "Keystore not found: $KeyStorePath"
}

if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "Gradle wrapper not found: $gradleWrapper"
}

function ConvertTo-PlainText([Security.SecureString]$Value) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

$storePassword = ConvertTo-PlainText (Read-Host "Keystore password" -AsSecureString)
$keyPassword = ConvertTo-PlainText (Read-Host "Key password (usually the same)" -AsSecureString)

$env:NEZABUDKA_KEYSTORE_PATH = (Resolve-Path -LiteralPath $KeyStorePath).Path
$env:NEZABUDKA_STORE_PASSWORD = $storePassword
$env:NEZABUDKA_KEY_ALIAS = $KeyAlias
$env:NEZABUDKA_KEY_PASSWORD = $keyPassword

try {
    Push-Location $projectRoot
    & $gradleWrapper :app:assembleRelease
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle release build failed with exit code $LASTEXITCODE"
    }
    Write-Host "Signed release APKs are ready." -ForegroundColor Green
} finally {
    Pop-Location
    Remove-Item Env:NEZABUDKA_KEYSTORE_PATH -ErrorAction SilentlyContinue
    Remove-Item Env:NEZABUDKA_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:NEZABUDKA_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:NEZABUDKA_KEY_PASSWORD -ErrorAction SilentlyContinue
    $storePassword = $null
    $keyPassword = $null
}

Read-Host "Press Enter to close"
