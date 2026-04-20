
import os

# 获取当前PATH
current_path = os.environ.get('PATH', '')
print("当前PATH长度:", len(current_path))

# 添加新路径
new_path = r"D:\Workspace\Android\Tools\android_cli"
updated_path = current_path + ";" + new_path

# 设置新的PATH
os.environ['PATH'] = updated_path
print("添加路径后:", new_path)

# 验证新的PATH
new_current_path = os.environ.get('PATH', '')
print("更新后PATH长度:", len(new_current_path))
print("是否包含新路径:", new_path in new_current_path)

# 输出完整的PATH
print("\n完整的PATH环境变量:")
print(new_current_path)
