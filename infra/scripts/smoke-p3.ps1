param(
    [string]$Gateway = 'http://localhost:8080',
    [string]$AdminEmail = 'admin@dpp.local',
    [string]$AdminPassword = 'AdminPass123!'
)

$ErrorActionPreference = 'Continue'
$script:pass = 0
$script:fail = 0

function Assert-True {
    param([string]$Name, [bool]$Condition, [string]$Detail = '')
    if ($Condition) {
        $script:pass++
        Write-Host ("PASS  {0}" -f $Name)
    } else {
        $script:fail++
        Write-Host ("FAIL  {0}  {1}" -f $Name, $Detail)
    }
}

function Call {
    param(
        [string]$Method, [string]$Path, [string]$Token,
        $Body, [hashtable]$Headers
    )
    $h = @{}
    if ($Token) { $h['Authorization'] = "Bearer $Token" }
    if ($Headers) { foreach ($k in $Headers.Keys) { $h[$k] = $Headers[$k] } }
    $request = @{
        Method = $Method; Uri = ($Gateway + $Path); Headers = $h
        UseBasicParsing = $true; TimeoutSec = 30
    }
    if ($null -ne $Body) {
        $request['Body'] = ($Body | ConvertTo-Json -Depth 10)
        $request['ContentType'] = 'application/json'
    }
    try {
        $r = Invoke-WebRequest @request
        $json = $null
        try { $json = $r.Content | ConvertFrom-Json } catch {}
        return [pscustomobject]@{ Status = [int]$r.StatusCode; Json = $json; Raw = $r.Content }
    } catch {
        $resp = $_.Exception.Response
        $code = if ($resp) { [int]$resp.StatusCode } else { -1 }
        $raw = ''
        try {
            $stream = $resp.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $raw = $reader.ReadToEnd()
        } catch {}
        $json = $null
        try { $json = $raw | ConvertFrom-Json } catch {}
        return [pscustomobject]@{ Status = $code; Json = $json; Raw = $raw }
    }
}

function Wait-Notification {
    param([string]$Token, [string]$OrderId, [string]$EventType, [int]$TimeoutSec = 40)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $list = Call -Method GET -Path '/notifications' -Token $Token
        if ($list.Status -eq 200 -and $list.Json.content) {
            $match = $list.Json.content | Where-Object { $_.orderId -eq $OrderId -and $_.eventType -eq $EventType }
            if ($match) { return $match }
        }
        Start-Sleep -Seconds 2
    }
    return $null
}

Write-Host "== P3 notifications smoke test =="

$anon = Call -Method GET -Path '/notifications'
Assert-True 'anonymous cannot list notifications (401)' ($anon.Status -eq 401) "status=$($anon.Status)"

$admin = Call -Method POST -Path '/auth/login' -Body @{ email = $AdminEmail; password = $AdminPassword }
Assert-True 'admin login returns token' ($admin.Status -eq 200 -and $admin.Json.accessToken) "status=$($admin.Status)"
$adminToken = $admin.Json.accessToken

$email = "smoke+$([guid]::NewGuid().ToString('N').Substring(0,8))@dpp.local"
$reg = Call -Method POST -Path '/auth/register' -Body @{ email = $email; password = 'Passw0rd!' }
Assert-True 'customer register (201)' ($reg.Status -eq 201) "status=$($reg.Status) $($reg.Raw)"

$login = Call -Method POST -Path '/auth/login' -Body @{ email = $email; password = 'Passw0rd!' }
Assert-True 'customer login returns token' ($login.Status -eq 200 -and $login.Json.accessToken) "status=$($login.Status)"
$customerToken = $login.Json.accessToken

$sku = 'SMOKE-' + ([guid]::NewGuid().ToString('N').Substring(0, 8)).ToUpper()
$product = Call -Method POST -Path '/admin/products' -Token $adminToken -Body @{
    sku = $sku; name = 'Smoke Widget'; description = 'smoke test product'
    price = 12.50; currency = 'USD'; totalStock = 10
}
Assert-True 'admin creates product with stock (201)' ($product.Status -eq 201 -and $product.Json.id) "status=$($product.Status) $($product.Raw)"
$productId = $product.Json.id

$order = Call -Method POST -Path '/orders' -Token $customerToken -Body @{ items = @(@{ productId = $productId; quantity = 2 }) }
Assert-True 'customer creates order (201, PENDING)' ($order.Status -eq 201 -and $order.Json.status -eq 'PENDING') "status=$($order.Status) $($order.Raw)"
$orderId = $order.Json.id

$created = Wait-Notification -Token $customerToken -OrderId $orderId -EventType 'OrderCreated'
Assert-True 'OrderCreated notification delivered (SENT)' ($null -ne $created -and $created.status -eq 'SENT') "found=$($null -ne $created)"
Assert-True 'OrderCreated notification addressed to customer email' ($null -ne $created -and $created.recipient -eq $email) "recipient=$($created.recipient) expected=$email"

$idemKey = 'smoke-' + [guid]::NewGuid().ToString('N')
$payment = Call -Method POST -Path '/payments' -Token $customerToken -Headers @{ 'Idempotency-Key' = $idemKey } -Body @{
    orderId = $orderId; amount = 25.00; currency = 'USD'
}
Assert-True 'customer creates payment (201, PENDING)' ($payment.Status -eq 201 -and $payment.Json.status -eq 'PENDING') "status=$($payment.Status) $($payment.Raw)"
$paymentId = $payment.Json.id

$confirm = Call -Method POST -Path "/payments/$paymentId/confirm" -Token $customerToken -Body @{ outcome = 'SUCCEEDED' }
Assert-True 'payment confirm succeeds' ($confirm.Status -eq 200 -and $confirm.Json.status -eq 'SUCCESS') "status=$($confirm.Status) $($confirm.Raw)"

$succeeded = Wait-Notification -Token $customerToken -OrderId $orderId -EventType 'PaymentSucceeded'
Assert-True 'PaymentSucceeded notification delivered (SENT)' ($null -ne $succeeded -and $succeeded.status -eq 'SENT') "found=$($null -ne $succeeded)"
Assert-True 'PaymentSucceeded notification addressed via read model' ($null -ne $succeeded -and $succeeded.recipient -eq $email) "recipient=$($succeeded.recipient) expected=$email"

$confirmed = Wait-Notification -Token $customerToken -OrderId $orderId -EventType 'OrderConfirmed'
Assert-True 'OrderConfirmed notification delivered (SENT)' ($null -ne $confirmed -and $confirmed.status -eq 'SENT') "found=$($null -ne $confirmed)"

$adminList = Call -Method GET -Path '/admin/notifications' -Token $adminToken
Assert-True 'admin can list all notifications (200)' ($adminList.Status -eq 200 -and $adminList.Json.content) "status=$($adminList.Status)"

$forbidden = Call -Method GET -Path '/admin/notifications' -Token $customerToken
Assert-True 'customer cannot list all notifications (403)' ($forbidden.Status -eq 403) "status=$($forbidden.Status)"

$mine = Call -Method GET -Path '/notifications' -Token $customerToken
$count = 0
if ($mine.Status -eq 200 -and $mine.Json.content) { $count = @($mine.Json.content).Count }
Assert-True 'customer notifications list returns own deliveries' ($mine.Status -eq 200 -and $count -ge 3) "status=$($mine.Status) count=$count"

Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 } else { exit 0 }
