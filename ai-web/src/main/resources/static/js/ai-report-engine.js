
var API_BASE = '/api';
var currentData = {};
var progressDotsTimer = null;
var onCount = 1;

function buildAnimatedChars(text, className) {
  return Array.from(text || '').map(function(char, index) {
    var safeChar = char === ' ' ? '&nbsp;' : escHtml(char);
    return '<span class="' + className + '" style="--char-index:' + index + '">' + safeChar + '</span>';
  }).join('');
}

function setAnimatedHeadlineText(text) {
  var el = document.getElementById('progressHeadlineText');
  if (!el) return;
  el.classList.add('wave');
  el.dataset.text = text || '';
  el.innerHTML = buildAnimatedChars(text || '', 'headline-char');
}

/* === Navigation === */
function showDetail() {
  document.getElementById('listPage').classList.add('hidden');
  document.getElementById('detailPage').classList.add('active');
  document.getElementById('breadCur').textContent = '销售健康度日报 · 规则配置';
}
function showList() {
  document.getElementById('listPage').classList.remove('hidden');
  document.getElementById('detailPage').classList.remove('active');
  document.getElementById('breadCur').textContent = '智能报告中心';
}

/* === Card Toggle === */
function toggleCard(btn, idx) {
  var isOn = btn.classList.contains('on');
  btn.classList.toggle('on');
  btn.classList.toggle('off');
  onCount += isOn ? -1 : 1;
  var meta = document.getElementById('meta' + idx);
  if (idx === 0) {
    if (!isOn) {
      meta.className = 'rc-meta active-meta';
      meta.textContent = '已开启 · 09:00 推送';
    } else {
      meta.className = 'rc-meta';
      meta.textContent = '5 项指标 · 8 条规则';
    }
  }
  document.getElementById('topBadge').textContent = onCount + ' 张报告已开启';
  document.getElementById('gridFooter').textContent = '当前已开启 ' + onCount + ' / 4 张报告卡片 · 更多场景即将上线';
}

/* === Push Settings === */
function selTime(el) {
  var opts = document.querySelectorAll('#timeOpts .opt');
  for (var i = 0; i < opts.length; i++) opts[i].classList.remove('sel-time');
  el.classList.add('sel-time');
}
function toggleCh(el) {
  el.classList.toggle('sel-ch');
}

/* === Demo Data === */
var demos = {
  green: {
    label: '绿色场景',
    todayRevenue: 12800, yesterdayRevenue: 9600,
    todayOrders: 72, yesterdayOrders: 55,
    todayRisingLinks: 110, yesterdayRisingLinks: 95,
    todayFallingLinks: 20, yesterdayFallingLinks: 28,
    todayNoOrderLinks: 8, yesterdayNoOrderLinks: 14,
    r1Threshold: 20, r2Threshold: 30, r3Threshold: 30,
    y1Threshold: 10, g1Threshold: 10, g2Threshold: 40
  },
  mixed: {
    label: '混合场景',
    todayRevenue: 7800, yesterdayRevenue: 8860,
    todayOrders: 45, yesterdayOrders: 48,
    todayRisingLinks: 50, yesterdayRisingLinks: 57,
    todayFallingLinks: 55, yesterdayFallingLinks: 49,
    todayNoOrderLinks: 35, yesterdayNoOrderLinks: 31,
    r1Threshold: 20, r2Threshold: 30, r3Threshold: 30,
    y1Threshold: 10, g1Threshold: 10, g2Threshold: 40
  },
  red: {
    label: '红色场景',
    todayRevenue: 5500, yesterdayRevenue: 8100,
    todayOrders: 28, yesterdayOrders: 43,
    todayRisingLinks: 20, yesterdayRisingLinks: 38,
    todayFallingLinks: 90, yesterdayFallingLinks: 61,
    todayNoOrderLinks: 75, yesterdayNoOrderLinks: 46,
    r1Threshold: 20, r2Threshold: 30, r3Threshold: 30,
    y1Threshold: 10, g1Threshold: 10, g2Threshold: 40
  }
};

function getRuleValues() {
  return {
    r1Threshold: +document.getElementById('r1Threshold').value || 20,
    r2Threshold: +document.getElementById('r2Threshold').value || 30,
    r3Threshold: +document.getElementById('r3Threshold').value || 30,
    y1Threshold: +document.getElementById('y1Threshold').value || 10,
    g1Threshold: +document.getElementById('g1Threshold').value || 10,
    g2Threshold: +document.getElementById('g2Threshold').value || 40
  };
}

