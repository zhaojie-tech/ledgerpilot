"""知识库导入加固——add-then-swap、导入锁串行、KNOWLEDGE_DIR 白名单、路由异步化。"""
import asyncio
import time

import pytest

from app.rag import knowledge_base as kb


class FakeCollection:
    """记录调用顺序的 chroma collection 替身。"""

    def __init__(self, existing_ids=None, fail_add=False):
        self.existing_ids = existing_ids or []
        self.fail_add = fail_add
        self.calls = []
        self.added_metadatas = []

    def get(self, where=None, include=None):
        self.calls.append(("get", where))
        return {"ids": list(self.existing_ids)}

    def add(self, ids, documents, metadatas):
        self.calls.append(("add", list(ids)))
        self.added_metadatas.extend(metadatas)
        if self.fail_add:
            raise RuntimeError("embedding 调用失败（模拟）")

    def delete(self, ids=None, where=None):
        self.calls.append(("delete", list(ids) if ids else where))


@pytest.fixture
def knowledge_file(tmp_path):
    f = tmp_path / "test_knowledge.md"
    f.write_text("# 标题\n\n这是测试知识内容，足够长以产生分块。" * 5, encoding="utf-8")
    return f


def test_import_file_is_add_then_swap(monkeypatch, knowledge_file):
    """顺序必须是 get → add → delete：导入失败不丢旧数据。"""
    fake = FakeCollection(existing_ids=["old-1", "old-2"])
    monkeypatch.setattr(kb, "get_collection", lambda: fake)

    n = kb.import_file(str(knowledge_file), "standard")

    assert n > 0
    ops = [c[0] for c in fake.calls]
    assert ops == ["get", "add", "delete"]
    new_ids = fake.calls[1][1]
    deleted_ids = fake.calls[2][1]
    assert set(deleted_ids) == {"old-1", "old-2"}
    assert not (set(new_ids) & set(deleted_ids))  # 新旧 id 不相交


def test_import_failure_keeps_old_data(monkeypatch, knowledge_file):
    """add 失败（如嵌入外呼报错）→ 不删除旧片段、返回 0。"""
    fake = FakeCollection(existing_ids=["old-1"], fail_add=True)
    monkeypatch.setattr(kb, "get_collection", lambda: fake)

    n = kb.import_file(str(knowledge_file), "standard")

    assert n == 0
    ops = [c[0] for c in fake.calls]
    assert "delete" not in ops


def test_import_idempotent_new_ids_each_run(monkeypatch, knowledge_file):
    """每次导入生成新 id（revision 参与哈希），避免与既有片段 id 冲突。"""
    fake = FakeCollection(existing_ids=["old-1"])
    monkeypatch.setattr(kb, "get_collection", lambda: fake)

    kb.import_file(str(knowledge_file), "standard")
    first_ids = fake.calls[1][1]
    fake.calls.clear()
    fake.existing_ids = list(first_ids)

    kb.import_file(str(knowledge_file), "standard")
    second_ids = fake.calls[1][1]
    assert not (set(first_ids) & set(second_ids))


def test_import_all_reads_only_whitelist_dir(monkeypatch, tmp_path):
    """导入只扫描 KNOWLEDGE_BASE_DIR（白名单），目录外文件一律不读。"""
    whitelist_dir = tmp_path / "knowledge"
    whitelist_dir.mkdir()
    inside = whitelist_dir / "税率.md"
    inside.write_text("增值税税率 13%\n", encoding="utf-8")
    outside = tmp_path / "secret.md"
    outside.write_text("不该被导入的内容\n", encoding="utf-8")

    fake = FakeCollection()
    monkeypatch.setattr(kb, "KNOWLEDGE_BASE_DIR", str(whitelist_dir))
    monkeypatch.setattr(kb, "get_collection", lambda: fake)

    stats = kb.import_all()

    imported_sources = {m["source"] for m in fake.added_metadatas}
    assert stats["total"] > 0
    assert "税率.md" in imported_sources
    assert "secret.md" not in imported_sources


async def test_knowledge_import_route_serialized(monkeypatch):
    """并发调用 /knowledge/import 必须被进程内锁串行化。"""
    events = []

    def fake_import_all():
        events.append("enter")
        time.sleep(0.05)
        events.append("exit")
        return {"total": 2}

    monkeypatch.setattr("app.rag.knowledge_base.import_all", fake_import_all)
    from app.api.routes import knowledge_import

    results = await asyncio.gather(knowledge_import(), knowledge_import())

    assert events == ["enter", "exit", "enter", "exit"]  # 无交错
    assert results[0] == {"total": 2}


async def test_knowledge_stats_runs_off_event_loop(monkeypatch):
    """get_stats 经 to_thread 执行（本地 IO 不阻塞事件循环）。"""
    calls = []
    monkeypatch.setattr(
        "app.rag.knowledge_base.get_stats",
        lambda: calls.append("called") or {"total": 7, "by_category": {}},
    )
    from app.api.routes import knowledge_stats

    result = await knowledge_stats()
    assert result["total"] == 7
    assert calls == ["called"]
