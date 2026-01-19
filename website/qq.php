<?php
/**
 * 网站中对应的管理页面
 * 这里对接了我网站里的登陆API
 */

// 1. 配置 Session 参数 (必须与 api.php 保持完全一致)
if (session_status() === PHP_SESSION_NONE) {
    ini_set('session.cookie_httponly', 1);
    ini_set('session.cookie_secure', 0); // 如果是HTTPS，请改为1
    ini_set('session.use_strict_mode', 1);
    ini_set('session.use_only_cookies', 1);
    ini_set('session.cookie_samesite', 'Lax'); // 改为 Lax 以兼容部分跨域跳转场景，或者保持 Strict
    session_start();
}

// 2. 简单的 Session 绑定检查
function check_session_validity() {
    $ua = $_SERVER['HTTP_USER_AGENT'] ?? '';
    if (isset($_SESSION['UA']) && $_SESSION['UA'] !== $ua) {
        return false;
    }
    return true;
}

// 3. 权限判断
$is_admin = false;
// 只有当 session 存在且 permission 为 5 时才放行
if (isset($_SESSION['admin_permission']) && (int)$_SESSION['admin_permission'] === 5 && check_session_validity()) {
    $is_admin = true;
}

// 4. 未登录或权限不足：显示登录页并退出
if (!$is_admin) {
?>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>系统登录 - QQBot管理后台</title>
    <style>
        :root { --primary: #4F46E5; --bg: #f5f6fa; --dark: #1F2937; }
        body { margin: 0; height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--bg); font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        .login-card { background: white; padding: 40px; border-radius: 12px; box-shadow: 0 10px 25px rgba(0,0,0,0.05); width: 100%; max-width: 360px; }
        .login-header { text-align: center; margin-bottom: 30px; }
        .login-header h2 { margin: 0; color: var(--dark); font-size: 24px; }
        .form-group { margin-bottom: 20px; }
        .form-group label { display: block; margin-bottom: 8px; color: #666; font-size: 14px; font-weight: 600; }
        .input-control { width: 100%; padding: 12px; border: 2px solid #eee; border-radius: 6px; box-sizing: border-box; transition: 0.3s; outline: none; }
        .input-control:focus { border-color: var(--primary); }
        .btn-login { width: 100%; padding: 12px; background: var(--primary); color: white; border: none; border-radius: 6px; font-size: 16px; font-weight: 600; cursor: pointer; transition: 0.3s; }
        .btn-login:hover { background: #4338ca; }
        .btn-login:disabled { background: #9ca3af; cursor: not-allowed; }
        #errorMsg { color: #e74c3c; text-align: center; margin-top: 15px; font-size: 14px; min-height: 20px; }
    </style>
</head>
<body>
    <div class="login-card">
        <div class="login-header"><h2>管理员登录</h2></div>
        <div class="form-group"><label>账号</label><input type="text" id="username" class="input-control"></div>
        <div class="form-group"><label>密码</label><input type="password" id="password" class="input-control"></div>
        <button class="btn-login" onclick="doLogin()" id="loginBtn">立即登录</button>
        <div id="errorMsg"></div>
    </div>

    <script>
        async function doLogin() {
            const user = document.getElementById('username').value.trim();
            const pass = document.getElementById('password').value.trim();
            const btn = document.getElementById('loginBtn');
            const msg = document.getElementById('errorMsg');

            if(!user || !pass) { msg.innerText = "请输入账号和密码"; return; }
            btn.disabled = true; btn.innerText = "登录中..."; msg.innerText = "";

            try {
                const formData = new FormData();
                formData.append('action', 'login');
                formData.append('username', user);
                formData.append('password', pass);

                // 这里请求的是同目录下的 api.php 进行登录验证
                const response = await fetch('login.php', { method: 'POST', body: formData });
                const res = await response.json();

                if (res.success && res.permission === 5) {
                    msg.style.color = "#10B981"; msg.innerText = "登录成功，跳转中...";
                    setTimeout(() => location.reload(), 800);
                } else {
                    msg.style.color = "#EF4444";
                    msg.innerText = res.message || (res.permission ? "权限不足(需5级)" : "登录失败");
                    btn.disabled = false; btn.innerText = "立即登录";
                }
            } catch (e) {
                console.error(e); msg.innerText = "请求错误: " + e.message;
                btn.disabled = false; btn.innerText = "立即登录";
            }
        }
        document.addEventListener('keydown', e => { if(e.key === 'Enter') doLogin(); });
    </script>
</body>
</html>
<?php
    exit; // 终止执行，不输出后面的 HTML
}
?>

<!-- ==================== 下方为 5 级管理员可见内容 ==================== -->

<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>QQBot 高级管理后台</title>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&display=swap" rel="stylesheet">
    <style>
        :root {
            --primary: #4F46E5; --primary-hover: #4338ca; --bg: #F3F4F6;
            --sidebar-bg: #1F2937; --sidebar-hover: #374151;
            --text-main: #111827; --text-muted: #6B7280;
            --danger: #EF4444; --success: #10B981; --border-color: #E5E7EB;
            --card-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
        }
        body { font-family: 'Inter', system-ui, sans-serif; margin: 0; display: flex; background: var(--bg); height: 100vh; overflow: hidden; color: var(--text-main); }
        
        #sidebar { width: 280px; background: var(--sidebar-bg); color: #E5E7EB; display: flex; flex-direction: column; box-shadow: 4px 0 24px rgba(0,0,0,0.15); z-index: 10; }
        .sidebar-header { padding: 24px; background: rgba(0,0,0,0.2); font-size: 1.1rem; font-weight: 600; border-bottom: 1px solid rgba(255,255,255,0.1); display: flex; justify-content: space-between; align-items: center; }
        .user-tag { font-size: 10px; background: var(--success); color: white; padding: 2px 6px; border-radius: 4px; margin-left: 10px; }
        
        #groupList { flex: 1; overflow-y: auto; padding: 10px 0; }
        #groupList::-webkit-scrollbar { width: 6px; }
        #groupList::-webkit-scrollbar-thumb { background: #4B5563; border-radius: 3px; }
        
        .group-item { padding: 16px 24px; border-bottom: 1px solid rgba(255,255,255,0.05); cursor: pointer; transition: all 0.2s ease; }
        .group-item:hover { background: var(--sidebar-hover); padding-left: 28px; }
        .group-item.active { background: var(--primary); color: white; }
        .group-name { display: block; font-weight: 500; margin-bottom: 6px; }
        .group-id { font-size: 0.8rem; opacity: 0.6; font-family: monospace; }

        .logout-area { padding: 20px; border-top: 1px solid rgba(255,255,255,0.1); }
        .logout-btn { width: 100%; padding: 10px; background: var(--danger); color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: 600; transition: 0.2s; }
        .logout-btn:hover { background: #DC2626; }

        #content { flex: 1; padding: 30px 40px; overflow-y: auto; display: flex; flex-direction: column; gap: 24px; }
        .card { background: white; padding: 30px; border-radius: 16px; box-shadow: var(--card-shadow); border: 1px solid white; animation: fadeIn 0.4s ease; }
        
        h2 { margin-top: 0; border-bottom: 2px solid var(--bg); padding-bottom: 16px; font-size: 1.25rem; display: flex; align-items: center; gap: 10px; }
        h2::before { content: ''; display: block; width: 4px; height: 24px; background: var(--primary); border-radius: 2px; }

        .feature-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 20px; }
        .feature-item { display: flex; justify-content: space-between; align-items: center; padding: 18px 20px; background: #F9FAFB; border-radius: 12px; border: 1px solid var(--border-color); }
        .feature-item:hover { background: white; border-color: var(--primary); transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.05); }

        .switch { position: relative; display: inline-block; width: 46px; height: 24px; }
        .switch input { opacity: 0; width: 0; height: 0; }
        .slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: #D1D5DB; transition: .3s; border-radius: 34px; }
        .slider:before { position: absolute; content: ""; height: 18px; width: 18px; left: 3px; bottom: 3px; background-color: white; transition: .3s; border-radius: 50%; box-shadow: 0 2px 4px rgba(0,0,0,0.2); }
        input:checked + .slider { background-color: var(--success); }
        input:checked + .slider:before { transform: translateX(22px); }

        .tools-panel { display: flex; flex-wrap: wrap; gap: 16px; align-items: center; background: #F9FAFB; padding: 20px; border-radius: 12px; margin-bottom: 24px; border: 1px solid var(--border-color); }
        .input-group { display: flex; align-items: center; gap: 8px; font-size: 0.9rem; color: var(--text-muted); }
        .input-control { padding: 8px 12px; border: 1px solid #D1D5DB; border-radius: 6px; outline: none; background: white; }
        .input-control:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.1); }
        
        .btn { padding: 9px 18px; border: none; border-radius: 6px; cursor: pointer; font-weight: 500; color: white; transition: 0.2s; }
        .btn:hover { transform: translateY(-1px); }
        .btn-blue { background: var(--primary); }
        .btn-danger { background: var(--danger); }
        .btn-nick { font-size: 11px; padding: 4px 10px; background: #9CA3AF; margin-left: 8px; border-radius: 20px; }

        .msg-table-container { border: 1px solid var(--border-color); border-radius: 8px; overflow-x: auto; }
        .msg-table { width: 100%; border-collapse: collapse; min-width: 600px; }
        .msg-table th { text-align: left; padding: 16px; background: #F9FAFB; color: var(--text-muted); font-weight: 600; font-size: 0.85rem; border-bottom: 1px solid var(--border-color); }
        .msg-table td { padding: 16px; border-bottom: 1px solid var(--border-color); vertical-align: top; font-size: 0.95rem; }
        .msg-row:hover { background: #F3F4F6; }
        .msg-body { word-break: break-all; line-height: 1.5; }
        .msg-time { color: var(--text-muted); font-size: 0.85em; width: 160px; }
        
        .pagination { margin-top: 24px; display: flex; gap: 12px; justify-content: center; align-items: center; }
        .page-btn { padding: 8px 16px; background: white; border: 1px solid #D1D5DB; border-radius: 6px; cursor: pointer; transition: 0.2s; }
        .page-btn:hover { border-color: var(--primary); color: var(--primary); }
        
        .divider { height: 32px; width: 1px; background: #E5E7EB; margin: 0 10px; }
        @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
    </style>
</head>
<body>

<div id="sidebar">
    <div class="sidebar-header">
        <span>🤖 QQBot 管理</span>
        <span class="user-tag"><?php echo htmlspecialchars($_SESSION['admin_user']); ?></span>
    </div>
    <div id="groupList"></div>
    <div class="logout-area">
        <button class="logout-btn" onclick="logout()">退出登录</button>
    </div>
</div>

<div id="content">
    <div id="welcome" class="card" style="text-align: center; color: #6B7280; padding: 80px 20px; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 50%;">
        <h3>👋 欢迎使用管理后台</h3>
        <p>请在左侧侧边栏选择一个群聊。</p>
    </div>

    <div id="mainUI" style="display: none;">
        <div class="card">
            <h2 id="viewGroupName">功能开关</h2>
            <div id="featureGrid" class="feature-grid"></div>
        </div>

        <div class="card">
            <h2>消息撤回与审计</h2>
            <div class="tools-panel">
                <div class="input-group">
                    <span>用户:</span>
                    <input type="text" id="wdUser" class="input-control" placeholder="QQ号" style="width:140px">
                </div>
                <div class="divider"></div>
                <div class="input-group">
                    <span>最近:</span>
                    <input type="number" id="wdCount" class="input-control" placeholder="条数" style="width:70px" value="10">
                </div>
                <div class="divider"></div>
                <div class="input-group" style="flex-direction: column; gap:4px;">
                    <div style="display:flex;align-items:center;gap:5px;">
                        <span style="font-size:10px;">FROM</span>
                        <input type="datetime-local" id="wdStart" class="input-control" style="font-size:11px;padding:4px;">
                    </div>
                    <div style="display:flex;align-items:center;gap:5px;">
                        <span style="font-size:10px;">TO</span>
                        <input type="datetime-local" id="wdEnd" class="input-control" style="font-size:11px;padding:4px;">
                    </div>
                </div>
                <div style="margin-left:auto; display:flex; gap:10px;">
                    <button class="btn btn-blue" onclick="batchWithdraw(false)">执行筛选撤回</button>
                    <button class="btn btn-danger" onclick="batchWithdraw(true)">撤回勾选</button>
                </div>
            </div>

            <div class="msg-table-container">
                <table class="msg-table">
                    <thead>
                        <tr>
                            <th width="40"><input type="checkbox" onclick="toggleAll(this)" style="cursor:pointer;"></th>
                            <th>用户信息</th>
                            <th>发送时间</th>
                            <th>内容预览</th>
                        </tr>
                    </thead>
                    <tbody id="msgList"></tbody>
                </table>
            </div>
            <div class="pagination">
                <button class="page-btn" onclick="changePage(-1)">← 上一页</button>
                <span id="pageLabel">第 1 页</span>
                <button class="page-btn" onclick="changePage(1)">下一页 →</button>
            </div>
        </div>
    </div>
</div>

<script>
    // 关键配置：后端数据接口地址 (群组、消息、功能开关)
    const DATA_API = "proxy.php?path=/api";
    
    let currentGid = null;
    let currentPage = 1;

    // 登出逻辑
    async function logout() {
        if(!confirm("确定要退出吗？")) return;
        try {
            await fetch('logout.php'); 
            location.reload(); 
        } catch(e) {
            alert('登出请求失败');
        }
    }

    // 加载群组列表
    async function loadGroups() {
        const list = document.getElementById('groupList');
        try {
            // 请求 DATA_API 而不是 api.php
            const response = await fetch(`${DATA_API}/groups`);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const groups = await response.json();
            
            list.innerHTML = '';
            groups.forEach(g => {
                const div = document.createElement('div');
                div.className = `group-item ${currentGid === g.groupId ? 'active' : ''}`;
                div.innerHTML = `
                    <span class="group-name">${g.groupName}</span>
                    <span class="group-id">${g.groupId}</span>
                `;
                div.onclick = () => {
                    document.querySelectorAll('.group-item').forEach(el => el.classList.remove('active'));
                    div.classList.add('active');
                    currentGid = g.groupId;
                    currentPage = 1;
                    selectGroup(g);
                };
                list.appendChild(div);
            });
        } catch (e) {
            console.error("Group Load Error:", e);
            list.innerHTML = `<div style="padding:20px; color:#ef4444; font-size:0.9rem;">
                加载失败<br>
                <span style="font-size:0.8em; opacity:0.8;">${e.message}</span><br>
                <span style="font-size:0.8em; opacity:0.8;">请检查 API 服务器是否运行</span>
            </div>`;
        }
    }

    // 选择群组
    function selectGroup(g) {
        document.getElementById('welcome').style.display = 'none';
        document.getElementById('mainUI').style.display = 'block';
        document.getElementById('viewGroupName').innerText = `${g.groupName} - 功能开关`;
        
        const grid = document.getElementById('featureGrid');
        grid.innerHTML = '';
        if (g.config) {
            Object.entries(g.config).forEach(([name, enabled]) => {
                const item = document.createElement('div');
                item.className = 'feature-item';
                item.innerHTML = `
                    <span style="font-weight:600; color:#444;">${name}</span>
                    <label class="switch">
                        <input type="checkbox" ${enabled ? 'checked' : ''} onchange="toggleFeature('${name}', this)">
                        <span class="slider"></span>
                    </label>`;
                grid.appendChild(item);
            });
        }
        
        loadMsgs();
        document.getElementById('wdStart').value = '';
        document.getElementById('wdEnd').value = '';
    }

    // 切换功能
    async function toggleFeature(featureName, checkbox) {
        try {
            await fetch(`${DATA_API}/toggle`, {
                method: 'POST',
                body: JSON.stringify({ groupId: currentGid, feature: featureName })
            });
        } catch (e) {
            alert('切换失败：网络错误');
            checkbox.checked = !checkbox.checked;
        }
    }

    // 加载消息
    async function loadMsgs() {
        const tbody = document.getElementById('msgList');
        try {
            const res = await fetch(`${DATA_API}/messages&groupId=${currentGid}&page=${currentPage}`);
            const msgs = await res.json();
            
            if (!msgs || !msgs.length) {
                tbody.innerHTML = '<tr><td colspan="4" style="text-align:center; padding:40px; color:#9CA3AF;">暂无消息记录</td></tr>';
                return;
            }

            tbody.innerHTML = '';
            msgs.forEach(m => {
                const tr = document.createElement('tr');
                tr.className = 'msg-row';
                const time = new Date(m.time * 1000).toLocaleString();
                tr.innerHTML = `
                    <td><input type="checkbox" class="msg-check" value="${m.id}" style="cursor:pointer;"></td>
                    <td class="msg-user">
                        <div style="font-weight:600; margin-bottom: 4px;">${m.userId}</div>
                        <button class="btn btn-nick" onclick="getNick(${m.userId}, this)">查昵称</button>
                    </td>
                    <td class="msg-time">${time}</td>
                    <td class="msg-body">${escapeHtml(m.message)}</td>`;
                tbody.appendChild(tr);
            });
            document.getElementById('pageLabel').innerText = `第 ${currentPage} 页`;
        } catch (e) {
            console.error("Msg Load Error:", e);
            tbody.innerHTML = '<tr><td colspan="4" style="text-align:center; padding:20px; color:#ef4444;">消息加载失败，请检查控制台</td></tr>';
        }
    }

    // 获取昵称
    async function getNick(uid, btn) {
        try {
            btn.innerText = "...";
            const res = await (await fetch(`${DATA_API}/nickname?userId=${uid}`)).json();
            btn.innerText = res.nickname || "未知";
            btn.style.background = "#10B981";
            btn.onclick = null;
        } catch (e) {
            btn.innerText = "失败";
            btn.style.background = "#EF4444";
        }
    }

    // 翻页
    function changePage(delta) {
        if (currentPage + delta < 1) return;
        currentPage += delta;
        loadMsgs();
    }

    // 全选
    function toggleAll(src) {
        document.querySelectorAll('.msg-check').forEach(c => c.checked = src.checked);
    }

    // 批量撤回
    async function batchWithdraw(useSelected) {
        let payload = { groupId: currentGid };
        
        if (useSelected) {
            const ids = Array.from(document.querySelectorAll('.msg-check:checked')).map(c => parseInt(c.value));
            if (!ids.length) return alert('请先勾选要撤回的消息');
            payload.messageIds = ids;
        } else {
            const user = document.getElementById('wdUser').value.trim();
            if (user) payload.userId = user;

            const startVal = document.getElementById('wdStart').value;
            const endVal = document.getElementById('wdEnd').value;
            const count = parseInt(document.getElementById('wdCount').value);

            if (startVal && endVal) {
                const sTime = Math.floor(new Date(startVal).getTime() / 1000);
                const eTime = Math.floor(new Date(endVal).getTime() / 1000);
                if (sTime >= eTime) return alert('结束时间必须晚于开始时间');
                payload.startTime = sTime;
                payload.endTime = eTime;
                if(!confirm(`即将撤回 [${startVal} 到 ${endVal}] 期间的消息，确定继续？`)) return;
            } else {
                if (!count || count <= 0) return alert('请输入有效的条数');
                payload.limit = count;
                if(!confirm(`即将撤回最近 ${count} 条消息，确定继续？`)) return;
            }
        }

        try {
            const res = await (await fetch(`${DATA_API}/withdraw`, { method:'POST', body:JSON.stringify(payload) })).json();
            alert(`成功撤回 ${res.count || 0} 条消息。`);
            loadMsgs(); 
        } catch (e) {
            alert('撤回请求失败，可能权限不足或网络问题');
        }
    }

    function escapeHtml(text) {
        if (!text) return "";
        return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
    }

    // 初始化加载
    loadGroups();
</script>
</body>
</html>
