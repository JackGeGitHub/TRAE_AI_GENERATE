
import os

# 目标目录
target_dir = r"D:\Workspace\AndroidSdk\sources\android-35\android\os"

# 统计Java文件数量
java_file_count = 0

# 遍历目录
for root, dirs, files in os.walk(target_dir):
    for file in files:
        if file.endswith('.java'):
            java_file_count += 1

# 输出结果
print(f"目录 {target_dir} 中共有 {java_file_count} 个Java文件")
