# NABOO-SITE BACKEND - DEPLOY TO CONTABO
#
#   .\deploy.ps1                 # bump patch, build, push, deploy
#   .\deploy.ps1 -Bump minor     # or: major
#   .\deploy.ps1 -Bump none      # deploy the version in .backend-version as is (first deploy)
#   .\deploy.ps1 -BuildOnly      # bump patch, build, push - and STOP. The server is never
#                                # contacted. Test that image on staging, then deploy the
#                                # SAME image with -DeployOnly <version>.
#   .\deploy.ps1 -DeployOnly 1.0.0
#                                # NO build/push: deploy an image already on Docker Hub
#
#   Weka faili hii + remote-deploy.sh kwenye ROOT ya repo ya naboo-site backend.
#
# Flow:
#   PC:     bump version -> mvnw clean package -> docker build -> docker push (exact tag)
#   Server: pull -> write BACKEND_VERSION -> compose up backend -> health check -> rollback
#   PC:     .backend-version is written + committed ONLY after DEPLOY_OK.
param(
    [ValidateSet('patch', 'minor', 'major', 'none')]
    [string]$Bump = 'patch',
    [switch]$BuildOnly,
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$DeployOnly
)

$ErrorActionPreference = 'Stop'

# -- CONFIG ---------------------------------------------------
$ImageName    = 'geofrey2025/naboo-site-backend'
$VersionFile  = '.backend-version'
$SshHost      = 'root@164.68.122.5'
$RemoteScript = '/tmp/remote-deploy-naboo-backend.sh'

function Step([string]$m) { Write-Host "[*] $m" -ForegroundColor Cyan }
function Fail([string]$m) { Write-Host "`n[FAILED] $m" -ForegroundColor Red; exit 1 }

function Invoke-Checked([string]$What, [scriptblock]$Cmd) {
    & $Cmd
    if ($LASTEXITCODE -ne 0) { Fail "$What (exit $LASTEXITCODE)" }
}

function Test-ImageOnHub([string]$Image) {
    $ErrorActionPreference = 'Continue'
    docker manifest inspect $Image 2>&1 | Out-Null
    return ($LASTEXITCODE -eq 0)
}

function Get-ImageDigest([string]$Image) {
    $meta = (docker image inspect $Image --format '{{json .}}') -join '' | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0 -or -not $meta) { return '' }
    return ((@($meta.RepoDigests) | Select-Object -First 1) -replace '^.*@', '')
}

function Assert-CleanTree([string]$Root) {
    $ErrorActionPreference = 'Continue'
    git -C $Root update-index -q --refresh | Out-Null
    $lines = @(git -C $Root -c core.quotepath=false status --porcelain=v1 --untracked-files=all --ignore-submodules=none)
    if ($LASTEXITCODE -ne 0) { Fail "git status imeshindwa kwenye $Root (exit $LASTEXITCODE)." }
    if ($lines.Count) {
        Fail ("Working tree si safi ($($lines.Count)) - commit au ondoa kwanza:`n    " + ($lines -join "`n    "))
    }
}

