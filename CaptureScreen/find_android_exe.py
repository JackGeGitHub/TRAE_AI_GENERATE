
import os
import subprocess

# 从环境变量中查找android.exe
def find_android_exe():
    # 首先检查PATH环境变量
    path_env = os.environ.get('PATH', '')
    path_dirs = path_env.split(os.pathsep)
    
    print("正在搜索PATH环境变量中的android.exe...")
    for directory in path_dirs:
        if directory:
            android_exe_path = os.path.join(directory, 'android.exe')
            if os.path.exists(android_exe_path):
                print(f"找到android.exe: {android_exe_path}")
                return android_exe_path
    
    # 检查Android SDK相关环境变量
    android_home = os.environ.get('ANDROID_HOME')
    if android_home:
        print(f"检查ANDROID_HOME环境变量: {android_home}")
        tools_dir = os.path.join(android_home, 'tools')
        android_exe_path = os.path.join(tools_dir, 'android.exe')
        if os.path.exists(android_exe_path):
            print(f"找到android.exe: {android_exe_path}")
            return android_exe_path
        
        # 检查tools/bin目录
        tools_bin_dir = os.path.join(android_home, 'tools', 'bin')
        android_exe_path = os.path.join(tools_bin_dir, 'android.exe')
        if os.path.exists(android_exe_path):
            print(f"找到android.exe: {android_exe_path}")
            return android_exe_path
    
    # 尝试常见的Android SDK安装路径
    common_paths = [
        r"C:\Android\android-sdk\tools",
        r"C:\Program Files (x86)\Android\android-sdk\tools",
        r"D:\AndroidSdk\tools",
        r"C:\Users\{}\AppData\Local\Android\Sdk\tools".format(os.getenv('USERNAME'))
    ]
    
    print("检查常见的Android SDK安装路径...")
    for path in common_paths:
        android_exe_path = os.path.join(path, 'android.exe')
        if os.path.exists(android_exe_path):
            print(f"找到android.exe: {android_exe_path}")
            return android_exe_path
    
    # 没有找到
    print("未找到android.exe")
    return None

if __name__ == "__main__":
    find_android_exe()
