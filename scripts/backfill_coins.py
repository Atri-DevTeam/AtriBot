#!/usr/bin/env python3
"""
历史打卡硬币回填脚本
读取 sign_data/ 下所有老 JSON 导出文件，按排名规则计算硬币，
汇总每人总硬币数，直接写入 check_in_total 表。

排名规则（与 SignRepository.calculateCoins 一致）：
  1-3 名:  90 ~ 100
  4-10 名: 70 ~ 89
  11-25 名: 50 ~ 69
  26+ 名:  20 ~ 49

随机种子 = 日期字符串，保证同一天多次运行结果一致（可复现）。

用法：
    python backfill_coins.py                     # 默认 sign_data 目录
    python backfill_coins.py --dir ./sign_data   # 指定 JSON 目录
    python backfill_coins.py --dry-run           # 只计算不写入
"""

import argparse
import json
import random
import sys
from collections import defaultdict
from pathlib import Path

import yaml  # pip install pyyaml
import pymysql  # pip install pymysql


def load_db_config(config_path: str = None) -> dict:
    """从 config.yml 读取数据库连接信息"""
    if config_path is None:
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
    )


def calculate_coins(rank: int, rng: random.Random) -> int:
    """与 Java 端 SignRepository.calculateCoins 完全一致"""
    if rank <= 3:
        return rng.randint(90, 100)
    elif rank <= 10:
        return rng.randint(70, 89)
    elif rank <= 25:
        return rng.randint(50, 69)
    else:
        return rng.randint(20, 49)


def main():
    parser = argparse.ArgumentParser(
        description="历史打卡硬币回填 — 从 sign_data JSON 计算并写入 check_in_total",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--config",
        default=None,
        help="config.yml 路径（默认脚本目录上级的 config.yml）",
    )
    parser.add_argument(
        "--dir",
        default="sign_data",
        help="sign_data 目录路径（默认 ./sign_data）",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        default=False,
        help="只计算汇总，不写入数据库",
    )
    args = parser.parse_args()

    # 1. 读取 JSON 文件
    data_dir = Path(args.dir)
    if not data_dir.exists():
        print(f"[ERROR] 目录不存在: {data_dir}")
        sys.exit(1)

    json_files = sorted(data_dir.glob("*.json"))
    if not json_files:
        print(f"[ERROR] {data_dir} 下没有 JSON 文件")
        sys.exit(1)

    print(f"[INFO] 找到 {len(json_files)} 个 JSON 文件")

    # 2. 逐天计算硬币
    user_coins: dict[str, int] = defaultdict(int)
    daily_details: list[dict] = []

    for f in json_files:
        with open(f, "r", encoding="utf-8") as fh:
            data = json.load(fh)

        date_str = data.get("date", f.stem)
        rng = random.Random(date_str)  # 日期做种子，可复现

        records = data.get("records", [])
        for rec in records:
            uid = rec["user_open_id"]
            rank = rec["rank"]
            coins = calculate_coins(rank, rng)
            user_coins[uid] += coins
            daily_details.append({
                "date": date_str,
                "user_open_id": uid,
                "rank": rank,
                "coins": coins,
            })

        print(f"  {date_str}: {len(records)} 条记录")

    total_coins = sum(user_coins.values())
    n_users = len(user_coins)

    # 3. 统计
    print(f"\n{'='*50}")
    print(f"天数:       {len(json_files)}")
    print(f"用户数:     {n_users}")
    print(f"总硬币:     {total_coins}")
    print(f"人均硬币:   {total_coins / n_users:.1f}" if n_users > 0 else "人均硬币:   0")

    tier_counts = {"1-3": 0, "4-10": 0, "11-25": 0, "26+": 0}
    tier_coins = {"1-3": 0, "4-10": 0, "11-25": 0, "26+": 0}
    for d in daily_details:
        r = d["rank"]
        if r <= 3:
            tier = "1-3"
        elif r <= 10:
            tier = "4-10"
        elif r <= 25:
            tier = "11-25"
        else:
            tier = "26+"
        tier_counts[tier] += 1
        tier_coins[tier] += d["coins"]

    print(f"\n档位分布:")
    for tier in ["1-3", "4-10", "11-25", "26+"]:
        cnt = tier_counts[tier]
        avg = tier_coins[tier] / cnt if cnt > 0 else 0
        print(f"  {tier}: {cnt} 人次, 平均 {avg:.1f} 硬币")

    # 4. Top 10
    top10 = sorted(user_coins.items(), key=lambda x: x[1], reverse=True)[:10]
    print(f"\nTop 10:")
    for i, (uid, total) in enumerate(top10, 1):
        print(f"  {i:>2}. {uid[:20]}...  {total} 硬币")

    print(f"{'='*50}")

    if args.dry_run:
        print("\n[Dry Run] 未写入数据库。")
        return

    # 5. 写入数据库
    print("[INFO] 加载数据库配置...")
    db_cfg = load_db_config(args.config)
    print(f"[INFO] 连接 {db_cfg['host']}:{db_cfg['port']}/{db_cfg['database']} ...")
    conn = get_connection(db_cfg)

    upsert_sql = (
        "INSERT INTO `check_in_total`"
        " (`user_open_id`, `total_count`, `total_coins`, `last_check_in_date`)"
        " VALUES (%s, 0, %s, CURDATE())"
        " ON DUPLICATE KEY UPDATE `total_coins` = `total_coins` + VALUES(`total_coins`)"
    )

    try:
        with conn.cursor() as cursor:
            updated = 0
            inserted = 0
            for uid, total in user_coins.items():
                cursor.execute(upsert_sql, (uid, total))
                if cursor.rowcount == 1:
                    inserted += 1
                else:
                    updated += 1
            conn.commit()

        print(f"\n[INFO] 写入完成 — INSERT {inserted} 行, UPDATE {updated} 行")

        # 验证
        with conn.cursor() as cursor:
            cursor.execute(
                "SELECT COUNT(*) AS cnt, SUM(`total_coins`) AS sum_coins"
                " FROM `check_in_total`"
            )
            row = cursor.fetchone()
            print(f"[INFO] 验证: check_in_total 共 {row[0]} 行, 硬币总和 {row[1]}")

    finally:
        conn.close()
        print("[INFO] 数据库连接已关闭。")


if __name__ == "__main__":
    main()