function showLoading(show) {
  var placeholder = document.getElementById('resultPlaceholder');
  var progress = document.getElementById('resultProgress');
  placeholder.style.display = show ? 'none' : 'flex';
  progress.classList.toggle('show', show);
  if (show) {
    document.getElementById('resultContent').classList.remove('show');
    resetProgress();
    var date = currentData.reportDate || new Date().toISOString().slice(0,10);
    var el = document.getElementById('progressDataDate');
    if (el) el.textContent = date;
    startProgressHeadlineAnimation();
  } else {
    stopProgressHeadlineAnimation();
  }
}

function fmtNum(n) {
  return (n != null ? Number(n).toLocaleString('zh-CN') : '-');
}

function updateDataDisplay(d) {
  if (!d) {
    document.getElementById('dispDate').textContent = '-';
    document.getElementById('dispTodayRev').textContent = '-';
    document.getElementById('dispYestRev').textContent = '-';
    document.getElementById('dispTodayOrd').textContent = '-';
    document.getElementById('dispYestOrd').textContent = '-';
    document.getElementById('dispRising').textContent = '-';
    document.getElementById('dispYestRising').textContent = '-';
    document.getElementById('dispFalling').textContent = '-';
    document.getElementById('dispYestFalling').textContent = '-';
    document.getElementById('dispNoOrder').textContent = '-';
    document.getElementById('dispYestNoOrder').textContent = '-';
    return;
  }
  document.getElementById('dispDate').textContent = d.reportDate || new Date().toISOString().slice(0,10);
  document.getElementById('dispTodayRev').textContent = '¥' + fmtNum(d.todayRevenue);
  document.getElementById('dispYestRev').textContent = '¥' + fmtNum(d.yesterdayRevenue);
  document.getElementById('dispTodayOrd').textContent = fmtNum(d.todayOrders);
  document.getElementById('dispYestOrd').textContent = fmtNum(d.yesterdayOrders);
  document.getElementById('dispRising').textContent = fmtNum(d.todayRisingLinks);
  document.getElementById('dispYestRising').textContent = fmtNum(d.yesterdayRisingLinks);
  document.getElementById('dispFalling').textContent = fmtNum(d.todayFallingLinks);
  document.getElementById('dispYestFalling').textContent = fmtNum(d.yesterdayFallingLinks);
  document.getElementById('dispNoOrder').textContent = fmtNum(d.todayNoOrderLinks);
  document.getElementById('dispYestNoOrder').textContent = fmtNum(d.yesterdayNoOrderLinks);
}

async function loadDemo(key) {
  var demo = demos[key];
  if (!demo) return;
  var today = new Date().toISOString().slice(0,10);
  currentData = { reportDate: today };
  for (var k in demo) { currentData[k] = demo[k]; }
  updateDataDisplay(demo);
  try {
    await fetch(API_BASE + '/smart-report/config/save', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(currentData)
    });
  } catch(e) {}
  showToast('已加载' + demo.label);
}

function resetDefaults() {
  document.getElementById('r1Threshold').value = 20;
  document.getElementById('r2Threshold').value = 30;
  document.getElementById('r3Threshold').value = 30;
  document.getElementById('y1Threshold').value = 10;
  document.getElementById('g1Threshold').value = 10;
  document.getElementById('g2Threshold').value = 40;
  showToast('已恢复默认规则阈值');
}

async function loadConfig() {
  try {
    var ruleResp = await fetch(API_BASE + '/smart-report/rule-config/load');
    if (ruleResp.ok) {
      var ruleData = await ruleResp.json();
      if (ruleData.code === 200 && ruleData.data) {
        var r = ruleData.data;
        if (r.r1Threshold != null) document.getElementById('r1Threshold').value = r.r1Threshold;
        if (r.r2Threshold != null) document.getElementById('r2Threshold').value = r.r2Threshold;
        if (r.r3Threshold != null) document.getElementById('r3Threshold').value = r.r3Threshold;
        if (r.y1Threshold != null) document.getElementById('y1Threshold').value = r.y1Threshold;
        if (r.g1Threshold != null) document.getElementById('g1Threshold').value = r.g1Threshold;
        if (r.g2Threshold != null) document.getElementById('g2Threshold').value = r.g2Threshold;
      }
    }
    var today = new Date().toISOString().slice(0,10);
    var bizResp = await fetch(API_BASE + '/smart-report/config/load?date=' + today);
    if (bizResp.ok) {
      var bizData = await bizResp.json();
      if (bizData.code === 200 && bizData.data && bizData.data.todayRevenue != null) {
        currentData = {};
        for (var bk in bizData.data) { currentData[bk] = bizData.data[bk]; }
        updateDataDisplay(bizData.data);
      }
    }
  } catch (e) {
    console.log('加载配置失败（首次或无数据库记录属于正常）');
  }
}

