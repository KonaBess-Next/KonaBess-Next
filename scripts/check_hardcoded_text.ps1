<#
.SYNOPSIS
    KonaBess Hardcoded Text Checker
.DESCRIPTION
    Scans Kotlin/Java and non-values XML files for likely user-facing hardcoded text
    that should be moved to Android string resources.
#>

param(
    [switch]$FailOnFound = $false
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$AppDir = Join-Path $ProjectRoot 'app'
$SrcDir = Join-Path $AppDir 'src\main\java'
$ResDir = Join-Path $AppDir 'src\main\res'
$ReportFile = Join-Path $ProjectRoot 'hardcoded_strings_report.md'

$Cyan = "`e[96m"
$Green = "`e[92m"
$Yellow = "`e[93m"
$Red = "`e[91m"
$Bold = "`e[1m"
$Reset = "`e[0m"

function Get-RelativePath {
    param([string]$Path)

    $normalizedRoot = [System.IO.Path]::GetFullPath($ProjectRoot)
    $normalizedPath = [System.IO.Path]::GetFullPath($Path)

    if ($normalizedPath.StartsWith($normalizedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        $rel = $normalizedPath.Substring($normalizedRoot.Length).TrimStart('\\')
        return $rel -replace '\\', '/'
    }

    return $Path
}

function Get-LineNumber {
    param(
        [string]$Text,
        [int]$Index
    )

    if ($Index -le 0) { return 1 }
    return (($Text.Substring(0, $Index) -split "`n").Count)
}

function Normalize-StringValue {
    param([string]$Value)

    if ($null -eq $Value) { return '' }

    $v = $Value
    $v = $v -replace '\\n', ' '
    $v = $v -replace '\\t', ' '
    $v = $v -replace '\\"', '"'
    $v = $v -replace '\\''', "'"
    $v = $v.Trim()

    return $v
}

function Should-IgnoreCandidate {
    param(
        [string]$Value,
        [string]$Snippet
    )

    if ([string]::IsNullOrWhiteSpace($Value)) { return $true }

    $v = Normalize-StringValue -Value $Value

    if ([string]::IsNullOrWhiteSpace($v)) { return $true }

    # Must contain at least one letter (supports unicode letters)
    if ($v -notmatch '\p{L}') { return $true }

    # Ignore likely technical identifiers/paths/uris
    if ($v -match '^(https?:\/\/|content:\/\/|file:\/\/)') { return $true }
    if ($v -match '^[A-Za-z]:\\') { return $true }
    if ($v -match '^[a-z0-9_.\-/:]+$') { return $true }

    # Ignore logs/debug contexts
    if ($Snippet -match '\bLog\.(d|i|w|e|v)\s*\(') { return $true }
    if ($Snippet -match '\bTimber\.') { return $true }
    if ($Snippet -match '\bprintln\s*\(') { return $true }

    return $false
}

function Add-Finding {
    param(
        [ref]$List,
        [string]$File,
        [int]$Line,
        [string]$Kind,
        [string]$Value,
        [string]$Snippet
    )

    $normalizedValue = Normalize-StringValue -Value $Value
    if ($normalizedValue.Length -gt 140) {
        $normalizedValue = $normalizedValue.Substring(0, 137) + '...'
    }

    $singleLineSnippet = (($Snippet -replace "`r", ' ') -replace "`n", ' ').Trim()
    if ($singleLineSnippet.Length -gt 180) {
        $singleLineSnippet = $singleLineSnippet.Substring(0, 177) + '...'
    }

    $List.Value += [PSCustomObject]@{
        File    = Get-RelativePath -Path $File
        Line    = $Line
        Kind    = $Kind
        Value   = $normalizedValue
        Snippet = $singleLineSnippet
    }
}

function Escape-Markdown {
    param([string]$Text)

    if ($null -eq $Text) { return '' }
    return ($Text -replace '\|', '\\|' -replace "`r|`n", ' ')
}

Write-Host ""
Write-Host "${Cyan}+==============================================================+${Reset}"
Write-Host "${Cyan}|${Reset}         ${Bold}${Yellow}KONABESS HARDCODED TEXT CHECKER${Reset}                       ${Cyan}|${Reset}"
Write-Host "${Cyan}+==============================================================+${Reset}"
Write-Host ""

$findings = @()

# 1) Scan Kotlin/Java code for likely user-facing literals in UI contexts
$codeFiles = @()
if (Test-Path $SrcDir) {
    $codeFiles = Get-ChildItem -Path $SrcDir -Recurse -File -Include *.kt, *.java |
        Where-Object { $_.FullName -notmatch '\\build\\|\\generated\\|\\ksp\\|\\tmp\\' }
}

$codePatterns = @(
    @{ Kind = 'Compose.Text literal'; Regex = '(?s)\bText\s*\(\s*(?:text\s*=\s*)?"(?<txt>(?:[^"\\]|\\.)+)"' },
    @{ Kind = 'Compose contentDescription'; Regex = '(?s)\bcontentDescription\s*=\s*"(?<txt>(?:[^"\\]|\\.)+)"' },
    @{ Kind = 'View setText/setTitle'; Regex = '(?s)\b(?:setText|setTitle|setMessage|setSubtitle|setHint)\s*\(\s*"(?<txt>(?:[^"\\]|\\.)+)"' },
    @{ Kind = 'Toast literal'; Regex = '(?s)\bToast\.makeText\s*\([^,]+,\s*"(?<txt>(?:[^"\\]|\\.)+)"' },
    @{ Kind = 'Snackbar literal'; Regex = '(?s)\bSnackbar\.make\s*\([^,]+,\s*"(?<txt>(?:[^"\\]|\\.)+)"' }
)

Write-Host "${Cyan}[1/2]${Reset} Scanning Kotlin/Java files..."
foreach ($file in $codeFiles) {
    $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8

    foreach ($pattern in $codePatterns) {
        $matches = [regex]::Matches($content, $pattern.Regex)
        foreach ($match in $matches) {
            $value = $match.Groups['txt'].Value
            $snippet = $match.Value

            if (Should-IgnoreCandidate -Value $value -Snippet $snippet) {
                continue
            }

            $line = Get-LineNumber -Text $content -Index $match.Index
            Add-Finding -List ([ref]$findings) -File $file.FullName -Line $line -Kind $pattern.Kind -Value $value -Snippet $snippet
        }
    }
}

# 2) Scan non-values XML for hardcoded visible text attributes
$xmlFiles = @()
if (Test-Path $ResDir) {
    $xmlFiles = Get-ChildItem -Path $ResDir -Recurse -File -Include *.xml |
        Where-Object {
            $_.FullName -notmatch '\\values' -and
            $_.FullName -notmatch '\\build\\|\\generated\\|\\tmp\\'
        }
}

$xmlRegex = '(?<attr>(?:android:|app:)(?:text|hint|title|summary|contentDescription|dialogTitle|dialogMessage))\s*=\s*"(?<val>[^"]+)"'

Write-Host "${Cyan}[2/2]${Reset} Scanning non-values XML files..."
foreach ($file in $xmlFiles) {
    $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8
    $matches = [regex]::Matches($content, $xmlRegex)

    foreach ($match in $matches) {
        $value = $match.Groups['val'].Value
        $attr = $match.Groups['attr'].Value

        if ($value.StartsWith('@') -or $value.StartsWith('?')) { continue }
        if ($value -match '^(true|false)$') { continue }

        if (Should-IgnoreCandidate -Value $value -Snippet $match.Value) {
            continue
        }

        $line = Get-LineNumber -Text $content -Index $match.Index
        Add-Finding -List ([ref]$findings) -File $file.FullName -Line $line -Kind "XML $attr" -Value $value -Snippet $match.Value
    }
}

# Deduplicate by file + line + value
$findings = $findings |
    Sort-Object File, Line, Value -Unique

$codeCount = ($findings | Where-Object { $_.Kind -notmatch '^XML ' }).Count
$xmlCount = ($findings | Where-Object { $_.Kind -match '^XML ' }).Count
$totalCount = $findings.Count

Write-Host ""
if ($totalCount -eq 0) {
    Write-Host "${Green}No likely hardcoded user-facing text found. Nice!${Reset}"
}
else {
    Write-Host "${Yellow}Found ${Bold}$totalCount${Reset}${Yellow} likely hardcoded text entries.${Reset}"
    Write-Host "  - Code hits : ${Bold}$codeCount${Reset}"
    Write-Host "  - XML hits  : ${Bold}$xmlCount${Reset}"
}
Write-Host ""

# Generate report
$now = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
$lines = @()
$lines += '# Hardcoded Text Report'
$lines += ''
$lines += "> Generated on: $now"
$lines += ''
$lines += '## Summary'
$lines += ''
$lines += "- Total findings: **$totalCount**"
$lines += "- Code findings: **$codeCount**"
$lines += "- XML findings: **$xmlCount**"
$lines += ''

if ($totalCount -gt 0) {
    $lines += '## Findings'
    $lines += ''
    $lines += '| File | Line | Kind | Value |'
    $lines += '|------|------|------|-------|'

    foreach ($f in $findings) {
        $safeFile = Escape-Markdown -Text $f.File
        $safeKind = Escape-Markdown -Text $f.Kind
        $safeValue = Escape-Markdown -Text $f.Value
        $lines += "| ``$safeFile`` | $($f.Line) | $safeKind | $safeValue |"
    }

    $lines += ''
    $lines += '## Suggested Fix'
    $lines += ''
    $lines += '1. Move text to `res/values/strings.xml` (English base).'
    $lines += '2. Replace literals with `stringResource(R.string.xxx)` (Compose), `getString(R.string.xxx)` (View), or `@string/xxx` (XML).'
    $lines += '3. Run localization checker to sync all locales.'
}
else {
    $lines += '## Findings'
    $lines += ''
    $lines += 'No suspicious hardcoded user-facing text detected.'
}

$lines += ''
$lines += '---'
$lines += ''
$lines += '*Generated by KonaBess Hardcoded Text Checker*'

$lines | Out-File -FilePath $ReportFile -Encoding UTF8

Write-Host "Report saved to: ${Bold}$ReportFile${Reset}"

if ($FailOnFound -and $totalCount -gt 0) {
    Write-Host "${Red}FailOnFound enabled. Exiting with code 2.${Reset}"
    exit 2
}

exit 0
