@echo off

rem 设置新的PATH环境变量
echo 当前PATH: %PATH%
echo.
echo 添加路径: D:\Workspace\Android\Tools\android_cli
echo.

set "PATH=%PATH%;D:\Workspace\Android\Tools\android_cli"

echo 更新后PATH: %PATH%
echo.
echo 路径添加完成！
