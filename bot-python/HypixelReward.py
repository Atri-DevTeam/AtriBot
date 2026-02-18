import asyncio
import websockets
import json
import cloudscraper
import re
import time
import ast
from urllib.parse import urlparse

session_storage = {}

def get_scraper():
    return cloudscraper.create_scraper(
        browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True}
    )

def parse_js_object(html_content):
    rewards = []

    # 1. 优先尝试匹配 window.appData (包含所有数据，最完整)
    print("[Debug] 尝试提取 window.appData...")
    app_match = re.search(r'window\.appData\s*=\s*(\{.*?\});', html_content, re.DOTALL)

    raw_data_str = ""
    if app_match:
        raw_data_str = app_match.group(1)
    else:
        # 2. 如果 appData 没找到，尝试直接匹配 rewards 数组
        print("[Debug] appData 未找到，尝试匹配 rewards 数组...")
        r_match = re.search(r'rewards"\s*:\s*(\[\s*\{.*?\}\s*\])', html_content, re.DOTALL)
        if r_match:
            raw_data_str = '{"rewards": ' + r_match.group(1) + '}'

    if not raw_data_str:
        return []

    # === 关键修复步骤 ===
    try:
        # 方法 A: 直接尝试 JSON 解析 (如果运气好是标准 JSON)
        data = json.loads(raw_data_str)
        return data.get('rewards', [])
    except Exception as e_json:
        print(f"[Debug] 标准 JSON 解析失败 ({e_json})，尝试清洗数据...")

        try:
            clean_str = raw_data_str.replace('true', 'True').replace('false', 'False').replace('null', 'None')
            data = ast.literal_eval(clean_str)
            return data.get('rewards', [])
        except Exception as e_ast:
            print(f"[Debug] AST 解析也失败: {e_ast}")

            try:
                # 替换掉反斜杠转义的单引号
                fixed_str = raw_data_str.replace("\\'", "'")
                # 再次尝试 JSON
                data = json.loads(fixed_str)
                return data.get('rewards', [])
            except:
                pass

    return []

def fetch_reward_list(url):
    """ 第一步：获取列表，并暂存 Session """
    print(f"\n[Debug] === 开始处理 URL: {url} ===")

    if "rewards.hypixel.net" not in url and "hypixel.net" in url:
        url = url.replace("hypixel.net", "rewards.hypixel.net")

    try:
        parsed = urlparse(url)
        if "hypixel.net" not in parsed.netloc:
            print("[Debug] 域名校验失败")
            return {"success": False, "msg": "这不是 Hypixel 的链接喵！"}
        # 尝试提取 ID
        parts = url.split("claim-reward/")
        if len(parts) > 1:
            reward_id = parts[-1].split("/")[0].split("?")[0]
        else:
            reward_id = "unknown"
        print(f"[Debug] 提取到的 Reward ID: {reward_id}")
    except Exception as e:
        print(f"[Debug] URL 解析异常: {e}")
        return {"success": False, "msg": "链接格式错误喵！"}

    scraper = get_scraper()

    try:
        print("[Debug] 正在发送 GET 请求...")
        resp = scraper.get(url, timeout=15)
        print(f"[Debug] 请求状态码: {resp.status_code}")
    except Exception as e:
        print(f"[Debug] 请求发生异常: {e}")
        return {"success": False, "msg": f"网络错误: {str(e)}"}

    html = resp.text

    # 提取 Token
    token_match = re.search(r'window\.securityToken\s*=\s*["\']([^"\']+)["\']', html)
    if not token_match:
        print("[Debug] ❌ 未找到 securityToken！Regex 匹配失败。")
        # 调试保存
        with open("debug_fail.html", "w", encoding="utf-8") as f: f.write(html)
        return {"success": False, "msg": "无法找到 Token，可能链接已失效或被 CF 拦截。"}

    security_token = token_match.group(1)
    print(f"[Debug] 成功获取 Token: {security_token[:15]}...")

    session_storage[security_token] = scraper

    # === 使用新的解析函数 ===
    rewards_data = parse_js_object(html)

    formatted_rewards = []
    if rewards_data:
        print(f"[Debug] 成功解析到 {len(rewards_data)} 个奖励对象")
        for idx, reward in enumerate(rewards_data):
            rarity = reward.get('rarity', 'COMMON').upper()

            r_name = reward.get('reward', 'Unknown')
            if 'gameType' in reward: r_name = f"{reward['gameType']} - {r_name}"
            if 'amount' in reward: r_name = f"{reward['amount']}x {r_name}"
            formatted_rewards.append(f"[{idx}] {rarity}: {r_name}")
    else:
        print("[Debug] ❌ 最终未能提取到任何奖励数据")
        return {"success": False, "msg": "解析奖励数据失败，请检查日志。"}

    print(f"[Debug] 成功提取列表: {formatted_rewards}")
    return {
        "success": True,
        "rewards": formatted_rewards,
        "security_token": security_token,
        "reward_id": reward_id,
        "original_url": url
    }

