<# 
.SYNOPSIS
    KonaBess Localization Checker
.DESCRIPTION
    Professional tool for detecting missing string translations in Android projects.
    Auto-detects locales and respects translatable="false".
#>

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Paths
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$ResDir = Join-Path $ProjectRoot 'app\src\main\res'
$ReportFile = Join-Path $ProjectRoot 'localization_report.md'

# Colors
$Green = "`e[92m"
$Red = "`e[91m"
$Yellow = "`e[93m"
$Cyan = "`e[96m"
$Bold = "`e[1m"
$Dim = "`e[2m"
$Reset = "`e[0m"

# Header
Write-Host ""
Write-Host "${Cyan}+==============================================================+${Reset}"
Write-Host "${Cyan}|${Reset}      ${Bold}${Yellow}KONABESS LOCALIZATION CHECKER${Reset}                          ${Cyan}|${Reset}"
Write-Host "${Cyan}|${Reset}      ${Dim}Professional Android String Resource Analyzer${Reset}           ${Cyan}|${Reset}"
Write-Host "${Cyan}+==============================================================+${Reset}"
Write-Host ""

# Check paths
$BaseFile = Join-Path $ResDir 'values\strings.xml'
if (-not (Test-Path $BaseFile)) {
    Write-Host "${Red}Error: Base strings.xml not found at ${BaseFile}${Reset}"
    exit 1
}

# Function to extract string names from XML
function Get-StringNames {
    param([string]$XmlPath, [bool]$FilterTranslatable = $false)
    
    if (-not (Test-Path $XmlPath)) { return @() }

    # Use .NET XML parser for reliability
    $xmlDoc = New-Object System.Xml.XmlDocument
    try {
        $xmlDoc.Load($XmlPath)
    }
    catch {
        Write-Host "${Red}Error parsing XML: $XmlPath${Reset}"
        return @()
    }

    $names = @()
    $nodes = $xmlDoc.SelectNodes('//string')
    foreach ($node in $nodes) {
        if ($node.HasAttribute('name')) {
            if ($FilterTranslatable) {
                if ($node.HasAttribute('translatable') -and $node.GetAttribute('translatable') -eq 'false') {
                    continue
                }
            }
            $names += $node.GetAttribute('name')
        }
    }
    return $names
}

# Extract base strings
Write-Host "  ${Cyan}[Scanning]${Reset} values/strings.xml (base)..."
$BaseStrings = Get-StringNames -XmlPath $BaseFile -FilterTranslatable $true
$BaseCount = $BaseStrings.Count
Write-Host "  ${Green}+${Reset} Found ${Bold}$BaseCount${Reset} translatable strings"
Write-Host ""

# Auto-detect locales
$LocaleDirs = Get-ChildItem -Path $ResDir -Directory | Where-Object { $_.Name -match '^values-(.+)$' -and $_.Name -ne 'values-night' }
$Locales = @()

foreach ($dir in $LocaleDirs) {
    if ($dir.Name -match '^values-(.+)$') {
        $Code = $Matches[1]
        $Locales += @{ Code = $Code; Name = $Code }
    }
}

if ($Locales.Count -eq 0) {
    Write-Host "${Yellow}No locale folders found (values-*).${Reset}"
    exit 0
}

# Process locales
$Results = @()

