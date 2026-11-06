# 槲寄生 JUnit 离线运行脚本（javac + junit-platform-console-standalone）
param(
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'
$JdkHome = 'E:\JDK21'
$Javac   = Join-Path $JdkHome 'bin\javac.exe'
$Java    = Join-Path $JdkHome 'bin\java.exe'

$root    = $PSScriptRoot
$build   = Join-Path $root 'build-1.21.1'
$classes = Join-Path $build 'classes'
$testOut = Join-Path $build 'test-classes'
$junit   = Join-Path $root 'libs\junit-platform-console-standalone-1.11.4.jar'

if (-not (Test-Path $junit)) { throw "缺少 JUnit：$junit" }
if (-not (Test-Path $classes)) { throw "先运行 build-1.21.1.ps1" }
if ($Clean -and (Test-Path $testOut)) { Remove-Item $testOut -Recurse -Force }
New-Item -ItemType Directory -Force $testOut | Out-Null

$testSources = Get-ChildItem (Join-Path $root 'src\test\java') -Recurse -Filter '*.java' -File | ForEach-Object FullName
$cp = "$classes;$junit"
& $Javac --release 21 -encoding UTF-8 -proc:none -cp $cp -d $testOut $testSources
if ($LASTEXITCODE -ne 0) { throw "测试编译失败，退出码 $LASTEXITCODE" }

Push-Location $root
try {
    & $Java -jar $junit execute --class-path "$classes;$testOut" --scan-class-path "$testOut" --disable-banner --details=tree
    $code = $LASTEXITCODE
} finally { Pop-Location }
if ($code -ne 0) { throw "JUnit 失败，退出码 $code" }