def execute_claim(token, reward_id, choice, original_url):
    print(f"\n[Debug] === 开始执行领取: ID={reward_id}, Choice={choice} ===")

    scraper = session_storage.get(token)
    if not scraper:
        print(f"[Debug] ❌ Token {token[:10]}... 在缓存中未找到")
        return {"success": False, "msg": "会话已过期或不存在，请重新发送链接喵！"}

    claim_url = "https://rewards.hypixel.net/claim-reward/claim"

    params = {
        "option": choice,
        "id": reward_id,
        "activeAd": 1,
        "_csrf": token,
        "watchedFallback": "false",
        "skipped": 0
    }

    headers = {
        "Referer": original_url,
        "Origin": "https://rewards.hypixel.net",
        "X-Requested-With": "XMLHttpRequest",
        "User-Agent": scraper.headers['User-Agent'] # 保持 UA 一致
    }

    try:
        final_resp = scraper.post(claim_url, params=params, headers=headers)
        print(f"[Debug] 领取请求状态码: {final_resp.status_code}")

        # 领取后从缓存删除 token
        if token in session_storage:
            del session_storage[token]

        if final_resp.status_code == 200:
            return {"success": True, "msg": "领取成功喵！"}
        else:
            return {"success": False, "msg": f"领取失败，状态码: {final_resp.status_code}"}
    except Exception as e:
        print(f"[Debug] 领取请求异常: {e}")
        return {"success": False, "msg": f"请求异常: {str(e)}"}

# === WebSocket 服务端逻辑 ===
connected_clients = set()

async def handler(websocket):
    connected_clients.add(websocket)
    try:
        async for message in websocket:
            try:
                data = json.loads(message)
                action = data.get("action")
                group_id = data.get("group_id")

                if action == "fetch":
                    url = data.get("url")
                    # 使用同步调用
                    result = fetch_reward_list(url)

                    if result["success"]:
                        response = {
                            "type": "selection_needed",
                            "group_id": group_id,
                            "rewards": result["rewards"],
                            "security_token": result["security_token"],
                            "reward_id": result["reward_id"],
                            "original_url": result["original_url"]
                        }
                    else:
                        response = {"type": "error", "group_id": group_id, "msg": result["msg"]}
                    await websocket.send(json.dumps(response))

                elif action == "claim":
                    token = data.get("security_token")
                    rid = data.get("reward_id")
                    choice = data.get("choice")
                    orig_url = data.get("original_url")

                    result = execute_claim(token, rid, choice, orig_url)

                    response = {
                        "type": "result",
                        "group_id": group_id,
                        "success": result["success"],
                        "msg": result["msg"]
                    }
                    await websocket.send(json.dumps(response))

            except Exception as e:
                print(f"[Debug] WebSocket 处理异常: {e}")
                import traceback
                traceback.print_exc()

    except websockets.exceptions.ConnectionClosed:
        pass
    finally:
        connected_clients.discard(websocket)

async def main():
    # 允许所有 IP 连接 (0.0.0.0) 方便调试
    async with websockets.serve(handler, "0.0.0.0", 8765):
        print("Python Hypixel 服务端 (修复版) 已启动...")
        await asyncio.Future()

if __name__ == "__main__":
    asyncio.run(main())