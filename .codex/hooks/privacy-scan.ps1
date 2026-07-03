Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

try {
    $repoRoot = (& git rev-parse --show-toplevel).Trim()
} catch {
    exit 0
}

if ([string]::IsNullOrWhiteSpace($repoRoot)) {
    exit 0
}

$issues = New-Object System.Collections.Generic.List[string]

$trackedEnvFiles = & git -C $repoRoot ls-files -- ".env" ".env.*"
foreach ($envFile in $trackedEnvFiles) {
    if ([string]::IsNullOrWhiteSpace($envFile)) {
        continue
    }

    $allowed = @(".env.example", ".env.sample", ".env.template")
    if ($envFile -eq ".env" -or ($envFile.StartsWith(".env.") -and $allowed -notcontains $envFile)) {
        $issues.Add("${envFile}: tracked env file")
    }
}

$changedFiles = @()
& git -C $repoRoot rev-parse --verify HEAD *> $null
if ($LASTEXITCODE -eq 0) {
    $changedFiles += & git -C $repoRoot diff --name-only --diff-filter=ACMRTUXB HEAD --
} else {
    $changedFiles += & git -C $repoRoot diff --name-only --diff-filter=ACMRTUXB --
}
$changedFiles += & git -C $repoRoot ls-files --others --exclude-standard
$changedFiles = $changedFiles | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object -Unique

$skipPrefixes = @(
    "target/",
    ".git/",
    ".graphify/cache/",
    ".idea/",
    ".vscode/"
)

$secretPatterns = @(
    @{
        Name = "literal secret assignment"
        Regex = "\b(OPENROUTER_API_KEY|GEMINI_API_KEY|TELEGRAM_BOT_TOKEN|FITNESS_APP_SECRET|DB_PASSWORD|FATSECRET_CLIENT_SECRET|FATSECRET_CONSUMER_SECRET)\s*[:=]\s*['""]?(?!\$\{|<|YOUR_|your-|test-|example|change-me|0000000000:test-token)[^'""\s#]+"
    },
    @{
        Name = "OpenAI-style API key"
        Regex = "\b(sk-or-v1-|sk-proj-|sk-)[A-Za-z0-9_\-]{20,}"
    },
    @{
        Name = "Telegram bot token"
        Regex = "\b\d{8,12}:[A-Za-z0-9_-]{30,}\b"
    },
    @{
        Name = "JWT token"
        Regex = "\beyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}\b"
    }
)

$javaPrivacyPatterns = @(
    @{
        Name = "raw Telegram logging phrase"
        Regex = "Message from \{\}:\s*\{\}|No handler found for message"
    },
    @{
        Name = "logging update message text"
        Regex = "log\.\w+\s*\([^;]*update\.getMessage\(\)\.getText\(\)"
    }
)

foreach ($relativePath in $changedFiles) {
    $normalized = $relativePath -replace "\\", "/"
    $skip = $false
    foreach ($prefix in $skipPrefixes) {
        if ($normalized.StartsWith($prefix)) {
            $skip = $true
            break
        }
    }
    if ($skip) {
        continue
    }

    $fullPath = Join-Path $repoRoot $relativePath
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        continue
    }

    $item = Get-Item -LiteralPath $fullPath
    if ($item.Length -gt 1048576) {
        continue
    }

    try {
        $content = Get-Content -LiteralPath $fullPath -Raw -ErrorAction Stop
    } catch {
        continue
    }

    foreach ($pattern in $secretPatterns) {
        if ([regex]::IsMatch($content, $pattern.Regex, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
            $issues.Add("${relativePath}: $($pattern.Name)")
        }
    }

    if ($normalized.StartsWith("src/main/java/") -or $normalized.StartsWith("src/test/java/")) {
        foreach ($pattern in $javaPrivacyPatterns) {
            if ([regex]::IsMatch($content, $pattern.Regex, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
                $issues.Add("${relativePath}: $($pattern.Name)")
            }
        }
    }
}

if ($issues.Count -gt 0) {
    Write-Host "Privacy scan found potential issues. Review the named files; values are intentionally not printed."
    foreach ($issue in $issues) {
        Write-Host " - $issue"
    }
    exit 1
}

exit 0
