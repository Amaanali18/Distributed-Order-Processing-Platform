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

Write-Host "== P2 saga smoke test =="

$anon = Call -Method POST -Path '/orders' -Body @{ items = @(@{ productId = '00000000-0000-0000-0000-000000000000'; quantity = 1 }) }
Assert-True 'anonymous cannot create order (401)' ($anon.Status -eq 401) "status=$($anon.Status)"

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
Assert-True 'order total is price x quantity' ([decimal]$order.Json.totalAmount -eq 25.00) "total=$($order.Json.totalAmount)"

$afterReserve = Call -Method GET -Path "/products/$productId"
Assert-True 'reservation holds 2 units' ($afterReserve.Json.reservedStock -eq 2 -and $afterReserve.Json.availableStock -eq 8) "reserved=$($afterReserve.Json.reservedStock) available=$($afterReserve.Json.availableStock)"

$idemKey = 'smoke-' + [guid]::NewGuid().ToString('N')
$payment = Call -Method POST -Path '/payments' -Token $customerToken -Headers @{ 'Idempotency-Key' = $idemKey } -Body @{
    orderId = $orderId; amount = 25.00; currency = 'USD'
}
Assert-True 'customer creates payment (201, PENDING)' ($payment.Status -eq 201 -and $payment.Json.status -eq 'PENDING') "status=$($payment.Status) $($payment.Raw)"
$paymentId = $payment.Json.id

$paymentRetry = Call -Method POST -Path '/payments' -Token $customerToken -Headers @{ 'Idempotency-Key' = $idemKey } -Body @{
    orderId = $orderId; amount = 25.00; currency = 'USD'
}
Assert-True 'payment create is idempotent (same id)' ($paymentRetry.Json.id -eq $paymentId) "id1=$paymentId id2=$($paymentRetry.Json.id)"

$confirm = Call -Method POST -Path "/payments/$paymentId/confirm" -Token $customerToken -Body @{ outcome = 'SUCCEEDED' }
Assert-True 'payment confirm succeeds' ($confirm.Status -eq 200 -and $confirm.Json.status -eq 'SUCCEEDED') "status=$($confirm.Status) $($confirm.Raw)"

$orderStatus = $null
$deadline = (Get-Date).AddSeconds(40)
while ((Get-Date) -lt $deadline) {
    $current = Call -Method GET -Path "/orders/$orderId" -Token $customerToken
    $orderStatus = $current.Json.status
    if ($orderStatus -eq 'CONFIRMED') { break }
    Start-Sleep -Seconds 2
}
Assert-True 'saga confirms the order (PaymentSucceeded -> CONFIRMED)' ($orderStatus -eq 'CONFIRMED') "status=$orderStatus"

$afterCommit = Call -Method GET -Path "/products/$productId"
Assert-True 'inventory committed (stock 10 -> 8, reserved 0)' ($afterCommit.Json.totalStock -eq 8 -and $afterCommit.Json.reservedStock -eq 0) "total=$($afterCommit.Json.totalStock) reserved=$($afterCommit.Json.reservedStock)"

$order2 = Call -Method POST -Path '/orders' -Token $customerToken -Body @{ items = @(@{ productId = $productId; quantity = 1 }) }
Assert-True 'second order created (PENDING)' ($order2.Status -eq 201 -and $order2.Json.status -eq 'PENDING') "status=$($order2.Status)"
$order2Id = $order2.Json.id

$cancel = Call -Method POST -Path "/orders/$order2Id/cancel" -Token $customerToken
Assert-True 'cancel pending order -> CANCELLED' ($cancel.Status -eq 200 -and $cancel.Json.status -eq 'CANCELLED') "status=$($cancel.Status) $($cancel.Raw)"

$afterRelease = Call -Method GET -Path "/products/$productId"
Assert-True 'cancelled order releases reservation' ($afterRelease.Json.reservedStock -eq 0 -and $afterRelease.Json.availableStock -eq 8) "reserved=$($afterRelease.Json.reservedStock) available=$($afterRelease.Json.availableStock)"

Write-Host ("== result: {0} passed, {1} failed ==" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 } else { exit 0 }
