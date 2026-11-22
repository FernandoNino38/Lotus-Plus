@echo off
setlocal
set "DOTNET_ROOT=%USERPROFILE%\.dotnet"
set "PATH=%DOTNET_ROOT%;%PATH%"

if exist "%~dp0windows\publish\Lotus.exe" (
    start "" "%~dp0windows\publish\Lotus.exe"
) else (
    dotnet run --project "%~dp0windows\Lotus.Windows\Lotus.Windows.csproj" -c Release
)