async function saveConfig() {
  var rules = getRuleValues();
  try {
    var resp = await fetch(API_BASE + '/smart-report/rule-config/save', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(rules)
    });
    if (!resp.ok) throw new Error('HTTP ' + resp.status);
    var data = await resp.json();
    if (data.code === 200) {
      showToast('规则配置已保存到数据库');
    } else {
      throw new Error(data.message || '保存失败');
    }
  } catch (e) {
    showToast('保存失败：' + e.message, true);
  }
}

async function analyze() {
  var btn = document.getElementById('analyzeBtn');
  var rules = getRuleValues();

  if (currentData.todayRevenue == null) {
    var demo = demos.green;
    var today = new Date().toISOString().slice(0,10);
    currentData = { reportDate: today };
    for (var k in demo) { currentData[k] = demo[k]; }
    updateDataDisplay(demo);
  }

  var requestBody = {};
  for (var k in currentData) { requestBody[k] = currentData[k]; }
  for (var k in rules) { requestBody[k] = rules[k]; }

  document.getElementById('diagnosisConclusion').innerHTML = '';
  document.getElementById('operationDiagnosis').innerHTML = '';

  showLoading(true);
  updateProgress('loading', '正在加载业务数据...');
  btn.disabled = true;
  btn.textContent = '分析中...';
  document.querySelector('.content').classList.add('blur-active');

  var mainEl = document.querySelector('.main');
  if (mainEl) mainEl.scrollTop = 0;
  document.documentElement.scrollTop = 0;

  try {
    var resp = await fetch(API_BASE + '/smart-report/analyze-stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(requestBody)
    });
    if (!resp.ok) throw new Error('HTTP ' + resp.status);
    if (!resp.body) throw new Error('浏览器不支持流式读取');

    var reader = resp.body.getReader();
    var decoder = new TextDecoder();
    var buffer = '';
    var resultData = null;

    while (true) {
      var r = await reader.read();
      if (r.done) break;
      buffer += decoder.decode(r.value, { stream: true });
      var parts = buffer.split('\n\n');
      buffer = parts.pop() || '';

      for (var pi = 0; pi < parts.length; pi++) {
        var part = parts[pi];
        var lines = part.split('\n');
        var eventType = 'message';
        var dataStr = '';

        for (var li = 0; li < lines.length; li++) {
          var line = lines[li];
          if (line.startsWith('event:')) eventType = line.slice(6).trim();
          else if (line.startsWith('data:')) dataStr = line.slice(5).trim();
        }
        if (!dataStr) continue;

        if (eventType === 'progress') {
          var payload = JSON.parse(dataStr);
          updateProgress(payload.step, payload.message);
        } else if (eventType === 'result') {
          resultData = JSON.parse(dataStr);
        }
      }
    }

    if (resultData) {
      await new Promise(function(r) { setTimeout(r, 600); });
      renderResult(resultData);
    } else {
      throw new Error('未获取到分析结果');
    }
  } catch (e) {
    showToast('请求失败：' + e.message, true);
    renderLocalFallback(rules);
  } finally {
    btn.disabled = false;
    btn.textContent = '开始分析';
    var cc = document.querySelector('.content');
    if (cc) cc.classList.remove('blur-active');
  }
}

function updateProgress(step, message) {
  var order = ['loading','ai_analysis','parsing','complete'];
  var idx = order.indexOf(step);
  if (idx < 0) return;
  var items = document.querySelectorAll('.step-item');
  var headlineMap = {
    loading: '正在整理业务数据',
    ai_analysis: 'AI 正在分析诊断数据',
    parsing: '正在解析与排版结果',
    complete: '分析已完成，准备展示'
  };
  setAnimatedHeadlineText(message || headlineMap[step] || '');
  for (var i = 0; i < items.length; i++) {
    var el = items[i];
    el.classList.remove('done', 'active', 'pending');
    if (i < idx) el.classList.add('done');
    else if (i === idx) el.classList.add('active');
    else el.classList.add('pending');
    var desc = el.querySelector('.step-desc');
    var state = el.querySelector('.step-state');
    if (desc) {
      if (i === idx && message) desc.textContent = message;
      else if (i < idx) desc.textContent = '完成';
      else desc.textContent = '';
    }
    if (state) state.textContent = i < idx ? 'DONE' : i === idx ? 'NOW' : 'WAIT';
  }
}