$RepoRoot = $PSScriptRoot
Push-Location $RepoRoot
$tempScript = $null
try {
    if ($BuildOnly -and $DeployOnly) { Fail "Tumia -BuildOnly AU -DeployOnly, si vyote viwili." }
    if ($DeployOnly -and $PSBoundParameters.ContainsKey('Bump')) { Fail "Tumia -DeployOnly AU -Bump, si vyote viwili." }

    # -- CHECKS -------------------------------------------------
    if (-not (Test-Path $VersionFile)) { Fail "$VersionFile haipo (inatakiwa iwe na semver, mfano 1.0.0)." }
    $current = (Get-Content $VersionFile -Raw).Trim()
    if ($current -notmatch '^(\d+)\.(\d+)\.(\d+)$') { Fail "$VersionFile si semver: '$current'" }
    $major = [int]$Matches[1]; $minor = [int]$Matches[2]; $patch = [int]$Matches[3]
    switch ($Bump) {
        'major' { $major++; $minor = 0; $patch = 0 }
        'minor' { $minor++; $patch = 0 }
        'patch' { $patch++ }
        'none'  { }
    }
    $newVersion = "$major.$minor.$patch"
    if ($DeployOnly) { $newVersion = $DeployOnly }
    $image = "${ImageName}:${newVersion}"

    Assert-CleanTree $RepoRoot

    Invoke-Checked "Docker haipatikani - washa Docker Desktop" { docker version --format '{{.Server.Version}}' | Out-Null }
    $dockerOs = (docker info --format '{{.OSType}}' 2>$null)
    if ($dockerOs -eq 'windows') { Fail "Docker iko Windows-container mode - switch to Linux containers." }

    if ($BuildOnly) {
        if (Test-ImageOnHub $image) { Fail "$image tayari iko Docker Hub - haitaandikwa upya. Tumia -DeployOnly $newVersion kuideploy, au -Bump kwa toleo jipya." }
        Step "-BuildOnly: build + push ya $image. Server HAIGUSWI; $VersionFile haibadilishwi."
    } else {
        Invoke-Checked "SSH kwenda '$SshHost' imeshindwa (jaribu: ssh $SshHost)" {
            ssh -o ConnectTimeout=15 $SshHost "true"
        }
    }

    Step "Toleo: $current -> $newVersion   ($image)"

    if ($DeployOnly) {
        if (-not (Test-ImageOnHub $image)) { Fail "$image haipo Docker Hub (au hujaingia: docker login -u geofrey2025). Server haijaguswa." }
        Invoke-Checked "docker pull $image imeshindwa. Server haijaguswa." { docker pull -q $image | Out-Null }
        Step "-DeployOnly: build na push vimerukwa. Image: $(Get-ImageDigest $image)"
    } else {
        # -- BUILD ------------------------------------------------
        Step "mvnw clean package -DskipTests ..."
        Invoke-Checked "Maven build imeshindwa" { .\mvnw.cmd clean package -DskipTests }

        Step "docker build..."
        $revision = (git -C $RepoRoot rev-parse HEAD).Trim()
        Invoke-Checked "docker build imeshindwa" {
            docker build --build-arg "APP_VERSION=$newVersion" --label "org.opencontainers.image.revision=$revision" -t $image .
        }

        Invoke-Checked "java -version ndani ya image imeshindwa" { docker run --rm --entrypoint java $image -version }

        Step "docker push $image ..."
        $pushed = $false
        foreach ($wait in 0, 5, 15) {
            if ($wait) { Write-Host "    push imeshindwa - najaribu tena baada ya ${wait}s" -ForegroundColor DarkYellow; Start-Sleep -Seconds $wait }
            docker push $image
            if ($LASTEXITCODE -eq 0) { $pushed = $true; break }
        }
        if (-not $pushed) { Fail "docker push imeshindwa. Umeingia Docker Hub? (docker login -u geofrey2025). Server haijaguswa." }
    }

    if ($BuildOnly) {
        Assert-CleanTree $RepoRoot
        Write-Host ""
        Write-Host "[OK] BUILD_OK $newVersion - imejengwa na kusukumwa. Server haijaguswa; $VersionFile bado ni $current." -ForegroundColor Green
        Write-Host "  Image:    $image"
        Write-Host "  Digest:   $(Get-ImageDigest $image)"
        Write-Host "  Commit:   $(git log -1 --format='%h %s')"
        Write-Host "  Kifuatacho: ijaribu staging, kisha"
        Write-Host "      .\deploy.ps1 -DeployOnly $newVersion"
        return
    }

    # -- REMOTE DEPLOY ------------------------------------------
    Assert-CleanTree $RepoRoot

    $body = [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'remote-deploy.sh')) -replace "`r", ''
    $tempScript = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllText($tempScript, $body, [System.Text.UTF8Encoding]::new($false))

    Step "Deploying kwenye $SshHost ..."
    Invoke-Checked "scp ya script imeshindwa. Server haijaguswa." { scp -q $tempScript "${SshHost}:${RemoteScript}" }

    $out = @()
    ssh $SshHost "bash $RemoteScript $newVersion; rc=`$?; rm -f $RemoteScript; exit `$rc" |
        ForEach-Object { Write-Host "    $_"; $out += $_ }
    $sshExit = $LASTEXITCODE

    if ($sshExit -ne 0 -or ($out -notcontains "DEPLOY_OK $newVersion")) {
        Fail "Deploy ya $newVersion imeshindwa (ssh exit $sshExit). Soma mistari ya [naboo] hapo juu - rollback imefanyika yenyewe kama kulikuwa na toleo la awali. $VersionFile haijabadilishwa."
    }

    # -- SUCCESS -------------------------------------------------
    [System.IO.File]::WriteAllText((Join-Path $RepoRoot $VersionFile), "$newVersion`n", [System.Text.ASCIIEncoding]::new())
    git add -- $VersionFile
    git diff --cached --quiet -- $VersionFile
    if ($LASTEXITCODE -ne 0) {
        git commit --quiet --only -m "deploy: backend $newVersion" -- $VersionFile
        if ($LASTEXITCODE -ne 0) { Write-Host "[!] Deploy imefanikiwa lakini git commit ya $VersionFile imeshindwa - commit kwa mkono." -ForegroundColor DarkYellow }
        else { Step "$VersionFile = $newVersion (committed)" }
    }

    docker images $ImageName --format '{{.Tag}}' |
        Where-Object { $_ -ne $newVersion -and $_ -ne $current -and $_ -ne '<none>' } |
        ForEach-Object { docker rmi "${ImageName}:$_" | Out-Null }

    Write-Host "`n[OK] DEPLOY_OK $newVersion" -ForegroundColor Green
}
finally {
    if ($tempScript -and (Test-Path $tempScript)) { Remove-Item $tempScript -Force }
    Pop-Location
}
