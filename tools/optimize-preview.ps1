param(
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$DeviceSerial = ""
)

$ErrorActionPreference = "Stop"
if (!(Test-Path -LiteralPath $AdbPath)) { throw "ADB не найден: $AdbPath" }
$taskDeviceArgs = @()
if ($DeviceSerial) { $taskDeviceArgs = @("-s", $DeviceSerial) }
$taskPackage = "ru.nezabudka.app.debug"
$taskPackageInfo = & $AdbPath @taskDeviceArgs shell dumpsys package $taskPackage
if ($LASTEXITCODE -ne 0) { throw "Не удалось прочитать сведения о приложении. Подключите один телефон или задайте DeviceSerial." }
if (!($taskPackageInfo -match 'versionName=')) { throw "Сначала установите preview-сборку приложения." }
if ($taskPackageInfo -match 'flags=\[.*\bDEBUGGABLE\b') {
    throw "Установлена debug-сборка. Выберите preview в Build Variants и обновите приложение перед оптимизацией."
}
& $AdbPath @taskDeviceArgs shell cmd package compile -m speed -f $taskPackage
if ($LASTEXITCODE -ne 0) { throw "Предварительная компиляция не завершилась." }
Write-Output "Код preview-сборки предварительно скомпилирован. Для проверки запустите приложение заново."
