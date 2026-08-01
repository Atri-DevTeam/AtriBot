#!/usr/bin/env python3
"""
DAU (Daily Active Users) 计算脚本
从 official_group_record 和 official_c2c_record 两张表中统计每日活跃用户数。

DAU 定义：当天至少发送过一条消息（sender_is_bot = FALSE）的去重 union_openId 数量。
同时统计群聊 DAU、私聊 DAU 以及总 DAU（union 后去重）。

用法：
    python calc_dau.py                    # 读取 ../config.yml，统计全部历史 DAU
    python calc_dau.py --days 30          # 只统计最近 30 天
    python calc_dau.py --start 2026-06-01 # 从指定日期开始
    python calc_dau.py --csv dau.csv      # 导出为 CSV
"""

import argparse
import sys
from collections import defaultdict
from datetime import date, datetime, timedelta
from pathlib import Path

import yaml  # pip install pyyaml
import pymysql  # pip install pymysql


def load_db_config(config_path: str = None) -> dict:
    """从 config.yml 读取数据库连接信息"""
    if config_path is None:
        # 默认脚本所在目录的上级目录下的 config.yml
        config_path = Path(__file__).resolve().parent.parent / "config.yml"

    if not Path(config_path).exists():
        print(f"[ERROR] 配置文件不存在: {config_path}")
        sys.exit(1)

    with open(config_path, "r", encoding="utf-8") as f:
        cfg = yaml.safe_load(f)

    mysql_cfg = cfg.get("mysql", {})
    return {
        "host": mysql_cfg.get("host", "localhost"),
        "port": mysql_cfg.get("port", 3306),
        "database": mysql_cfg.get("database", "database"),
        "user": mysql_cfg.get("username", "root"),
        "password": mysql_cfg.get("password", ""),
        "charset": "utf8mb4",
    }


def get_connection(db_cfg: dict):
    """创建 MySQL 连接"""
    return pymysql.connect(
        host=db_cfg["host"],
        port=db_cfg["port"],
        user=db_cfg["user"],
        password=db_cfg["password"],
        database=db_cfg["database"],
        charset=db_cfg["charset"],
        cursorclass=pymysql.cursors.DictCursor,
    )


def build_date_filter(start_date: str = None, days: int = None) -> str:
    """构造 WHERE 条件中的日期过滤子句"""
    if days is not None and days > 0:
        return f"AND created_at >= DATE_SUB(NOW(), INTERVAL {int(days)} DAY)"
    if start_date:
        return f"AND created_at >= '{start_date}'"
    return ""


def query_dau(conn, table: str, user_col: str, date_filter: str) -> dict:
    """
    查询单张表的 DAU。
    返回 {date_str: set_of_user_ids}
    """
    sql = f"""
        SELECT
            DATE(created_at) AS day,
            `{user_col}` AS user_id
        FROM `{table}`
        WHERE sender_is_bot = FALSE
          AND `{user_col}` IS NOT NULL
          AND `{user_col}` != ''
          {date_filter}
    """
    dau_map = defaultdict(set)
    with conn.cursor() as cursor:
        cursor.execute(sql)
        for row in cursor.fetchall():
            day_str = str(row["day"])
            dau_map[day_str].add(row["user_id"])
    return dau_map


