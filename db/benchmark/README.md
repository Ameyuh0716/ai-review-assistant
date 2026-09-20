# 索引优化基准测试

用于量化「数据库索引优化」的效果，并作为后续调整索引时的回归依据。

## 目录结构

| 文件 | 用途 |
|---|---|
| `seed.sql` | 生成模拟数据（含重度用户/超长会话等**数据倾斜**） |
| `queries.sql` | 22 条真实业务查询的 `EXPLAIN ANALYZE` 探针 |
| `report.py` | 解析单次运行结果，输出每条查询的耗时与访问路径 |
| `compare.py` | 对比「加索引前 / 加索引后」，输出加速比 |

## 为什么需要基准测试

「数据多了会不会变慢」不能靠猜。本项目曾出现两个**看起来没问题、实际是性能陷阱**的写法：

1. `WHERE conversation_id = ? ORDER BY id LIMIT 1`（取会话首条消息）
   → 优化器按 `conversation_id` 的选择性（约 3.7%）估算「沿主键扫几十行即可命中」，
     因而放弃 `(conversation_id, id)` 索引改走主键索引；
     但同一会话的消息在物理上连续，实际要扫完整张表才找到（56 万行时 35～190ms）。
2. `WHERE content ILIKE '%关键词%' LIMIT 3`（RAG 关键词兜底）
   → 该查询**没有其它过滤条件**，且「无匹配」正是它的常态；
     全表扫描无法提前终止，必须扫完所有行（5 万块 × 500 字符时 416ms）。

两者的共同点：只在数据量变大时才暴露，靠读代码几乎看不出来。

## 使用方法

```bash
# 1. 建基准库（与生产库隔离，绝不影响 review_db）
docker exec ai-review-postgres psql -U postgres -c "CREATE DATABASE idx_bench;"
docker exec -i ai-review-postgres psql -U postgres -d idx_bench -q < init.sql
docker exec ai-review-postgres psql -U postgres -d idx_bench -q -c "
  CREATE EXTENSION IF NOT EXISTS vector;
  CREATE TABLE vector_store (id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
                             content text, metadata json, embedding vector(1536));
  CREATE INDEX spring_ai_vector_index ON vector_store USING hnsw (embedding vector_cosine_ops);"

# 2. 灌入模拟数据（约 220 万行，耗时 1～2 分钟）
docker exec -i ai-review-postgres psql -U postgres -d idx_bench -q < db/benchmark/seed.sql

# 3. 测量「加索引前」（跑两次，第一次是冷启动，取第二次）
docker exec -i ai-review-postgres psql -U postgres -d idx_bench < db/benchmark/queries.sql > /tmp/before.txt

# 4. 应用索引优化
docker exec -i ai-review-postgres psql -U postgres -d idx_bench < db/migration/2026-09-19-index-optimization.sql

# 5. 测量「加索引后」（跑 3 次，脚本取中位数以消除噪声）
for i in 1 2 3; do
  docker exec -i ai-review-postgres psql -U postgres -d idx_bench < db/benchmark/queries.sql > /tmp/after$i.txt
done

# 6. 对比
python3 db/benchmark/compare.py /tmp/before.txt /tmp/after1.txt /tmp/after2.txt /tmp/after3.txt

# 7. 清理
docker exec ai-review-postgres psql -U postgres -c "DROP DATABASE idx_bench;"
```

## 数据规模

| 表 | 行数 | 说明 |
|---|---|---|
| review_records | 800,000 | 其中 3 个重度用户各 10 万条 |
| message | 560,000 | 其中 3 个超长会话各 2 万条 |
| agent_log | 290,000 | 其中 9 万条集中在超长会话 |
| rag_search_log | 290,000 | 同上 |
| wrong_answer_book | 150,000 | 其中 1 个重度用户 5 万道 |
| conversation | 50,003 | |
| courses / study_plan | 20,000 | |
| vector_store | 20,000 | 每块约 400 字符 |
| app_user | 1,000 | |

**为什么要造倾斜？** 真实数据从不均匀分布。少数用户有上千条复习记录、少数会话有上千条消息，
这些重度用户才是「数据变多后变慢」的真正受害者。若只造均匀数据，
平均耗时看起来还好，却测不出真实痛点。

## 测量注意事项

- **首次运行不可信**：冷启动时数据不在共享缓冲区，Q3 曾测得 400ms 而稳定值是 36ms。
  脚本因此跑 3 次取中位数。
- **测试机空闲**：并行查询（`Parallel Seq Scan`）会受其它负载影响。
- **`ANALYZE` 必须执行**：否则统计信息陈旧，优化器可能选错计划，
  出现「索引建了却不用」的假象。
- **生产库若要执行 `CREATE INDEX`**：千万行级表应改用 `CREATE INDEX CONCURRENTLY`
  （不阻塞读写，但需在事务块外执行）。