foreach ($locale in $Locales) {
    $Code = $locale.Code
    $LocaleFile = Join-Path $ResDir "values-$Code\strings.xml"
    
    if (-not (Test-Path $LocaleFile)) {
        Write-Host "  ${Yellow}[?]${Reset} values-$Code : strings.xml not found"
        continue
    }
    
    Write-Host "  ${Cyan}[Scanning]${Reset} values-$Code/strings.xml..."
    
    $LocaleStrings = Get-StringNames -XmlPath $LocaleFile -FilterTranslatable $false
    
    # Find missing strings
    $Missing = $BaseStrings | Where-Object { $_ -notin $LocaleStrings }
    $MissingCount = $Missing.Count
    $FoundCount = $LocaleStrings.Count
    
    # Calculate coverage
    if ($BaseCount -gt 0) {
        $RealFound = $BaseCount - $MissingCount
        $Coverage = [math]::Round(($RealFound / $BaseCount) * 100, 1)
    }
    else {
        $Coverage = 100
    }
    
    # Determine status color
    if ($Coverage -ge 95) {
        $Status = "${Green}[OK]${Reset}"
        $CovColor = $Green
    }
    elseif ($Coverage -ge 80) {
        $Status = "${Yellow}[!!]${Reset}"
        $CovColor = $Yellow
    }
    else {
        $Status = "${Red}[XX]${Reset}"
        $CovColor = $Red
    }
    
    Write-Host "  $Status ${Bold}$Code${Reset}"
    Write-Host "       Found: $FoundCount | Missing: ${Red}$MissingCount${Reset} | Coverage: ${CovColor}$Coverage%${Reset}"
    
    $Results += @{
        Code         = $Code
        Name         = $Code
        Found        = $FoundCount
        Missing      = $Missing
        MissingCount = $MissingCount
        Coverage     = $Coverage
    }
    
    Write-Host ""
}

# Generate report using Array
Write-Host "${Cyan}================================================================${Reset}"
Write-Host "            ${Bold}${Yellow}GENERATING REPORT${Reset}"
Write-Host "${Cyan}================================================================${Reset}"
Write-Host ""

$CodeBlock = '```'
# Ensure clean date format string
$DateFormat = 'yyyy-MM-dd HH:mm:ss'
$DateStr = Get-Date -Format $DateFormat
$ReportLines = @()
$ReportLines += '# Localization Status Report'
$ReportLines += ''
$ReportLines += "> Generated on: $DateStr"
$ReportLines += ''
$ReportLines += '## Summary'
$ReportLines += ''
$ReportLines += "**Base Language (English):** $BaseCount translatable strings"
$ReportLines += ''
$ReportLines += '| Locale | Code | Found | Missing | Coverage |'
$ReportLines += '|--------|------|-------|---------|----------|'

foreach ($result in $Results) {
    if ($result.Coverage -ge 95) { $StatusEmoji = '[OK]' }
    elseif ($result.Coverage -ge 80) { $StatusEmoji = '[!!]' }
    else { $StatusEmoji = '[XX]' }
    
    $Line = "| $($result.Name) | ``$($result.Code)`` | $($result.Found) | $($result.MissingCount) | $StatusEmoji $($result.Coverage)% |"
    $ReportLines += $Line
}

$ReportLines += ''
$ReportLines += '---'
$ReportLines += ''
$ReportLines += '## Missing Strings by Locale'

foreach ($result in $Results) {
    if ($result.MissingCount -eq 0) {
        $ReportLines += ''
        $ReportLines += "### [OK] $($result.Name) (``$($result.Code)``)"
        $ReportLines += ''
        $ReportLines += 'All strings are translated!'
    }
    else {
        $ReportLines += ''
        $ReportLines += "### [XX] $($result.Name) (``$($result.Code)``)"
        $ReportLines += ''
        $ReportLines += "**Missing $($result.MissingCount) strings:**"
        $ReportLines += ''
        $ReportLines += "${CodeBlock}text"
        foreach ($missing in $result.Missing | Sort-Object) {
            $ReportLines += "$missing"
        }
        $ReportLines += $CodeBlock
    }
}

$ReportLines += ''
$ReportLines += '---'
$ReportLines += ''
$ReportLines += '## How to Fix'
$ReportLines += ''
$ReportLines += '1. Open the corresponding locale file (e.g., ``values-de/strings.xml``)'
$ReportLines += '2. Add the missing ``<string>`` entries using the keys listed above.'
$ReportLines += '3. Re-run this checker to verify completeness.'
$ReportLines += ''
$ReportLines += '---'
$ReportLines += ''
$ReportLines += '*Generated by KonaBess Localization Checker*'

$ReportLines | Out-File -FilePath $ReportFile -Encoding UTF8

Write-Host "  ${Green}Report saved to: ${Bold}localization_report.md${Reset}"
Write-Host ""
Write-Host "  ${Dim}Tip: Use this report to copy-paste missing keys into localized files.${Reset}"
Write-Host ""
