
import os

# 验证环境变量
path = os.environ.get('PATH', '')
print("PATH环境变量长度:", len(path))

# 检查特定路径是否在环境变量中
target_path = r"D:\Workspace\Android\Tools\android_cli"
if target_path in path:
    print(f"路径 '{target_path}' 已成功添加到PATH环境变量")
else:
    print(f"路径 '{target_path}' 未找到在PATH环境变量中")

# 输出完整的PATH环境变量
print("\n完整的PATH环境变量:")
print(path)
