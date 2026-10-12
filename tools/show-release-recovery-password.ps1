$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$passwordFile = Join-Path $projectRoot '.private/release-password.dpapi'
if (-not (Test-Path -LiteralPath $passwordFile -PathType Leaf)) { throw 'Encrypted signing password not found.' }
$secure = Get-Content -LiteralPath $passwordFile -Raw | ConvertTo-SecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    Write-Host 'Store this password with a separate backup of .private/nezabudka-release.jks:'
    Write-Host ([Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer))
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}
