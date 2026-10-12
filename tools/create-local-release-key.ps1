$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$signingDirectory = Join-Path $projectRoot '.private'
$keyStore = Join-Path $signingDirectory 'nezabudka-release.jks'
$passwordFile = Join-Path $signingDirectory 'release-password.dpapi'

if ((Test-Path -LiteralPath $keyStore) -or (Test-Path -LiteralPath $passwordFile)) {
    throw 'Release signing files already exist. Refusing to replace them.'
}

$keytool = (Get-Command keytool -ErrorAction Stop).Source
New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
$randomBytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
$password = [Convert]::ToBase64String($randomBytes)
try {
    & $keytool -genkeypair -keystore $keyStore -storetype PKCS12 -alias nezabudka `
        -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 10000 `
        -dname 'CN=NeZabudKa, OU=Android, O=Nikita Revenko, C=RU' `
        -storepass $password -keypass $password -noprompt
    if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }
    ConvertTo-SecureString $password -AsPlainText -Force |
        ConvertFrom-SecureString |
        Set-Content -LiteralPath $passwordFile -NoNewline
    Write-Host "Release key created in $signingDirectory. Keep a separate backup of the keystore and password."
} finally {
    $password = $null
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
}
