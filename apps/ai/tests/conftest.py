"""
测试全局夹具：保证测试环境与「净克隆 + 无 key」状态一致。
"""
import os

# 必须在 app 首次导入前执行：清掉可能存在的本地密钥/令牌，等价净克隆环境
for _var in (
    "DASHSCOPE_API_KEY",
    "DEEPSEEK_API_KEY",
    "ZHIPU_API_KEY",
    "LEDGERPILOT_API_TOKEN",
    "JAVA_BACKEND_URL",
):
    os.environ.pop(_var, None)
