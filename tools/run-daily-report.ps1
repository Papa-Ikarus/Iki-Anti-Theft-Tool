$ErrorActionPreference = "Stop"

$FunctionUrl = "https://ywrhhuhadgtmdzldbawa.supabase.co/functions/v1/daily-report"
$Secret = $env:IKI_DAILY_REPORT_SECRET

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "       Iki Anti-Theft - Tagesbericht" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if ([string]::IsNullOrWhiteSpace($Secret)) {
    Write-Host "FEHLER: IKI_DAILY_REPORT_SECRET ist nicht gesetzt." -ForegroundColor Red
    Write-Host ""
    exit 1
}

Write-Host "Starte Tagesbericht..." -ForegroundColor Yellow
Write-Host ""

try {
    $response = Invoke-RestMethod `
        -Uri $FunctionUrl `
        -Method Post `
        -Headers @{
            Authorization = "Bearer $Secret"
        } `
        -ContentType "application/json"

    Write-Host "Tagesbericht erfolgreich ausgeführt." -ForegroundColor Green
    Write-Host ""

    Write-Host "Ergebnis:" -ForegroundColor Cyan
    Write-Host "  Geräte:     $($response.devices)"
    Write-Host "  Verarbeitet: $($response.processed)"
    Write-Host "  Reports:     $($response.reports)"
    Write-Host "  Locations:  $($response.locations)"
    Write-Host ""

    foreach ($result in $response.results) {
        Write-Host "Gerät: $($result.device_id)" -ForegroundColor Cyan
        Write-Host "  Locations: $($result.location_count)"
        Write-Host "  Report:    $($result.report_created)"
        Write-Host "  Push:      $($result.push_sent)"

        if ($result.error) {
            Write-Host "  Fehler:    $($result.error)" -ForegroundColor Red
        }

        Write-Host ""
    }

    Write-Host "========================================" -ForegroundColor Green
    Write-Host "              FERTIG" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
}
catch {
    Write-Host ""
    Write-Host "FEHLER beim Ausführen des Tagesberichts!" -ForegroundColor Red
    Write-Host ""
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""
    exit 1
}

Write-Host ""
Read-Host "Enter drücken zum Beenden"
