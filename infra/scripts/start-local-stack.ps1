param(
    [string]$AdminEmail = 'admin@dpp.local',
    [string]$AdminPassword = 'AdminPass123!'
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$logs = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

$java = 'java'
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
}

$services = @(
    @{ Name = 'discovery-server'; Port = 8761; Jar = 'discovery-server\target\discovery-server-1.0.0-SNAPSHOT.jar'; Env = $null },
    @{ Name = 'auth-service';     Port = 8081; Jar = 'auth-service\target\auth-service-1.0.0-SNAPSHOT.jar';     Env = @{ DPP_BOOTSTRAP_ADMIN_EMAIL = $AdminEmail; DPP_BOOTSTRAP_ADMIN_PASSWORD = $AdminPassword } },
    @{ Name = 'catalog-service';  Port = 8082; Jar = 'catalog-service\target\catalog-service-1.0.0-SNAPSHOT.jar';  Env = @{ DPP_INTERNAL_SERVICE_TOKEN = 'dpp-internal-dev-token' } },
    @{ Name = 'order-service';    Port = 8083; Jar = 'order-service\target\order-service-1.0.0-SNAPSHOT.jar';      Env = @{ DPP_INTERNAL_SERVICE_TOKEN = 'dpp-internal-dev-token' } },
    @{ Name = 'payment-service';  Port = 8084; Jar = 'payment-service\target\payment-service-1.0.0-SNAPSHOT.jar';  Env = @{ DPP_INTERNAL_SERVICE_TOKEN = 'dpp-internal-dev-token'; PAYMENT_PROVIDER = 'simulated' } },
    @{ Name = 'api-gateway';      Port = 8080; Jar = 'api-gateway\target\api-gateway-1.0.0-SNAPSHOT.jar';         Env = $null }
)

$pidFile = Join-Path $logs 'stack-pids.txt'
$pids = @()

function Wait-Healthy {
    param([string]$url, [int]$timeoutSec, [string]$label)
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq 200) {
                Write-Host "healthy: $label"
                return $true
            }
        } catch {
            Start-Sleep -Milliseconds 1500
        }
    }
    Write-Host "TIMEOUT waiting for $label ($url)"
    return $false
}

function Start-Group {
    param($items)
    foreach ($svc in $items) {
        if ($svc.Env) {
            foreach ($key in $svc.Env.Keys) {
                Set-Item -Path ("Env:" + $key) -Value $svc.Env[$key]
            }
        }
        $jarPath = Join-Path $root $svc.Jar
        $out = Join-Path $logs ($svc.Name + '.log')
        $err = Join-Path $logs ($svc.Name + '.err.log')
        $argumentList = '-jar "' + $jarPath + '"'
        $proc = Start-Process -FilePath $java -ArgumentList $argumentList `
            -RedirectStandardOutput $out -RedirectStandardError $err `
            -PassThru -WindowStyle Hidden
        $script:pids += $proc.Id
        Write-Output ("started {0} pid={1}" -f $svc.Name, $proc.Id)
    }
}

Start-Group -items @($services[0])
if (-not (Wait-Healthy 'http://localhost:8761/actuator/health' 120 'discovery-server')) { exit 1 }

Start-Group -items @($services[1], $services[2])
if (-not (Wait-Healthy 'http://localhost:8081/actuator/health' 120 'auth-service')) { exit 1 }
if (-not (Wait-Healthy 'http://localhost:8082/actuator/health' 120 'catalog-service')) { exit 1 }

Start-Group -items @($services[3], $services[4])
if (-not (Wait-Healthy 'http://localhost:8083/actuator/health' 120 'order-service')) { exit 1 }
if (-not (Wait-Healthy 'http://localhost:8084/actuator/health' 120 'payment-service')) { exit 1 }

Start-Group -items @($services[5])
if (-not (Wait-Healthy 'http://localhost:8080/actuator/health' 120 'api-gateway')) { exit 1 }

Set-Content -Path $pidFile -Value ($pids -join "`n")
Write-Output "stack up; pids in $pidFile"
