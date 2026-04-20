
import openpyxl

# 打开Excel文件
file_path = r"d:\Workspace\Android\Codes\TRAE_AI_GENERATE\CaptureScreen\doc\CaptureScreenCodes.xlsx"
wb = openpyxl.load_workbook(file_path)
ws = wb.active

# 统计Java文件数量
java_file_count = 0

# 从第二行开始遍历（第一行是表头）
for row in ws.iter_rows(min_row=2, values_only=True):
    file_type = row[3]  # 第四列是文件类型
    if file_type == "Java源文件":
        java_file_count += 1

# 输出结果
print(f"Excel文件中共有 {java_file_count} 个Java文件")

# 关闭工作簿
wb.close()
