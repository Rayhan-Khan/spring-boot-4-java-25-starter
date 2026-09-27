<#
.SYNOPSIS
    Renames this base project for a new application.

.DESCRIPTION
    Changes the Gradle project name and group, the Java package (moving the source folders), the main
    application class, spring.application.name, the Docker image/container names and the API title.

    Run it once on a fresh copy of the base project:

        powershell -ExecutionPolicy Bypass -File scripts/rename-project.ps1 -Name crm-backend -Package com.acme.crm

    The current values are read from the project itself, so it also works on a copy that was renamed before.
    Files under docs/ are left unchanged. Written for Windows PowerShell 5.1 and PowerShell 7 (pwsh).

.PARAMETER Name
    New project name in kebab-case, e.g. crm-backend.

.PARAMETER Package
    New base Java package, e.g. com.acme.crm. The Gradle group becomes the package without its last segment (com.acme).

.PARAMETER AppName
    Prefix of the main class, e.g. Crm for CrmApplication. Default: first word of -Name, capitalized.

.PARAMETER Title
    Human-readable name for the README and Swagger title, e.g. "CRM Backend". Default: words of -Name, capitalized.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$Package,
    [string]$AppName,
    [string]$Title
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function ConvertTo-Capitalized([string]$Word) {
    if ($Word.Length -eq 0) { return $Word }
    return $Word.Substring(0, 1).ToUpper() + $Word.Substring(1)
}

function Read-Text([string]$Path) { return [System.IO.File]::ReadAllText($Path) }

function Write-Text([string]$Path, [string]$Text) {
    # UTF-8 without BOM: javac rejects a BOM, and Windows PowerShell 5.1 would add one by default
    [System.IO.File]::WriteAllText($Path, $Text, (New-Object System.Text.UTF8Encoding $false))
}

# --- Validate input --------------------------------------------------------------------------------
if ($Name -cnotmatch '^[a-z][a-z0-9]*(-[a-z0-9]+)*$') { throw "-Name must be kebab-case, for example crm-backend." }
if ($Package -cnotmatch '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$') {
    throw "-Package must be lowercase with at least two segments, for example com.acme.crm."
}
$words = $Name -split '-'
if (-not $AppName) { $AppName = ConvertTo-Capitalized $words[0] }
if ($AppName -cnotmatch '^[A-Z][A-Za-z0-9]*$') { throw "-AppName must start with an uppercase letter, for example Crm." }
if (-not $Title) { $Title = ($words | ForEach-Object { ConvertTo-Capitalized $_ }) -join ' ' }

$root = Split-Path -Parent $PSScriptRoot
$mainJava = Join-Path $root 'src/main/java'

# --- Detect current values -------------------------------------------------------------------------
$oldName = [regex]::Match((Read-Text (Join-Path $root 'settings.gradle')), "rootProject\.name\s*=\s*'([^']+)'").Groups[1].Value
$mainFile = Get-ChildItem $mainJava -Recurse -File -Filter '*.java' |
        Where-Object { (Read-Text $_.FullName) -match '@SpringBootApplication' } |
        Select-Object -First 1
if (-not $oldName -or -not $mainFile) { throw "Could not find rootProject.name in settings.gradle or the @SpringBootApplication class." }

$oldPackage = [regex]::Match((Read-Text $mainFile.FullName), '(?m)^package\s+([\w.]+);').Groups[1].Value
$oldMainClass = [System.IO.Path]::GetFileNameWithoutExtension($mainFile.Name)
$newMainClass = "${AppName}Application"
$oldGroup = $oldPackage.Substring(0, $oldPackage.LastIndexOf('.'))
$newGroup = $Package.Substring(0, $Package.LastIndexOf('.'))

$oldTitle = $null
$openApi = Get-ChildItem $mainJava -Recurse -File -Filter 'OpenApiConfig.java' | Select-Object -First 1
if ($openApi) {
    $match = [regex]::Match((Read-Text $openApi.FullName), '\.title\("(.+?) Service API"\)')
    if ($match.Success) { $oldTitle = $match.Groups[1].Value }
}

