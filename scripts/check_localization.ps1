<# 
.SYNOPSIS
    KonaBess Localization Checker
.DESCRIPTION
    Professional tool for detecting missing string translations in Android projects.
#>

$ErrorActionPreference = "Stop"
$OutputEncoding = [System.Text.Encoding]::UTF8

# Paths
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$ResDir = Join-Path $ProjectRoot "app\src\main\res"
$ReportFile = Join-Path $ProjectRoot "localization_report.md"

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
$BaseFile = Join-Path $ResDir "values\strings.xml"
if (-not (Test-Path $BaseFile)) {
    Write-Host "${Red}Error: Base strings.xml not found at ${BaseFile}${Reset}"
    exit 1
}

# Function to extract string names from XML
function Get-StringNames {
    param([string]$XmlPath)
    
    [xml]$xml = Get-Content $XmlPath -Encoding UTF8
    $names = @()
    foreach ($string in $xml.resources.string) {
        if ($string.name) {
            $names += $string.name
        }
    }
    return $names
}

# Extract base strings
Write-Host "  ${Cyan}[Scanning]${Reset} values/strings.xml (base)..."
$BaseStrings = Get-StringNames -XmlPath $BaseFile
$BaseCount = $BaseStrings.Count
Write-Host "  ${Green}+${Reset} Found ${Bold}$BaseCount${Reset} strings"
Write-Host ""

# Locale configuration
$Locales = @(
    @{ Code = "de"; Name = "German" },
    @{ Code = "in"; Name = "Indonesian" },
    @{ Code = "zh-rCN"; Name = "Chinese Simplified" }
)

# Process locales
$Results = @()

foreach ($locale in $Locales) {
    $LocaleFile = Join-Path $ResDir "values-$($locale.Code)\strings.xml"
    
    if (-not (Test-Path $LocaleFile)) {
        Write-Host "  ${Red}[X]${Reset} $($locale.Name): File not found"
        continue
    }
    
    Write-Host "  ${Cyan}[Scanning]${Reset} values-$($locale.Code)/strings.xml..."
    
    $LocaleStrings = Get-StringNames -XmlPath $LocaleFile
    $FoundCount = $LocaleStrings.Count
    
    # Find missing strings
    $Missing = $BaseStrings | Where-Object { $_ -notin $LocaleStrings }
    $MissingCount = $Missing.Count
    
    # Calculate coverage
    $Coverage = [math]::Round((($BaseCount - $MissingCount) / $BaseCount) * 100, 1)
    
    # Determine status color
    if ($Coverage -ge 90) {
        $Status = "${Green}[OK]${Reset}"
        $CovColor = $Green
    } elseif ($Coverage -ge 70) {
        $Status = "${Yellow}[!!]${Reset}"
        $CovColor = $Yellow
    } else {
        $Status = "${Red}[XX]${Reset}"
        $CovColor = $Red
    }
    
    Write-Host "  $Status ${Bold}$($locale.Name)${Reset}"
    Write-Host "       Found: $FoundCount | Missing: ${Red}$MissingCount${Reset} | Coverage: ${CovColor}$Coverage%${Reset}"
    
    if ($MissingCount -gt 0) {
        $ShowCount = [math]::Min(5, $MissingCount)
        for ($i = 0; $i -lt $ShowCount; $i++) {
            Write-Host "       ${Dim}-${Reset} ${Cyan}$($Missing[$i])${Reset}"
        }
        if ($MissingCount -gt 5) {
            $Remaining = $MissingCount - 5
            Write-Host "       ${Dim}  ... and $Remaining more${Reset}"
        }
    }
    
    Write-Host ""
    
    $Results += @{
        Code = $locale.Code
        Name = $locale.Name
        Found = $FoundCount
        Missing = $Missing
        MissingCount = $MissingCount
        Coverage = $Coverage
    }
}

# Generate report
Write-Host "${Cyan}================================================================${Reset}"
Write-Host "            ${Bold}${Yellow}GENERATING REPORT${Reset}"
Write-Host "${Cyan}================================================================${Reset}"
Write-Host ""

$Report = @"
# 🌍 Localization Status Report

> Generated on: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")

## 📊 Summary

**Base Language (English):** $BaseCount strings

| Locale | Language | Found | Missing | Coverage |
|--------|----------|-------|---------|----------|
"@

foreach ($result in $Results) {
    if ($result.Coverage -ge 90) { $StatusEmoji = "✅" }
    elseif ($result.Coverage -ge 70) { $StatusEmoji = "⚠️" }
    else { $StatusEmoji = "❌" }
    
    $Report += "`n| ``$($result.Code)`` | $($result.Name) | $($result.Found) | $($result.MissingCount) | $StatusEmoji $($result.Coverage)% |"
}

$Report += @"

---

## 📝 Missing Strings by Locale

"@

foreach ($result in $Results) {
    if ($result.MissingCount -eq 0) {
        $Report += @"

### ✅ $($result.Name) (``$($result.Code)``)

All strings are translated! 🎉
"@
    } else {
        $Report += @"

### ❌ $($result.Name) (``$($result.Code)``)

**Missing $($result.MissingCount) strings:**

"@
        foreach ($missing in $result.Missing | Sort-Object) {
            $Report += "- ``$missing```n"
        }
    }
}

$Report += @"

---

## 🔧 How to Fix

1. Open the corresponding locale file (e.g., ``values-de/strings.xml``)
2. Add the missing ``<string>`` entries with translated values
3. Re-run this checker to verify completeness

---

*Generated by KonaBess Localization Checker*
"@

$Report | Out-File -FilePath $ReportFile -Encoding UTF8

Write-Host "  ${Green}📄${Reset} Report saved to: ${Bold}localization_report.md${Reset}"
Write-Host ""

# Summary
$TotalCoverage = 0
foreach ($r in $Results) { $TotalCoverage += $r.Coverage }
$AvgCoverage = if ($Results.Count -gt 0) { $TotalCoverage / $Results.Count } else { 0 }

if ($AvgCoverage -ge 90) {
    Write-Host "  ${Green}[OK] Great job! Average coverage: $([math]::Round($AvgCoverage, 1))%${Reset}"
} elseif ($AvgCoverage -ge 70) {
    Write-Host "  ${Yellow}[!!] Some work needed. Average coverage: $([math]::Round($AvgCoverage, 1))%${Reset}"
} else {
    Write-Host "  ${Red}[XX] Significant translations missing. Average coverage: $([math]::Round($AvgCoverage, 1))%${Reset}"
}
Write-Host ""

