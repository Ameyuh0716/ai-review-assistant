#!/usr/bin/env python3
"""
索引优化基准测试结果分析器。

用途：把 psql 执行 db/benchmark/queries.sql 的输出解析成一张对比表，
      逐条列出每个查询的执行计划特征与耗时，便于"加索引前 vs 加索引后"对照。

用法：
    python3 db/benchmark/report.py /tmp/bench_before.txt [/tmp/bench_after.txt]
"""
import re
import sys

# 计划中出现的、值得点名的退化特征
PLAN_FLAGS = [
    (r'Seq Scan on (message|conversation|review_records|wrong_answer_book|'
     r'vector_store|agent_log|rag_search_log|courses|study_plan|app_user|knowledge_document)',
     'SEQ SCAN'),
    (r'->\s+Sort\b', 'SORT'),
    (r'->\s+Hash Join', 'HASH JOIN'),
    (r'->\s+Nested Loop', 'NESTED LOOP'),
    (r'Disk:', 'DISK SPILL'),
    (r'->\s+Bitmap Heap Scan', 'BITMAP'),
    (r'Index (Scan|Only Scan)', 'INDEX'),
]


def parse(path):
    """把 EXPLAIN ANALYZE 输出解析为 {查询标题: (耗时ms, [计划特征])}"""
    with open(path, encoding='utf-8', errors='replace') as fh:
        text = fh.read()

    # 标题行形如 "#################### Q1 会话消息列表（重度会话 2 万条）####################"
    # 注意：尾部 # 前是否有空格取决于 \echo 的写法，因此这里统一用 \s*#*\s*\n 收尾
    chunks = re.split(r'\n#+\s*(Q\d+[^\n]*?)\s*#*\s*\n', text)
    results = {}
    for i in range(1, len(chunks) - 1, 2):
        title = chunks[i].strip()
        body = chunks[i + 1]
        times = re.findall(r'Execution Time: ([\d.]+) ms', body)
        if not times:
            continue
        flags = [name for pattern, name in PLAN_FLAGS if re.search(pattern, body)]
        # 去掉被 INDEX 覆盖时的重复标注
        flags = [f for f in flags if f != 'INDEX'] or ['INDEX']
        results[title] = (float(times[-1]), flags)
    return results


def render(before, after):
    print()
    print(f"{'查询':<44}{'加索引前':>26}{'加索引后':>26}")
    print(f"{'':<44}{'耗时ms':>10}{'计划':>16}{'耗时ms':>10}{'计划':>16}")
    print('-' * 96)
    for title in before:
        b_ms, b_flags = before[title]
        a_ms, a_flags = after.get(title, (None, []))
        b_str = f"{b_ms:>10.3f}{'+'.join(b_flags):>16}"
        if a_ms is None:
            a_str = f"{'-':>10}{'-':>16}"
        else:
            ratio = a_ms / b_ms if b_ms > 0 else 0
            mark = ' ⚡' if ratio < 0.8 else ('  ' if ratio < 1.2 else ' ⚠')
            a_str = f"{a_ms:>10.3f}{'+'.join(a_flags):>16}{mark}"
        print(f"{title[:42]:<44}{b_str}{a_str}")
    print('-' * 96)
    b_total = sum(v[0] for v in before.values())
    a_total = sum(v[0] for v in after.values()) if after else 0
    print(f"{'合计':<44}{b_total:>10.3f}{'':>16}{a_total:>10.3f}" if after
          else f"{'合计':<44}{b_total:>10.3f}")
    if after and b_total > 0:
        print(f"{'整体加速':<44}{b_total / a_total:>10.1f}x")


if __name__ == '__main__':
    before = parse(sys.argv[1])
    after = parse(sys.argv[2]) if len(sys.argv) > 2 else {}
    render(before, after)
