
import os

# 从PATH环境变量中查找android.exe
def find_android_exe_in_path():
    # 获取PATH环境变量
    path_env = os.environ.get('PATH', '')
    print(f"PATH环境变量长度: {len(path_env)} 字符")
    
    # 分割PATH为目录列表
    path_dirs = path_env.split(os.pathsep)
    print(f"PATH环境变量包含 {len(path_dirs)} 个目录")
    
    # 遍历每个目录
    found_paths = []
    for i, directory in enumerate(path_dirs, 1):
        if directory:
            print(f"[{i}/{len(path_dirs)}] 检查目录: {directory}")
            android_exe_path = os.path.join(directory, 'android.exe')
            if os.path.exists(android_exe_path):
                print(f"  找到android.exe: {android_exe_path}")
                found_paths.append(android_exe_path)
            else:
                print(f"  未找到android.exe")
    
    # 输出结果
    if found_paths:
        print(f"\n总共找到 {len(found_paths)} 个android.exe文件:")
        for path in found_paths:
            print(f"  - {path}")
        return found_paths[0]  # 返回第一个找到的路径
    else:
        print("\n在PATH环境变量中未找到android.exe")
        return None

if __name__ == "__main__":
    find_android_exe_in_path()
