# -*- coding: utf-8 -*-
import asyncio
import websockets
import json
import cloudscraper
import re
from urllib.parse import urlparse
import traceback

WORKER_DOMAIN = "hypixel-rewards.yzljc.top"
session_storage = {}

def get_scraper():
    return cloudscraper.create_scraper(
        browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True}
    )

def parse_js_object(html_content):
    r_match = re.search(
        r'rewards"\s*:\s*(\[\s*\{.*?\}\s*\])',
        html_content,
        re.DOTALL
    )

    if not r_match:
        return []

    raw_data_str = '{"rewards": ' + r_match.group(1) + '}'

    # Hypixel 把 JSON 放在 JS 字符串里，可能包含 \'，JSON 不支持
    raw_data_str = raw_data_str.replace("\\'", "'")

    try:
        data = json.loads(raw_data_str)
        return data.get("rewards", [])
    except Exception as e:
        print(f"[Error] JSON 解析失败: {e}")
        return []

def fetch_reward_list(url):
    print(f"[Info] 开始获取奖励数据 (源链接): {url}")
    try:
        parsed = urlparse(url)
        if "hypixel.net" not in parsed.netloc:
            return {"success": False, "msg": "这不是 Hypixel 的链接喵！"}
        parts = url.split("claim-reward/")
        reward_id = parts[-1].split("/")[0].split("?")[0] if len(parts) > 1 else "unknown"
    except Exception:
        return {"success": False, "msg": "链接格式错误喵！"}

    proxy_url = url.replace("rewards.hypixel.net", WORKER_DOMAIN).replace("hypixel.net", WORKER_DOMAIN)
    scraper = get_scraper()
    try:
        resp = scraper.get(proxy_url, timeout=15)
    except Exception as e:
        return {"success": False, "msg": f"代理请求网络错误: {str(e)}"}

    html = resp.text
    token_match = re.search(r'window\.securityToken\s*=\s*["\']([^"\']+)["\']', html)
    if not token_match:
        return {"success": False, "msg": "无法找到 Token，可能链接已失效或被拦截。"}

    security_token = token_match.group(1)
    session_storage[security_token] = scraper
    rewards_data = parse_js_object(html)

    formatted_rewards = []
    if rewards_data:
        for idx, reward in enumerate(rewards_data):
            rarity = reward.get('rarity', 'COMMON').upper()
            r_name = reward.get('reward', 'Unknown')
            if 'gameType' in reward: r_name = f"{reward['gameType']} - {r_name}"
            if 'amount' in reward: r_name = f"{reward['amount']}x {r_name}"
            formatted_rewards.append(f"[{idx}] {rarity}: {r_name}")
    else:
        return {"success": False, "msg": "解析奖励数据失败。"}

    print(f"[Success] 成功提取 ID: {reward_id}")
    return {
        "success": True,
        "rewards": formatted_rewards,
        "security_token": security_token,
        "reward_id": reward_id,
        "original_url": url
    }

def execute_claim(token, reward_id, choice, original_url):
    print(f"[Info] 开始执行领取: ID={reward_id}, Choice={choice}")
    scraper = session_storage.get(token)
    if not scraper:
        return {"success": False, "msg": "会话已过期或不存在，请重新发送链接喵！"}

    claim_url = f"https://{WORKER_DOMAIN}/claim-reward/claim"
    params = {"option": choice, "id": reward_id, "activeAd": 1, "_csrf": token, "watchedFallback": "false", "skipped": 0}
    headers = {
        "Referer": original_url,
        "Origin": "https://rewards.hypixel.net",
        "X-Requested-With": "XMLHttpRequest",
        "User-Agent": scraper.headers['User-Agent']
    }

    try:
        final_resp = scraper.post(claim_url, params=params, headers=headers, timeout=15)
        if token in session_storage:
            del session_storage[token]
        if final_resp.status_code == 200:
            print(f"[Success] ID: {reward_id} 领取成功")
            return {"success": True, "msg": "领取成功喵！"}
        else:
            return {"success": False, "msg": f"领取失败，状态码: {final_resp.status_code}"}
    except Exception as e:
        return {"success": False, "msg": f"请求异常: {str(e)}"}

connected_clients = set()

async def handler(websocket):
    connected_clients.add(websocket)
    try:
        async for message in websocket:
            asyncio.create_task(process_message(websocket, message))
    except websockets.exceptions.ConnectionClosed:
        pass
    finally:
        connected_clients.discard(websocket)

async def process_message(websocket, message):
    try:
        data = json.loads(message)
        action = data.get("action")
        session_id = data.get("session_id") # 【核心】唯一会话标识

        if action == "fetch":
            url = data.get("url")
            result = await asyncio.to_thread(fetch_reward_list, url)

            if result["success"]:
                response = {
                    "type": "selection_needed",
                    "session_id": session_id,
                    "rewards": result["rewards"],
                    "security_token": result["security_token"],
                    "reward_id": result["reward_id"],
                    "original_url": result["original_url"]
                }
            else:
                response = {"type": "error", "session_id": session_id, "msg": result["msg"]}

            await websocket.send(json.dumps(response))

        elif action == "claim":
            token = data.get("security_token")
            rid = data.get("reward_id")
            choice = data.get("choice")
            orig_url = data.get("original_url")

            result = await asyncio.to_thread(execute_claim, token, rid, choice, orig_url)

            response = {
                "type": "result",
                "session_id": session_id,
                "success": result["success"],
                "msg": result["msg"]
            }
            await websocket.send(json.dumps(response))

    except Exception as e:
        print(f"[Error] WebSocket 处理异常: {e}")
        traceback.print_exc()

async def main():
    async with websockets.serve(handler, "0.0.0.0", 8765, ping_interval=20, ping_timeout=20):
        print("Python Hypixel 服务端启动 (纯净 session_id 通信协议)")
        await asyncio.Future()

if __name__ == "__main__":
    asyncio.run(main())