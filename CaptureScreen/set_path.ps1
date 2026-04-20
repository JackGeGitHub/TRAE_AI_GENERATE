# 设置环境变量
$newPath = "D:\Workspace\Android\Tools\android_cli"
$env:PATH = $env:PATH + ";" + $newPath

# 验证设置
Write-Host "路径已添加: $newPath"
Write-Host "更新后的PATH:"
Write-Host $env:PATH
