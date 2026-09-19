#!/usr/bin/env python3
"""
索引优化效果对比报告。

把 db/benchmark/queries.sql 在「加索引前 / 加索引后」两次运行的结果解析为对比表。

用法：
    python3 db/benchmark/compare.py 前.txt 后_run1.txt 后_run2.txt 后_run3.txt
    后处理多个 run：取中位数，避免冷启动噪声干扰结论。
"""
import re
import sys


def parse(path):
    """解析 EXPLAIN ANALYZE 输出 → {查询标题: (耗时ms, [计划特征])}"""
    with open(path, encoding='utf-8', errors='replace') as fh:
        text = fh.read()

    chunks = re.split(r'\n#+\s*(Q\d+[^\n]*?)\s*#*\s*\n', text)
    out = {}
    for i in range(1, len(chunks) - 1, 2):
        title = chunks[i].strip()
        body = chunks[i + 1]
        times = re.findall(r'Execution Time: ([\d.]+) ms', body)
        if not times:
            continue
        flags = []
        if re.search(r'Seq Scan on (?!vector_store|wrong_answer_book\b).*\b(message|review_records|conversation|courses|study_plan|app_user)\b', body):
            flags.append('SEQ SCAN')
        elif re.search(r'Seq Scan', body):
            flags.append('seq scan')
        if re.search(r'->\s+Sort\b', body):
            flags.append('SORT')
        if re.search(r'Disk:', body):
            flags.append('DISK')
        if re.search(r'Bitmap Index Scan', body):
            flags.append('bitmap')
        elif re.search(r'(Index Scan|Index Only Scan)', body):
            flags.append('index')
        out[title] = (float(times[-1]), '+'.join(flags) or '-')
    return out


def main():
    before = parse(sys.argv[1])
    after_runs = [parse(p) for p in sys.argv[2:]]

    print()
    print('=' * 104)
    print(f"{'查询':<38}{'加索引前':>22}{'加索引后（3次中位）':>26}{'提升':>14}")
    print(f"{'':<38}{'耗时ms':>11}{'访问路径':>11}{'耗时ms':>12}{'访问路径':>14}")
    print('=' * 104)

    total_b = total_a = 0.0
    for title in before:
        b_ms, b_plan = before[title]
        vals = [r[title][0] for r in after_runs if title in r]
        a_plan = next((r[title][1] for r in after_runs if title in r), '-')
        if not vals:
            continue
        a_ms = sorted(vals)[len(vals) // 2]     # 中位数
        total_b += b_ms
        total_a += a_ms
        speedup = b_ms / a_ms if a_ms > 0 else float('inf')
        if speedup >= 10:
            mark = f'{speedup:>10.0f}x  🚀'
        elif speedup >= 2:
            mark = f'{speedup:>10.1f}x  ⚡'
        elif speedup >= 1.2:
            mark = f'{speedup:>10.2f}x  ✓'
        elif speedup >= 0.8:
            marker = '  '
            mark = f'{speedup:>10.2f}x {marker} '
        else:
            mark = f'{speedup:>10.2f}x  ⚠'
        print(f"{title[:36]:<38}{b_ms:>11.3f}{b_plan:>11}{a_ms:>12.3f}{a_plan:>14}{mark}")

    print('=' * 104)
    print(f"{'合计':<38}{total_b:>11.3f}{'':>11}{total_a:>12.3f}{'':>14}{total_b / total_a:>9.1f}x")
    print()


if __name__ == '__main__':
    main()
