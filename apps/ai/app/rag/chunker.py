"""
文档分块策略

将长文档按语义边界分割为适合检索的片段。
"""
import re


def chunk_markdown(text: str, max_chars: int = 800, overlap: int = 100) -> list[dict]:
    """
    将 Markdown 文档按标题/段落分割为知识片段

    Args:
        text: Markdown 文档内容
        max_chars: 每个片段最大字符数
        overlap: 片段间重叠字符数

    Returns:
        [{"content": str, "heading": str}, ...]
    """
    chunks = []
    current_heading = ""

    # 按标题分割
    sections = re.split(r'^(#{1,3}\s+.+)$', text, flags=re.MULTILINE)

    for i in range(len(sections)):
        section = sections[i].strip()

        # 检测标题
        if re.match(r'^#{1,3}\s+', section):
            current_heading = re.sub(r'^#{1,3}\s+', '', section).strip()
            continue

        if not section:
            continue

        # 如果段落超过 max_chars，按句子分割
        if len(section) <= max_chars:
            chunks.append({
                "content": section,
                "heading": current_heading,
            })
        else:
            # 按句号/换行分割
            sentences = re.split(r'(?<=[。！？\n])', section)
            current_chunk = ""
            for sentence in sentences:
                sentence = sentence.strip()
                if not sentence:
                    continue

                if len(current_chunk) + len(sentence) > max_chars and current_chunk:
                    chunks.append({
                        "content": current_chunk.strip(),
                        "heading": current_heading,
                    })
                    # 保留尾部重叠
                    current_chunk = current_chunk[-overlap:] + sentence
                else:
                    current_chunk += sentence

            if current_chunk.strip():
                chunks.append({
                    "content": current_chunk.strip(),
                    "heading": current_heading,
                })

    return chunks


def chunk_by_line(text: str, max_chars: int = 800) -> list[dict]:
    """
    按行分割（适用于纯文本/列表格式）

    每个块包含若干行，总字符不超过 max_chars
    """
    lines = text.strip().split('\n')
    chunks = []
    current_lines = []
    current_len = 0

    for line in lines:
        line_stripped = line.strip()
        if not line_stripped or line_stripped.startswith('#'):
            # 空行或标题行，先保存当前块
            if current_lines:
                chunks.append({"content": '\n'.join(current_lines), "heading": ""})
                current_lines = []
                current_len = 0
            continue

        if current_len + len(line_stripped) > max_chars and current_lines:
            chunks.append({"content": '\n'.join(current_lines), "heading": ""})
            current_lines = []
            current_len = 0

        current_lines.append(line_stripped)
        current_len += len(line_stripped)

    if current_lines:
        chunks.append({"content": '\n'.join(current_lines), "heading": ""})

    return chunks
