# all-in-one 演示镜像

一条命令试用完整 LedgerPilot（AI 无 key 走规则层；后端 H2 演示库，重启即重置）：

```bash
docker build -f deploy/all-in-one/Dockerfile -t ledgerpilot/all-in-one .
docker run -p 3000:3000 ledgerpilot/all-in-one
# 打开 http://localhost:3000 （admin / 123456）
```

仅用于演示/评估。生产请用 `deploy/docker-compose.yml`（组件分容器、MySQL 持久化、env 注入密钥）。
