<# 
.SYNOPSIS
    KonaBess Localization Checker
.DESCRIPTION
    Professional tool for detecting missing string translations in Android projects.
    Auto-detects locales, scans all strings*.xml files, and respects translatable="false".
#>

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Paths
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$ResDir = Join-Path $ProjectRoot 'app\src\main\res'
$BaseValuesDir = Join-Path $ResDir 'values'
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
if (-not (Test-Path $BaseValuesDir)) {
    Write-Host "${Red}Error: Base values directory not found at ${BaseValuesDir}${Reset}"
    exit 1
}

$BaseFiles = Get-ChildItem -Path $BaseValuesDir -File -Filter 'strings*.xml' | Sort-Object Name
if ($BaseFiles.Count -eq 0) {
    Write-Host "${Red}Error: No base strings*.xml found in ${BaseValuesDir}${Reset}"
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

# Extract base strings from all base string files
$BaseStringsByFile = @{}
$BaseEntries = @()

Write-Host "  ${Cyan}[Scanning]${Reset} values/strings*.xml (base)..."
foreach ($baseFile in $BaseFiles) {
    $relative = "values/$($baseFile.Name)"
    $names = Get-StringNames -XmlPath $baseFile.FullName -FilterTranslatable $true
    $BaseStringsByFile[$baseFile.Name] = $names
    foreach ($name in $names) {
        $BaseEntries += [PSCustomObject]@{
            File = $baseFile.Name
            Name = $name
        }
    }
    Write-Host "  ${Green}+${Reset} ${relative}: ${Bold}$($names.Count)${Reset} translatable strings"
}

$BaseCount = $BaseEntries.Count

if ($BaseCount -eq 0) {
    Write-Host "${Yellow}No translatable strings found in base files.${Reset}"
    exit 0
}

Write-Host ""
Write-Host "  ${Green}+${Reset} Total base translatable strings: ${Bold}$BaseCount${Reset}"
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
    Write-Host "  ${Cyan}[Scanning]${Reset} values-$Code/strings*.xml..."

    $TranslatedCount = 0
    $Missing = @()

    foreach ($baseFile in $BaseFiles) {
        $baseFileName = $baseFile.Name
        $baseNames = $BaseStringsByFile[$baseFileName]
        $localeFile = Join-Path $ResDir "values-$Code\$baseFileName"
        $localeStrings = Get-StringNames -XmlPath $localeFile -FilterTranslatable $false

        foreach ($baseName in $baseNames) {
            if ($baseName -in $localeStrings) {
                $TranslatedCount++
            }
            else {
                $Missing += "${baseFileName}:$baseName"
            }
        }
    }

    $MissingCount = $Missing.Count
    $FoundCount = $TranslatedCount
    
    # Calculate coverage
    if ($BaseCount -gt 0) {
        $Coverage = [math]::Round(($FoundCount / $BaseCount) * 100, 1)
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
$ReportLines += "**Base Language (English):** $BaseCount translatable strings across $($BaseFiles.Count) files"
$ReportLines += ''
$ReportLines += '**Scanned base files:**'
foreach ($baseFile in $BaseFiles) {
    $ReportLines += "- ``values/$($baseFile.Name)``"
}
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
$ReportLines += '1. Run ``scripts/sync_localization.ps1`` to auto-add missing keys from English base.'
$ReportLines += '2. Translate the auto-filled English values in locale files.'
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
