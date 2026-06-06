$url = "https://github.com/alphacep/vosk-models/releases/download/v0.22/vosk-model-small-cn-0.22.zip"
$outputPath = "vosk-model-small-cn-0.22.zip"
$extractPath = "public"

Write-Host "正在下载 Vosk WebAssembly 中文模型..."
Write-Host "URL: $url"

try {
    $webClient = New-Object System.Net.WebClient
    $webClient.DownloadFile($url, $outputPath)
    Write-Host "下载完成！"

    Write-Host "正在解压模型文件..."
    Expand-Archive -Path $outputPath -DestinationPath $extractPath -Force
    Write-Host "解压完成！"

    Write-Host "正在清理临时文件..."
    Remove-Item -Path $outputPath -Force
    Write-Host "临时文件已删除"

    Write-Host "模型安装完成！"
    Write-Host "模型路径: $extractPath/vosk-model-small-cn-0.22"
} catch {
    Write-Host "下载或解压失败: $_"
    exit 1
}