function resetProgress() {
  var items = document.querySelectorAll('.step-item');
  for (var i = 0; i < items.length; i++) {
    var el = items[i];
    el.classList.remove('done', 'active');
    el.classList.add('pending');
    var state = el.querySelector('.step-state');
    if (state) state.textContent = 'WAIT';
    var desc = el.querySelector('.step-desc');
    if (desc) desc.textContent = '';
  }
  var first = document.querySelector('.step-item');
  if (first) { first.classList.remove('pending'); first.classList.add('active'); }
  setAnimatedHeadlineText('正在整理业务数据并生成诊断结论');
}

function startProgressHeadlineAnimation() {
  var dots = document.getElementById('progressHeadlineDots');
  if (!dots) return;
  stopProgressHeadlineAnimation();
  var frames = ['.', '..', '...'];
  var i = 0;
  dots.textContent = frames[0];
  progressDotsTimer = setInterval(function() {
    i = (i + 1) % frames.length;
    dots.textContent = frames[i];
  }, 420);
}

function stopProgressHeadlineAnimation() {
  if (progressDotsTimer) {
    clearInterval(progressDotsTimer);
    progressDotsTimer = null;
  }
  var dots = document.getElementById('progressHeadlineDots');
  if (dots) dots.textContent = '...';
}

function renderResult(data) {
  var now = new Date().toLocaleString('zh-CN');
  var resultContent = document.getElementById('resultContent');
  document.getElementById('resultTime').textContent = now;
  document.getElementById('diagnosisConclusion').innerHTML = formatDiagnosisConclusion(data['诊断结论']);
  document.getElementById('operationDiagnosis').innerHTML = formatOperationDiagnosis(data['运营诊断']);
  stopProgressHeadlineAnimation();
  document.getElementById('resultProgress').classList.remove('show');
  resultContent.classList.remove('show');
  void resultContent.offsetWidth;
  resultContent.classList.add('show');
}

function formatDiagnosisConclusion(dc) {
  if (!dc || typeof dc !== 'object') return '<div class="dc-empty">诊断结论未返回</div>';
  var groups = [
    { key: '立即关注', cls: 'dc-red', label: '立即关注' },
    { key: '值得注意', cls: 'dc-amber', label: '值得注意' },
    { key: '运营亮点', cls: 'dc-green', label: '运营亮点' }
  ];
  return groups.map(function(g) {
    var items = dc[g.key];
    var hasItems = Array.isArray(items) && items.length;
    var criticalClass = g.key === '立即关注' && hasItems ? ' critical-hit' : '';
    var alertTag = g.key === '立即关注' && hasItems
      ? '<span class="dc-alert-tag">立即处理</span>' : '';
    return '<div class="dc-section ' + g.cls + criticalClass + '">' +
      '<div class="dc-header"><span class="dc-dot"></span>' + g.label + alertTag +
      '<span class="dc-badge">' + (hasItems ? items.length + ' 条' : '') + '</span></div>' +
      (hasItems
        ? '<ul class="dc-list">' + items.map(function(i) { return '<li>' + escHtml(i) + '</li>'; }).join('') + '</ul>'
        : '<div class="dc-empty">无命中规则</div>') +
      '</div>';
  }).join('');
}

function formatOperationDiagnosis(od) {
  if (!od || typeof od !== 'object') return '<div class="od-card"><div class="od-title">运营诊断未返回</div></div>';
  var sections = [
    { key: '整体表现', emoji: '📊', label: '整体表现' },
    { key: '链接结构分析', emoji: '🔗', label: '链接结构分析', table: true },
    { key: '异常逻辑判断', emoji: '⚠️', label: '异常逻辑判断', plain: true },
    { key: '运营建议', emoji: '💡', label: '运营建议', suggestion: true, markdown: true }
  ];
  return '<div class="od-section">' + sections.map(function(s) {
    var text = od[s.key];
    if (!text || !text.trim()) return '';
    var content;
    if (s.table) content = renderTableContent(text);
    else if (s.plain) content = renderPlainContent(text);
    else if (s.markdown) content = renderMarkdownListContent(text);
    else content = renderListContent(text);
    if (s.suggestion) {
      return '<div class="od-suggestion"><div class="od-title"><span class="emoji">' + s.emoji + '</span>' + s.label + '</div>' + content + '</div>';
    }
    return '<div class="od-card"><div class="od-title"><span class="emoji">' + s.emoji + '</span>' + s.label + '</div>' + content + '</div>';
  }).join('') + '</div>';
}

