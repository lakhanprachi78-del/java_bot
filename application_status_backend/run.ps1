Set-Location $PSScriptRoot

$env:SPRING_PROFILES_ACTIVE = "dev"

if (-not (Test-Path ".env")) {
    throw "Missing .env file in $PSScriptRoot"
}

Get-Content ".env" | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#")) {
        $parts = $line.Split('=', 2)
        if ($parts.Length -eq 2) {
            $name = $parts[0].Trim()
            $value = $parts[1].Trim().Trim('"').Trim("'")
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

if ($env:DATABASE_URL -match '^postgresql://') {
    $databaseUri = [Uri]$env:DATABASE_URL
    $credentials = $databaseUri.UserInfo.Split(':', 2)
    $env:LOS_DATABASE_URL = "jdbc:postgresql://$($databaseUri.Host):$($databaseUri.Port)$($databaseUri.AbsolutePath)"
    $env:LOS_DATABASE_USERNAME = [Uri]::UnescapeDataString($credentials[0])
    $env:LOS_DATABASE_PASSWORD = [Uri]::UnescapeDataString($credentials[1])
}

if (-not $env:LOS_DATABASE_URL) {
    throw "DATABASE_URL must be a PostgreSQL URL"
}

$maven = "C:\Users\prachi.lakhan\Downloads\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd"
& $maven spring-boot:run
