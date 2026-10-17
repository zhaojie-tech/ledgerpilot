"""
LLM 错误分类。
"""


class ConfigError(Exception):
    """配置类错误：提供商不支持 response_format 等；不计入熔断"""


class LengthLimitError(Exception):
    """输出长度截断（finish_reason=length 单列）"""
