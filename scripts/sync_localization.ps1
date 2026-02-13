<# 
.SYNOPSIS
    KonaBess Localization Sync Tool
.DESCRIPTION
    Auto-generates or syncs locale string files from English base files (values/strings*.xml).
    - Creates missing locale folders/files
    - Adds missing <string> keys using English values
    - Preserves existing translations
#>

param(
    [string[]]$Locales,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Paths
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$ResDir = Join-Path $ProjectRoot 'app\src\main\res'
$BaseValuesDir = Join-Path $ResDir 'values'

# Colors
$Green = "`e[92m"
$Red = "`e[91m"
$Yellow = "`e[93m"
$Cyan = "`e[96m"
$Bold = "`e[1m"
$Dim = "`e[2m"
$Reset = "`e[0m"

Write-Host ""
Write-Host "${Cyan}+==============================================================+${Reset}"
Write-Host "${Cyan}|${Reset}         ${Bold}${Yellow}KONABESS LOCALIZATION SYNC TOOL${Reset}                      ${Cyan}|${Reset}"
Write-Host "${Cyan}+==============================================================+${Reset}"
Write-Host ""

if (-not (Test-Path $BaseValuesDir)) {
    Write-Host "${Red}Error: Base values directory not found at ${BaseValuesDir}${Reset}"
    exit 1
}

$BaseFiles = Get-ChildItem -Path $BaseValuesDir -File -Filter 'strings*.xml' | Sort-Object Name
if ($BaseFiles.Count -eq 0) {
    Write-Host "${Red}Error: No base strings*.xml found in ${BaseValuesDir}${Reset}"
    exit 1
}

function Load-XmlOrCreate {
    param([string]$Path)

    $xmlDoc = New-Object System.Xml.XmlDocument
    if (Test-Path $Path) {
        $xmlDoc.Load($Path)
    }
    else {
        $declaration = $xmlDoc.CreateXmlDeclaration('1.0', 'utf-8', $null)
        $null = $xmlDoc.AppendChild($declaration)
        $resources = $xmlDoc.CreateElement('resources')
        $null = $xmlDoc.AppendChild($resources)
    }
    return $xmlDoc
}

function Save-XmlIndented {
    param(
        [System.Xml.XmlDocument]$XmlDoc,
        [string]$Path
    )

    $settings = New-Object System.Xml.XmlWriterSettings
    $settings.Indent = $true
    $settings.IndentChars = '    '
    $settings.NewLineChars = "`r`n"
    $settings.NewLineHandling = [System.Xml.NewLineHandling]::Replace
    $settings.Encoding = New-Object System.Text.UTF8Encoding($false)

    $writer = [System.Xml.XmlWriter]::Create($Path, $settings)
    try {
        $XmlDoc.Save($writer)
    }
    finally {
        $writer.Dispose()
    }
}

function Get-TargetLocales {
    param([string[]]$RequestedLocales)

    if ($RequestedLocales -and $RequestedLocales.Count -gt 0) {
        return $RequestedLocales | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' } | Sort-Object -Unique
    }

    $existing = Get-ChildItem -Path $ResDir -Directory |
        Where-Object { $_.Name -match '^values-(.+)$' -and $_.Name -ne 'values-night' } |
        ForEach-Object { ($_.Name -replace '^values-', '') } |
        Sort-Object -Unique

    return $existing
}

$TargetLocales = Get-TargetLocales -RequestedLocales $Locales

if ($TargetLocales.Count -eq 0) {
    Write-Host "${Yellow}No target locales found.${Reset}"
    Write-Host "${Dim}Tip: pass explicit locales, e.g. -Locales de,in,zh-rCN${Reset}"
    exit 0
}

Write-Host "${Cyan}Base files:${Reset}"
foreach ($baseFile in $BaseFiles) {
    Write-Host "  - values/$($baseFile.Name)"
}
Write-Host ""

Write-Host "${Cyan}Target locales:${Reset} $($TargetLocales -join ', ')"
if ($DryRun) {
    Write-Host "${Yellow}Dry-run mode: no files will be written.${Reset}"
}
Write-Host ""

$summary = @()

foreach ($locale in $TargetLocales) {
    $localeDir = Join-Path $ResDir "values-$locale"
    $createdDir = $false

    if (-not (Test-Path $localeDir)) {
        if (-not $DryRun) {
            $null = New-Item -ItemType Directory -Path $localeDir -Force
        }
        $createdDir = $true
    }

    Write-Host "${Cyan}[Locale]${Reset} values-$locale"

    $localeAddedTotal = 0
    $filesUpdated = 0

    foreach ($baseFile in $BaseFiles) {
        $basePath = $baseFile.FullName
        $localePath = Join-Path $localeDir $baseFile.Name

        $baseXml = New-Object System.Xml.XmlDocument
        $baseXml.Load($basePath)
        $baseNodes = $baseXml.SelectNodes('//string')

        $localeXml = Load-XmlOrCreate -Path $localePath
        $resourcesNode = $localeXml.SelectSingleNode('/resources')
        if (-not $resourcesNode) {
            $resourcesNode = $localeXml.CreateElement('resources')
            $null = $localeXml.AppendChild($resourcesNode)
        }

        $existing = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        $localeNodes = $localeXml.SelectNodes('//string')
        foreach ($node in $localeNodes) {
            if ($node.Attributes -and $node.Attributes['name']) {
                $null = $existing.Add($node.Attributes['name'].Value)
            }
        }

        $addedForFile = 0
        foreach ($baseNode in $baseNodes) {
            if (-not ($baseNode.Attributes -and $baseNode.Attributes['name'])) {
                continue
            }

            $nameAttr = $baseNode.Attributes['name'].Value

            if ($baseNode.Attributes['translatable'] -and $baseNode.Attributes['translatable'].Value -eq 'false') {
                continue
            }

            if ($existing.Contains($nameAttr)) {
                continue
            }

            $newNode = $localeXml.CreateElement('string')
            $newNode.SetAttribute('name', $nameAttr)

            foreach ($attr in $baseNode.Attributes) {
                if ($attr.Name -eq 'name') { continue }
                if ($attr.Name -eq 'translatable' -and $attr.Value -eq 'false') { continue }
                $newNode.SetAttribute($attr.Name, $attr.Value)
            }

            $newNode.InnerText = $baseNode.InnerText
            $null = $resourcesNode.AppendChild($newNode)
            $null = $existing.Add($nameAttr)
            $addedForFile++
        }

        if ($addedForFile -gt 0) {
            $filesUpdated++
            $localeAddedTotal += $addedForFile
            Write-Host "  ${Green}+${Reset} $($baseFile.Name): added ${Bold}$addedForFile${Reset} key(s)"

            if (-not $DryRun) {
                Save-XmlIndented -XmlDoc $localeXml -Path $localePath
            }
        }
        else {
            Write-Host "  ${Dim}=${Reset} $($baseFile.Name): no missing keys"
        }
    }

    $summary += [PSCustomObject]@{
        Locale       = $locale
        CreatedDir   = $createdDir
        FilesUpdated = $filesUpdated
        KeysAdded    = $localeAddedTotal
    }

    Write-Host ""
}

Write-Host "${Cyan}================================================================${Reset}"
Write-Host "${Bold}${Yellow}SYNC SUMMARY${Reset}"
Write-Host "${Cyan}================================================================${Reset}"

foreach ($item in $summary) {
    $createdTag = if ($item.CreatedDir) { ' [new locale]' } else { '' }
    Write-Host "- values-$($item.Locale)$createdTag : updated files=$($item.FilesUpdated), added keys=$($item.KeysAdded)"
}

Write-Host ""
if ($DryRun) {
    Write-Host "${Yellow}Dry-run complete. No files were modified.${Reset}"
}
else {
    Write-Host "${Green}Sync complete.${Reset}"
    Write-Host "${Dim}Tip: Re-run scripts/check_localization.ps1 to verify 100% coverage.${Reset}"
}
