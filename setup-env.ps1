# StreamVault — Environment Setup Script
# Run before starting services:  . .\setup-env.ps1

$ProjectRoot = $PSScriptRoot

# FFmpeg
$env:FFMPEG_PATH = Join-Path $ProjectRoot "ffmpeg-8.1.2-essentials_build\bin\ffmpeg.exe"

# Temp directory for encoding jobs
$env:TEMP_DIR = Join-Path $ProjectRoot "temp\encoding"

# Streaming service base URL (used for HLS proxy URLs)
$env:STREAMING_BASE_URL = "http://localhost:8084"

# AWS (set in .env file — copy from .env.example)
# $env:AWS_ACCESS_KEY = "your-access-key"
# $env:AWS_SECRET_KEY = "your-secret-key"
# $env:AWS_REGION = "ap-south-1"
# $env:AWS_BUCKET_NAME = "your-bucket-name"

# Load local .env file if it exists
$envFile = Join-Path $ProjectRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim()
            [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
    Write-Host "Loaded credentials from .env" -ForegroundColor Yellow
} else {
    Write-Warning ".env file not found. Copy .env.example to .env and add your AWS credentials."
}

# Create temp directory if it doesn't exist
if (-not (Test-Path $env:TEMP_DIR)) {
    New-Item -ItemType Directory -Path $env:TEMP_DIR -Force | Out-Null
    Write-Host "Created temp directory: $env:TEMP_DIR"
}

# Verify FFmpeg exists
if (-not (Test-Path $env:FFMPEG_PATH)) {
    Write-Warning "FFmpeg not found at: $env:FFMPEG_PATH"
    Write-Warning "Download FFmpeg and place it in: $ProjectRoot\ffmpeg-8.1.2-essentials_build\"
} else {
    Write-Host "FFmpeg found: $env:FFMPEG_PATH"
}

Write-Host ""
Write-Host "StreamVault environment configured:" -ForegroundColor Green
Write-Host "  FFMPEG_PATH        = $env:FFMPEG_PATH"
Write-Host "  TEMP_DIR           = $env:TEMP_DIR"
Write-Host "  STREAMING_BASE_URL = $env:STREAMING_BASE_URL"
Write-Host ""
Write-Host "Now start your services in this same terminal window." -ForegroundColor Cyan