Write-Host "Renaming project:"
Write-Host "  name        $oldName -> $Name"
Write-Host "  package     $oldPackage -> $Package"
Write-Host "  group       $oldGroup -> $newGroup"
Write-Host "  main class  $oldMainClass -> $newMainClass"
if ($oldTitle) { Write-Host "  title       $oldTitle -> $Title" }

# --- Move source folders to the new package --------------------------------------------------------
foreach ($sourceRoot in @('src/main/java', 'src/test/java')) {
    $base = Join-Path $root $sourceRoot
    $oldDir = Join-Path $base ($oldPackage.Replace('.', '/'))
    $newDir = Join-Path $base ($Package.Replace('.', '/'))
    if (-not (Test-Path $oldDir) -or ((Resolve-Path $oldDir).Path -eq [System.IO.Path]::GetFullPath($newDir))) { continue }

    # Move through a temporary folder so the new package may sit inside or above the old one
    $temp = Join-Path $base ('.rename-' + [guid]::NewGuid().ToString('N'))
    Move-Item $oldDir $temp
    $parent = Split-Path $oldDir -Parent
    while ((Test-Path $parent) -and ([System.IO.Path]::GetFullPath($parent) -ne [System.IO.Path]::GetFullPath($base)) -and
            -not (Get-ChildItem $parent -Force)) {
        Remove-Item $parent
        $parent = Split-Path $parent -Parent
    }
    New-Item -ItemType Directory -Force (Split-Path $newDir -Parent) | Out-Null
    Move-Item $temp $newDir
}

$movedMain = Join-Path (Join-Path $mainJava ($Package.Replace('.', '/'))) "$oldMainClass.java"
if ($oldMainClass -ne $newMainClass -and (Test-Path $movedMain)) {
    Rename-Item $movedMain "$newMainClass.java"
}

# --- Replace names in text files -------------------------------------------------------------------
$replacements = @(
    @{ Pattern = '(?<![\w.])' + [regex]::Escape($oldPackage) + '(?!\w)'; Value = $Package },
    @{ Pattern = '\b' + [regex]::Escape($oldMainClass) + '\b'; Value = $newMainClass },
    @{ Pattern = "(?m)^group\s*=\s*'" + [regex]::Escape($oldGroup) + "'"; Value = "group = '$newGroup'" },
    @{ Pattern = '(?<![\w-])' + [regex]::Escape($oldName) + '(?![\w-])'; Value = $Name },
    @{ Pattern = '(?<!\w)' + [regex]::Escape($oldName.Replace('-', '_')) + '(?=_|\b)'; Value = $Name.Replace('-', '_') }
)
if ($oldTitle) { $replacements += @{ Pattern = [regex]::Escape($oldTitle); Value = $Title } }

$excludedDirs = @('.git', '.gradle', 'build', 'docs', 'gradle', 'node_modules', '.idea', '.vscode', 'scripts')
$textExtensions = @('.java', '.gradle', '.yml', '.yaml', '.properties', '.md', '.sh', '.xml', '.txt')
$textFileNames = @('Dockerfile', '.env.example')

$files = Get-ChildItem $root -Force |
        Where-Object { -not ($_.PSIsContainer -and $excludedDirs -contains $_.Name) } |
        ForEach-Object { if ($_.PSIsContainer) { Get-ChildItem $_.FullName -Recurse -File -Force } else { $_ } } |
        Where-Object { ($textExtensions -contains $_.Extension) -or ($textFileNames -contains $_.Name) }

$changed = 0
foreach ($file in $files) {
    $original = Read-Text $file.FullName
    $text = $original
    foreach ($replacement in $replacements) {
        $text = [regex]::Replace($text, $replacement.Pattern, $replacement.Value.Replace('$', '$$'))
    }
    if ($text -cne $original) {
        Write-Text $file.FullName $text
        $changed++
    }
}

# Old build output still contains classes in the old package, and IntelliJ's .idea folder still refers
# to the old project name. Both are regenerated (build on the next build, .idea when the IDE opens the project).
foreach ($generated in @('build', '.idea')) {
    $path = Join-Path $root $generated
    if (Test-Path $path) { Remove-Item $path -Recurse -Force }
}

Write-Host "Updated $changed files."
Write-Host "Next: review .env, then run 'gradlew build' to check that everything compiles and the tests pass."