def main():
    parser = argparse.ArgumentParser(
        description="计算 AtriBot 的 DAU（日活跃用户数）",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--config",
        default=None,
        help="config.yml 路径（默认脚本目录上级的 config.yml）",
    )
    parser.add_argument(
        "--days",
        type=int,
        default=None,
        help="只统计最近 N 天（与 --start 互斥）",
    )
    parser.add_argument(
        "--start",
        default=None,
        help="起始日期，格式 YYYY-MM-DD",
    )
    parser.add_argument(
        "--csv",
        default=None,
        help="导出 CSV 文件路径",
    )
    parser.add_argument(
        "--with-wau",
        action="store_true",
        default=False,
        help="同时输出周活 (WAU) 数据",
    )
    args = parser.parse_args()

    if args.days and args.start:
        print("[ERROR] --days 和 --start 不能同时使用")
        sys.exit(1)

    # 1. 加载配置并连接数据库
    print("[INFO] 加载数据库配置...")
    db_cfg = load_db_config(args.config)
    print(f"[INFO] 连接 {db_cfg['host']}:{db_cfg['port']}/{db_cfg['database']} ...")
    conn = get_connection(db_cfg)

    date_filter = build_date_filter(args.start, args.days)
    try:
        # 2. 查询群聊 DAU
        print("[INFO] 查询群聊消息 (official_group_record)...")
        group_dau = query_dau(conn, "official_group_record", "union_openId", date_filter)

        # 3. 查询私聊 DAU
        print("[INFO] 查询私聊消息 (official_c2c_record)...")
        c2c_dau = query_dau(conn, "official_c2c_record", "union_openId", date_filter)

        # 4. 合并计算总 DAU
        all_dates = sorted(set(group_dau.keys()) | set(c2c_dau.keys()))

        if not all_dates:
            print("[INFO] 没有查询到任何数据。")
            return

        print(f"\n{'='*70}")
        print(f"{'Date':<12} {'Group DAU':>10} {'C2C DAU':>10} {'Total DAU':>10}")
        print(f"{'-'*70}")

        rows = []
        total_group = 0
        total_c2c = 0
        total_dau_sum = 0  # 用于算日均

        for day in all_dates:
            g_set = group_dau.get(day, set())
            c_set = c2c_dau.get(day, set())
            union_set = g_set | c_set
            g_count = len(g_set)
            c_count = len(c_set)
            u_count = len(union_set)

            print(f"{day:<12} {g_count:>10} {c_count:>10} {u_count:>10}")

            rows.append({
                "date": day,
                "group_dau": g_count,
                "c2c_dau": c_count,
                "total_dau": u_count,
            })
            total_group += g_count
            total_c2c += c_count
            total_dau_sum += u_count

        n_days = len(all_dates)
        print(f"{'-'*70}")
        print(f"{'日均':<12} {total_group / n_days:>10.1f} {total_c2c / n_days:>10.1f} {total_dau_sum / n_days:>10.1f}")
        print(f"{'总计(非去重)':<12} {total_group:>10} {total_c2c:>10} {total_dau_sum:>10}")
        print(f"{'='*70}")

        # 5. WAU（周活跃用户）— 如果需要
        if args.with_wau:
            print("\n--- WAU (Weekly Active Users) ---")
            wau_data = compute_wau(rows)
            print(f"{'Week':<12} {'WAU':>10}")
            print(f"{'-'*30}")
            for week_label, wau_count in wau_data.items():
                print(f"{week_label:<12} {wau_count:>10}")

        # 6. 导出 CSV
        if args.csv:
            import csv
            csv_path = Path(args.csv)
            with open(csv_path, "w", newline="", encoding="utf-8-sig") as f:
                writer = csv.DictWriter(f, fieldnames=["date", "group_dau", "c2c_dau", "total_dau"])
                writer.writeheader()
                writer.writerows(rows)
            print(f"\n[INFO] CSV 已导出到: {csv_path.resolve()}")

    finally:
        conn.close()
        print("\n[INFO] 数据库连接已关闭。")


def compute_wau(rows: list) -> dict:
    """
    简易 WAU 计算：按 ISO 周聚合。
    rows 包含每条记录的 date (str) 和 total_dau (int)。
    注意：这是对已聚合的每日 DAU 再按周去重计算，**不是**从原始数据重新按周去重用户。
    如需精确 WAU，需要从原始数据按周对 union_openId 去重。
    """
    from collections import defaultdict as dd
    week_sets = dd(set)
    # 这里只能做粗略估算，精确 WAU 需要回到原始查询
    print("[WARN] 精确 WAU 需要从原始数据按周去重，此处仅为 DAU 周均。")
    weekly_sums = dd(int)
    weekly_days = dd(int)
    for r in rows:
        d = datetime.strptime(r["date"], "%Y-%m-%d")
        iso_year, iso_week, _ = d.isocalendar()
        key = f"{iso_year}-W{iso_week:02d}"
        weekly_sums[key] += r["total_dau"]
        weekly_days[key] += 1

    return {k: round(weekly_sums[k] / weekly_days[k], 1) for k in sorted(weekly_sums)}


if __name__ == "__main__":
    main()
