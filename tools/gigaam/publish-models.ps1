param([string]$Tag = 'models-v3')
$ErrorActionPreference = 'Stop'
if ($Tag -ne 'models-v3') { throw 'This verified catalog uses the pinned tag models-v3.' }
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$directory = Join-Path $root '.gigaam-cache/publish'
$manifest = Get-Content (Join-Path $directory 'manifest.json') -Raw | ConvertFrom-Json
$publicModels = @($manifest.gigaam | ForEach-Object {
    @{file=$_.file; version=$_.version; bytes=$_.bytes; sha256=$_.sha256; checkpointSha256=$_.checkpointSha256; quantization='dynamic per-channel INT8 MatMul, FP32 frontend and convolutions'}
})
@{format='gigaam-waveform-ctc-1';sampleRate=16000;gigaam=$publicModels;nlu=$manifest.nlu} |
    ConvertTo-Json -Depth 8 | Set-Content (Join-Path $directory 'MODELS.json') -Encoding utf8
$notice = Get-Content (Join-Path $root 'android/app/src/main/assets/nlu/NOTICE.txt') -Raw
$gigaamLicense = Get-Content (Join-Path $root '.gigaam-cache/GigaAM/LICENSE') -Raw
"# GigaAM v1/v2/v3 CTC`n`nSource: https://github.com/salute-developers/GigaAM`nUpstream commit: 7447938d791c4f3e643386ee22c33777004293a5`nDerived waveform ONNX INT8 models for NeZabudKa.`n`n$gigaamLicense`n`n# Classic NLU v8`n`n$notice" |
    Set-Content (Join-Path $directory 'MODEL_LICENSES.md') -Encoding utf8
$body = @'
Модели для НеЗабудьКа 0.2.80 и новее. Скачиваются отдельно от APK и работают локально.

| Файл | Назначение | Размер |
|---|---|---:|
| gigaam-v3-ctc-int8.onnx | Рекомендуемая GigaAM для русской речи | около 306 МиБ |
| gigaam-v2-ctc-int8.onnx | Предыдущая версия для сравнения | около 324 МиБ |
| gigaam-v1-ctc-int8.onnx | Первая версия для сравнения | около 361 МиБ |
| classic_nlu_v8_int8.onnx | Понимание команд и выделение их полей; не распознаёт звук | около 28 МиБ |

**GigaAM:** производные от официальных CTC v1/v2/v3, MIT. ONNX opset 17 со встроенным log-mel frontend, вход mono PCM16/float32 16 kHz. Посимвольный greedy CTC; без текстовых подсказок, NLU и восстановления пунктуации. Словарь и описание формата встроены в каждый файл. Это специальный формат НеЗабудьКа, не обычный ONNX-экспорт энкодера. RNNT здесь не представлен.

Квантование динамическое per-channel INT8 для линейных MatMul. Frontend и свёртки остаются FP32. На 9 известных контрольных записях (57 слов, 7 разных фраз) v1/v2 дали по 2 ошибки, v3 — 1; INT8 сохранила текст исходных моделей на всех 11 входах, включая два варианта исходного звука. Выборка небольшая и не слепая, это не гарантия такой же точности на другой речи. Для слабых телефонов нужен отдельный замер.

**NLU v8:** дообученная cointegrated/rubert-tiny2 с метками и токенизатором НеЗабудьКа. Поддерживает свободные формулировки напоминаний, заметок и папок. Стандартные команды продолжают работать без NLU. Предыдущие модели не удалены.

В приложении: Настройки → Речь и команды → Распознавание речи → GigaAM, затем выбор модели. Для NLU: Распознавание команд → Выбор модели NLU. Для своей загрузки используйте соответствующее окно импорта.

SHA-256 и точные размеры: MODELS.json. Лицензии: MODEL_LICENSES.md.
Этот релиз содержит модели, а не обновление приложения; он не назначается последней версией APK.
'@
$files = @('gigaam-v1-ctc-int8.onnx','gigaam-v2-ctc-int8.onnx','gigaam-v3-ctc-int8.onnx','classic_nlu_v8_int8.onnx','MODELS.json','MODEL_LICENSES.md')
foreach ($name in $files) { if (-not (Test-Path -LiteralPath (Join-Path $directory $name))) { throw "Missing $name" } }
$credential = "protocol=https`nhost=github.com`n`n" | git credential fill
$secret = $credential | Where-Object { $_ -like 'password=*' } | Select-Object -First 1
if (-not $secret) { throw 'GitHub credential unavailable' }
$headers = @{Authorization="Bearer $($secret.Substring(9))";Accept='application/vnd.github+json';'User-Agent'='NeZabudKa-model-publisher'}
$api = 'https://api.github.com/repos/NikitaRe99/NeZabudKa-Android'
try {
    try { $release = Invoke-RestMethod "$api/releases/tags/$Tag" -Headers $headers }
    catch {
        if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw }
        $payload = @{tag_name=$Tag;target_commitish='main';name='Модели: GigaAM v1/v2/v3 INT8 и NLU v8';body=$body;draft=$true;prerelease=$false;make_latest='false'} | ConvertTo-Json
        $release = Invoke-RestMethod -Method Post "$api/releases" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($payload))
    }
    foreach ($name in $files) {
        $file = Join-Path $directory $name
        $hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
        $size = (Get-Item -LiteralPath $file).Length
        $asset = $release.assets | Where-Object {$_.name -eq $name} | Select-Object -First 1
        if (-not $asset) {
            $url = "https://uploads.github.com/repos/NikitaRe99/NeZabudKa-Android/releases/$($release.id)/assets?name=$([uri]::EscapeDataString($name))"
            $asset = Invoke-RestMethod -Method Post $url -Headers $headers -ContentType 'application/octet-stream' -InFile $file -TimeoutSec 1800
        }
        if ($asset.size -ne $size -or $asset.digest -ne "sha256:$hash") { throw "Asset checksum mismatch: $name" }
        Write-Output "Verified $name ($size bytes)"
    }
    $payload = @{draft=$false;prerelease=$false;make_latest='false';body=$body} | ConvertTo-Json
    $published = Invoke-RestMethod -Method Patch "$api/releases/$($release.id)" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($payload))
    Write-Output "Published $($published.html_url)"
} finally { $headers=$null; $secret=$null; $credential=$null }