function renderTableContent(text) {
  var lines = text.split('\n').map(function(l) { return l.trim(); }).filter(function(l) { return l.length > 0; });
  var html = '';
  var inTable = false;
  for (var i = 0; i < lines.length; i++) {
    var line = lines[i];
    if (line.startsWith('|') && line.endsWith('|')) {
      if (/^\|[-:\s|]+\|$/.test(line)) continue;
      if (!inTable) { html += '<table class="od-table"><thead><tr>'; inTable = true; }
      var cells = line.split('|').filter(function(c) { return c.trim() !== ''; });
      if (html.indexOf('</thead>') >= 0) {
        html += '<tr>' + cells.map(function(c) { return '<td>' + escHtml(c.trim()) + '</td>'; }).join('') + '</tr>';
      } else {
        html += cells.map(function(c) { return '<th>' + escHtml(c.trim()) + '</th>'; }).join('');
        html += '</tr></thead><tbody>';
      }
    } else {
      if (inTable) { html += '</tbody></table>'; inTable = false; }
      html += '<div class="od-table-note">' + escHtml(line) + '</div>';
    }
  }
  if (inTable) html += '</tbody></table>';
  return html;
}

function renderListContent(text) {
  var lines = text.split('\n').map(function(l) { return l.trim(); }).filter(function(l) { return l.length > 0; });
  return '<ul class="od-list">' + lines.map(function(l) { return '<li>' + escHtml(l) + '</li>'; }).join('') + '</ul>';
}

function renderPlainContent(text) {
  var lines = text.split('\n').map(function(l) { return l.trim(); }).filter(function(l) { return l.length > 0; });
  return lines.map(function(l) { return '<div class="od-plain-item">' + escHtml(l) + '</div>'; }).join('');
}

function renderMarkdownListContent(text) {
  var lines = text.split('\n').map(function(l) { return l.trim(); }).filter(function(l) { return l.length > 0; });
  return '<ul class="od-list">' + lines.map(function(l) { return '<li>' + renderInlineMarkdown(escHtml(l)) + '</li>'; }).join('') + '</ul>';
}

function renderInlineMarkdown(text) {
  return text.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
}

function escHtml(str) {
  var d = document.createElement('div');
  d.textContent = str;
  return d.innerHTML;
}

function formatPercentTrend(num) {
  return (num >= 0 ? '↑' : '↓') + Math.abs(num).toFixed(2) + '%';
}

function renderLocalFallback(rules) {
  var fallbackData = {};
  fallbackData['诊断结论'] = {
    '立即关注': [],
    '值得注意': ['无法连接服务器，请检查服务是否已启动'],
    '运营亮点': []
  };
  fallbackData['运营诊断'] = {
    '整体表现': '服务暂不可用，无法获取运营诊断数据。',
    '链接结构分析': '请确认后端服务已启动，且数据库中有业务数据。',
    '异常逻辑判断': '当前显示为离线降级信息，非真实分析结果。',
    '运营建议': '✅ 请检查服务状态后重新尝试分析。'
  };
  renderResult(fallbackData);
}

function showToast(msg, isError) {
  var t = document.getElementById('toast');
  t.textContent = msg;
  t.className = 'toast show' + (isError ? ' error' : '');
  setTimeout(function() { t.className = 'toast'; }, 2800);
}

async function loadHistoryData() {
  var dateInput = document.getElementById('historyDate');
  var date = dateInput.value;
  if (!date) { showToast('请选择日期', true); return; }
  try {
    var resp = await fetch(API_BASE + '/smart-report/config/load?date=' + date);
    if (!resp.ok) throw new Error('HTTP ' + resp.status);
    var data = await resp.json();
    if (data.code === 200 && data.data && data.data.todayRevenue != null) {
      currentData = { reportDate: date };
      for (var k in data.data) { currentData[k] = data.data[k]; }
      updateDataDisplay(data.data);
      showToast('已加载 ' + date + ' 的历史数据');
    } else {
      showToast(date + ' 无历史数据', true);
    }
  } catch (e) {
    showToast('加载失败：' + e.message, true);
  }
}

// init
setAnimatedHeadlineText('正在整理业务数据并生成诊断结论');
loadConfig().then(function() {
  if (currentData.todayRevenue == null) {
    loadDemo('green');
  }
});
