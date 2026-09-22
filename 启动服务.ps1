param([switch]$NoBrowser)
$ErrorActionPreference='Stop'
$projectRoot=$PSScriptRoot
Set-Location -LiteralPath $projectRoot
$env:OLLAMA_MODELS=Join-Path $projectRoot 'models'
$env:OLLAMA_HOST='127.0.0.1:11434'
$env:OLLAMA_NO_CLOUD='1'
$dataPath=Join-Path $projectRoot 'data'
New-Item -ItemType Directory -Path $dataPath -Force | Out-Null
try { Invoke-RestMethod 'http://127.0.0.1:11434/api/tags' -TimeoutSec 2 | Out-Null }
catch { Start-Process -FilePath (Join-Path $projectRoot 'tools/ollama/ollama.exe') -ArgumentList 'serve' -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $dataPath 'ollama-out.log') -RedirectStandardError (Join-Path $dataPath 'ollama-error.log') | Out-Null }
$healthy=$false
try { $response=Invoke-RestMethod 'http://127.0.0.1:8086/api/v2/health' -TimeoutSec 3; $healthy=$null -ne $response.model } catch {}
if(-not $healthy) {
    $java=(Get-Command java -ErrorAction Stop).Source
    Start-Process -FilePath $java -ArgumentList '-Dfile.encoding=UTF-8','-jar','target/math-video-ai-1.0.0.jar','--server.port=8086' -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $dataPath 'app-out.log') -RedirectStandardError (Join-Path $dataPath 'app-error.log') | Out-Null
    for($i=0;$i -lt 30;$i++) {
        Start-Sleep -Seconds 1
        try { $response=Invoke-RestMethod 'http://127.0.0.1:8086/api/v2/health' -TimeoutSec 2; if($null -ne $response.model){$healthy=$true;break} } catch {}
    }
}
if(-not $healthy){throw 'Startup failed. Check data/app-out.log and port 8086.'}
Write-Host 'Math learning is ready: http://localhost:8086/'
if(-not $NoBrowser){Start-Process 'http://localhost:8086/'}
