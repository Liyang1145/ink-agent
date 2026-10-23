#!/usr/bin/env python3
"""InkAgent v0.4 end-to-end scenario on an Android emulator (pixel_c, landscape).

Covers: launch into a note; finger strokes; area eraser splitting a stroke; unified 选择 tool (lasso,
long-press → rectangle); moving a selection; 加入 Agent → attachment tray without sending; two real
DeepSeek turns with canvas tools; footer stats; session sidebar; collapsed agent (notes fullscreen);
provider settings. Writes screenshots + report + session log into e2e-out/.
"""
import os, re, subprocess, sys, time, json, base64
import xml.etree.ElementTree as ET

OUT = 'e2e-out'; os.makedirs(OUT, exist_ok=True)
PKG = 'com.inkagent'
KEY = os.environ.get('DS_KEY', '')
SCEN = os.environ.get('SCENARIO', 'full')
APK = os.environ.get('APK', 'app/build/outputs/apk/debug/app-debug.apk')
shots = 0
report = []
checks = []

def sh(cmd, check=False, timeout=60):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    if check and r.returncode != 0: raise RuntimeError(cmd + '\n' + r.stderr)
    return r.stdout

def adb(a, **k): return sh('adb ' + a, **k)

def note(msg):
    print(msg, flush=True); report.append(msg)

def check(name, ok, detail=''):
    checks.append((name, bool(ok), detail))
    note(f"CHECK {'PASS' if ok else 'FAIL'} {name} {detail}")

def shot(name):
    global shots
    shots += 1
    fn = f'{OUT}/{shots:02d}_{name}.png'
    subprocess.run(f'adb exec-out screencap -p > {fn}', shell=True)
    note(f'screenshot {fn}')
    return fn

def raw_screen():
    """(w, h, bytes RGBA) from `screencap` raw output."""
    data = subprocess.run('adb exec-out screencap', shell=True, capture_output=True).stdout
    w = int.from_bytes(data[0:4], 'little'); h = int.from_bytes(data[4:8], 'little')
    hdr = len(data) - w * h * 4
    return w, h, data[hdr:]

def dark_near(scr, x, y, r=8):
    w, h, px = scr
    best = 999
    for yy in range(max(0, y - r), min(h, y + r + 1)):
        i = (yy * w + x) * 4
        r_, g_, b_ = px[i], px[i + 1], px[i + 2]
        # ink = dark (default pen is black), or clearly coloured
        if r_ + g_ + b_ < 330 or max(r_, g_, b_) - min(r_, g_, b_) > 90: return True
    return False

def dump():
    adb('shell uiautomator dump /sdcard/ui.xml', timeout=40)
    adb(f'pull /sdcard/ui.xml {OUT}/ui.xml')
    try: return ET.parse(f'{OUT}/ui.xml').getroot()
    except Exception as e: note(f'ui dump failed {e}'); return None

def nodes(pred):
    root = dump()
    out = []
    if root is None: return out
    for n in root.iter('node'):
        if pred(n):
            b = [int(v) for v in re.findall(r'\d+', n.get('bounds'))]
            out.append(((b[0] + b[2]) // 2, (b[1] + b[3]) // 2, b, n))
    return out

def find(pred):
    r = nodes(pred)
    return r[0] if r else None

def lab(n): return (n.get('text') or '') + '|' + (n.get('content-desc') or '')

def by_label(s, exact=False):
    if exact: return find(lambda n: (n.get('text') or '') == s or (n.get('content-desc') or '') == s)
    return find(lambda n: s in lab(n))

def all_text():
    root = dump()
    if root is None: return ''
    return '\n'.join(lab(n) for n in root.iter('node'))

def tap(x, y): adb(f'shell input tap {x} {y}')

def tap_label(s, required=False, exact=False):
    p = by_label(s, exact)
    if not p:
        note(f'!! element not found: {s}')
        if required: raise SystemExit(1)
        return False
    tap(p[0], p[1]); return True

def select_tool(name):
    """Tap a toolbar tool; if that opened its options popup (re-tap of the active tool), close it."""
    tap_label(name, exact=True); time.sleep(0.8)
    t = all_text()
    if t and '撤销' not in t:  # popup window covers the dump -> toolbar not visible
        note(f'closing {name} options popup'); adb('shell input keyevent 4'); time.sleep(0.8)

def stroke(pts, tool='stylus'):
    """One gesture through the debug TEST_INPUT receiver (DOWN/MOVE/UP, +16 ms per point)."""
    p = ';'.join(f'{int(x)},{int(y)}' for (x, y) in pts)
    adb(f"shell \"am broadcast -a com.inkagent.TEST_INPUT -p {PKG} --es pts '{p}' --es tool {tool}\"")
    time.sleep(len(pts) * 0.016 + 0.3)

def set_composer(t):
    b = base64.b64encode(t.encode('utf-8')).decode()
    adb(f"shell am broadcast -a com.inkagent.TEST_INPUT -p {PKG} --es text_b64 {b}")
    time.sleep(1)

def state(tag=''):
    """Panel state from agent.html via the debug TEST_STATE receiver (logcat tag InkAgentState)."""
    adb(f'shell am broadcast -a com.inkagent.TEST_STATE -p {PKG}')
    for _ in range(6):
        time.sleep(1)
        lines = [l for l in adb('logcat -d -s InkAgentState:I', timeout=30).splitlines() if '{' in l]
        if lines:
            try:
                st = json.loads(lines[-1][lines[-1].index('{'):])
                note(f'state[{tag}]: {st}'); return st
            except Exception as e: note(f'state parse failed {e}: {lines[-1]}')
    note(f'!! no InkAgentState line [{tag}]'); return {}

def line(x1, y1, x2, y2, n=10):
    return [(int(x1 + (x2 - x1) * i / n), int(y1 + (y2 - y1) * i / n)) for i in range(n + 1)]

def session_text():
    return adb(f"shell \"run-as {PKG} sh -c 'cat files/sessions/*.jsonl' 2>/dev/null\"", timeout=60)

def count_events(txt, t):
    return txt.count(f'"type":"{t}"') + txt.count('"type":"' + t.replace('/', '\\/') + '"')

def wait_turn_end(n_expected, limit=300, every=20, tag='wait'):
    t0 = time.time(); k = 0
    while time.time() - t0 < limit:
        time.sleep(5)
        txt = session_text()
        if count_events(txt, 'turn/end') >= n_expected: return txt
        if time.time() - t0 > (k + 1) * every:
            k += 1; shot(f'{tag}_{k}')
    note('!! turn did not finish in time'); return session_text()

def type_text(t):
    words = t.split(' ')
    for i, w in enumerate(words):
        adb("shell input text '" + (w + ('%s' if i < len(words) - 1 else '')).replace("'", '') + "'")
        time.sleep(0.25)

def launch(extra=''):
    out = adb(f'shell am start -W -n {PKG}/com.xnotes.MainActivity {extra}', timeout=90)
    note('am start: ' + ' | '.join(l for l in out.splitlines() if l.strip() and 'test_key' not in l)[:300])
    time.sleep(3)
    pid = adb(f'shell pidof {PKG}').strip()
    note('pid: ' + (pid or 'NOT RUNNING'))
    if not pid: note('crash buffer:\n' + adb('logcat -d -b crash', timeout=30)[-4000:])

def focus():
    out = adb('shell dumpsys window', timeout=60)
    m = re.search(r'mCurrentFocus=.*', out)
    return m.group(0) if m else '?'

def dismiss_dialogs():
    for _ in range(3):
        f = focus()
        if 'ImmersiveModeConfirmation' in f:
            note('dismissing immersive-mode confirmation')
            if not (tap_label('Got it') or tap_label('知道了')): adb('shell input keyevent 4')
            time.sleep(2); continue
        if 'Not Responding' not in f and 'Application Error' not in f: return
        note('dismissing system dialog: ' + f)
        if not (tap_label('Wait') or tap_label('Close app') or tap_label('等待') or tap_label('关闭应用')):
            adb('shell input keyevent 4')
        time.sleep(2)

def ensure_front(extra=''):
    for i in range(4):
        dismiss_dialogs()
        f = focus(); note('focus: ' + f)
        if PKG in f: return True
        launch(extra); time.sleep(8)
    note('!! app never reached the foreground'); return False

def composer():
    return find(lambda n: n.get('class') == 'android.widget.EditText' and ('消息' in lab(n) or 'input' in (n.get('resource-id') or '')))\
        or find(lambda n: n.get('class') == 'android.widget.EditText')

def main():
    note('wm size: ' + adb('shell wm size').strip().replace('\n', ' | '))
    adb(f'install -r -g {APK}', check=True, timeout=240)
    adb('shell settings put system accelerometer_rotation 0')
    adb('shell settings put system user_rotation 0')
    adb('logcat -c')
    adb('shell settings put global hide_error_dialogs 1')
    adb('shell settings put global anr_show_background 0')
    adb('shell settings put secure immersive_mode_confirmations confirmed')
    time.sleep(40)
    dismiss_dialogs()
    adb('shell input keyevent 82'); adb('shell input keyevent 3')
    time.sleep(5)
    ex = f'--es test_key {KEY} --ez test_finger true --es test_mode compact --es test_effort high --ez test_collapsed false'
    launch(ex)
    time.sleep(12)
    if not ensure_front(ex): raise SystemExit(1)
    time.sleep(3)
    shot('launch')
    W, H, _ = raw_screen()  # actual orientation as shown (the app requests landscape in test mode)
    note(f'screen {W}x{H} (from screenshot)')
    check('launch: landscape', W > H, f'{W}x{H}')
    txt = all_text()
    check('launch: toolbar shows 笔/荧光笔/橡皮/选择/撤销/重做/更多',
          all(k in txt for k in ['笔', '荧光笔', '橡皮', '选择', '撤销', '重做', '更多']))
    st = state('launch')
    check('launch: agent panel visible', st.get('panelVisible') is True, str(st))

    # notes area = left of the agent pane; find the drag handle to know where it ends
    h = by_label('拖动调整 Agent 宽度')
    notes_right = h[2][0] if h else int(W * 0.62)
    note(f'notes area right edge x={notes_right}')
    cx0 = int(notes_right * 0.12)
    oy, s = int(H * 0.30), int(H * 0.06)

    # ---------------- finger strokes: "x^2 = 4" + a long line for the eraser ----------------
    select_tool('笔')
    ox = cx0
    stroke(line(ox, oy, ox + s, oy + s))
    stroke(line(ox + s, oy, ox, oy + s))
    stroke([(ox + s + 10, oy - 5), (ox + s + 25, oy - 25), (ox + s + 40, oy - 5), (ox + s + 12, oy + 10), (ox + s + 45, oy + 10)])
    stroke(line(ox + 2 * s, oy + s // 3, ox + 3 * s, oy + s // 3))
    stroke(line(ox + 2 * s, oy + 2 * s // 3, ox + 3 * s, oy + 2 * s // 3))
    stroke(line(ox + 3 * s + 40, oy, ox + 3 * s + 10, oy + s * 2 // 3))
    stroke(line(ox + 3 * s + 10, oy + s * 2 // 3, ox + 3 * s + 60, oy + s * 2 // 3))
    stroke(line(ox + 3 * s + 45, oy + s // 4, ox + 3 * s + 45, oy + s + 10))
    ly = int(H * 0.62); lx1 = cx0; lx2 = int(notes_right * 0.75)
    stroke(line(lx1, ly, lx2, ly, 24))
    time.sleep(1.5)
    shot('strokes')
    scr = raw_screen()
    check('strokes: ink drawn', dark_near(scr, (lx1 + lx2) // 2, ly) and dark_near(scr, ox + s // 2, oy + s // 2))

    # ---------------- area eraser cuts the long line in the middle ----------------
    select_tool('橡皮')
    mx = (lx1 + lx2) // 2
    stroke(line(mx, ly - 60, mx, ly + 60, 12))
    stroke(line(mx + 8, ly + 60, mx + 8, ly - 60, 12))
    time.sleep(1.5)
    shot('area_erased')
    scr = raw_screen()
    left = dark_near(scr, lx1 + (mx - lx1) // 3, ly); right = dark_near(scr, mx + (lx2 - mx) * 2 // 3, ly)
    mid = dark_near(scr, mx + 4, ly, r=4)
    check('eraser: partial erase (ink left & right of cut, gap in middle)', left and right and not mid,
          f'left={left} right={right} middle_ink={mid}')

    # ---------------- 选择: lasso (default) around the formula, move it ----------------
    select_tool('选择')
    m = 40
    x1, y1, x2, y2 = ox - m, oy - m - 30, ox + 4 * s + m, oy + s + m
    loop = line(x1, y1, x2, y1) + line(x2, y1, x2, y2) + line(x2, y2, x1, y2) + line(x1, y2, x1, y1)
    stroke(loop)
    time.sleep(2)
    shot('lasso_selected')
    txt = all_text()
    check('lasso: selection menu shown (复制/剪切/删除/改颜色/加入 Agent)',
          all(k in txt for k in ['复制', '剪切', '删除', '改颜色', '加入 Agent']))
    # move: drag from inside the selection
    sx, sy = ox + s // 2, oy + s // 2
    stroke([(sx, sy)] * 3 + line(sx, sy, sx + 120, sy + 140, 40) + [(sx + 120, sy + 140)] * 3)
    time.sleep(2)
    shot('selection_moved')
    scr = raw_screen()
    check('move: formula moved', dark_near(scr, sx + 120, sy + 140, r=20))
    # 加入 Agent
    before = count_events(session_text(), 'user/message')
    ok = tap_label('加入 Agent')
    time.sleep(2.5)
    shot('added_to_tray')
    after_txt = session_text()
    check('加入 Agent: tapped', ok)
    check('加入 Agent: not auto-sent', count_events(after_txt, 'user/message') == before)
    imgs = nodes(lambda n: n.get('class') == 'android.widget.Image')
    note(f'images in UI after add: {len(imgs)}')

    # ---------------- long-press 选择 → rectangle select ----------------
    p = by_label('选择', exact=True)
    if p:
        adb(f'shell input swipe {p[0]} {p[1]} {p[0]} {p[1]} 1200')
        time.sleep(1.2)
        shot('rect_mode_toast')
        txt = all_text()
        check('long-press: switched to rectangle', '矩形' in txt)
        # rectangle around the right half of the cut line
        stroke(line(mx + 30, ly - 70, lx2 + 40, ly + 70, 40))
        time.sleep(2)
        shot('rect_selected')
        check('rectangle: selection menu shown', '加入 Agent' in all_text())
        tap(int(notes_right * 0.5), int(H * 0.85)); time.sleep(1)
    select_tool('笔')

    if SCEN == 'ui-only':
        return finish()

    # ---------------- two real DeepSeek turns ----------------
    c = composer()
    if c: tap(c[0], c[1])
    else: note('!! no composer EditText'); tap(W - 400, H - 160)
    time.sleep(1)
    set_composer('附图是一道手写方程。请先调用 view_page 查看页面，再用 canvas_write_latex 把完整解答写在页面上，最后简短回复。')
    time.sleep(1)
    adb('shell input keyevent 111'); time.sleep(1)
    shot('typed')
    if not tap_label('发送消息'):
        note('!! send button not found')
    time.sleep(4)
    shot('turn1_running')
    txt = wait_turn_end(1, tag='turn1')
    time.sleep(2)
    shot('turn1_done')

    c = composer()
    if c:
        tap(c[0], c[1]); time.sleep(1)
        set_composer('现在用 canvas_draw_svg 在解答下方画出 y=x^2-4 的函数图像（带坐标轴），然后调用 list_pages，告诉我第 1 页有多少笔画。')
        adb('shell input keyevent 111'); time.sleep(1)
        tap_label('发送消息')
        time.sleep(3)
        txt = wait_turn_end(2, tag='turn2')
        time.sleep(3)
    shot('turn2_done')
    tools = re.findall(r'"name":"(view_page|canvas_write_latex|canvas_draw_svg|list_pages)"', txt)
    note('tool calls seen: ' + ', '.join(sorted(set(tools))))
    for t in ['view_page', 'canvas_write_latex', 'canvas_draw_svg']:
        check(f'turns: tool {t} used', t in tools)
    errs = re.findall(r'"isError":true', txt)
    check('turns: two turns completed', count_events(txt, 'turn/end') >= 2 and '"reason":"error"' not in txt, f'tool errors={len(errs)}')
    st = state('after_turns')
    check('turns: messages rendered in panel (TEST_STATE)', st.get('userCount', 0) >= 2 and st.get('messageCount', 0) >= 3, str(st))
    ftxt = all_text()
    check('footer: tokens + 缓存命中 + ¥', ('输入' in ftxt and '输出' in ftxt and '缓存命中' in ftxt and '¥' in ftxt))
    check('usage recorded in session', '"usage"' in txt and '"cacheHit"' in txt)

    # ---------------- session sidebar ----------------
    tap_label('会话列表'); time.sleep(2)
    shot('session_sidebar')
    st = state('sidebar')
    stxt = all_text()
    check('sidebar: open (TEST_STATE)', st.get('sidebarOpen') is True, str(st))
    check('sidebar: new/session listed', '新对话' in stxt and '会话操作' in stxt)
    p = by_label('会话操作')
    if p:
        tap(p[0], p[1]); time.sleep(1); shot('session_menu')
        check('sidebar: menu has 置顶/重命名/删除', all(k in all_text() for k in ['置顶', '重命名', '删除']))
        tap_label('置顶', exact=True); time.sleep(1.5)
        shot('session_pinned')
    tap_label('关闭会话列表'); time.sleep(1)

    # ---------------- collapse → floating ball, notes fullscreen ----------------
    tap_label('收起 Agent'); time.sleep(2)
    shot('collapsed_fullscreen')
    ctxt = all_text()
    st = state('collapsed')
    check('collapse: floating ball shown', '展开 Agent' in ctxt)
    check('collapse: panel hidden (TEST_STATE)', st.get('panelVisible') is False, str(st))
    tap_label('展开 Agent'); time.sleep(2)
    st = state('expanded')
    check('expand: panel visible again (TEST_STATE)', st.get('panelVisible') is True, str(st))

    # ---------------- provider settings ----------------
    tap_label('设置', exact=True); time.sleep(3)
    shot('settings_models')
    stxt = all_text()
    check('settings: provider card (DeepSeek, Anthropic Messages, 编辑)', 'DeepSeek' in stxt and '编辑' in stxt and '添加模型提供商' in stxt)
    if tap_label('编辑', exact=True):
        time.sleep(1.5); shot('settings_provider_editor')
        etxt = all_text()
        check('settings: editor fields', all(k in etxt for k in ['显示名称', 'API 地址', 'API 协议', 'API 密钥', '模型目录', '获取可用模型', '恢复默认模型']))
    tap_label('用量与价格'); time.sleep(1); shot('settings_pricing')
    tap_label('完成', exact=True); time.sleep(1.5)
    shot('final')
    return finish()

def safe(f, *a):
    try: return f(*a)
    except Exception as e: note(f'!! finish step failed: {e}'); return ''

def finish():
    open(f'{OUT}/logcat.txt', 'w').write(safe(adb, 'logcat -d -v time') or '')
    txt = safe(session_text)
    open(f'{OUT}/session.jsonl', 'w').write(txt)
    for line in txt.splitlines():
        try: e = json.loads(line)
        except Exception: continue
        t = e['type']; d = e['data']
        if t == 'tool/result':
            c = ' '.join(b.get('text', '[img]') if b['type'] == 'text' else '[img]' for b in d['content'])
            note(f"tool {d['name']} err={d.get('isError')} :: {c[:200]}")
        elif t == 'turn/end': note(f"turn/end {d}")
        elif t == 'assistant/message':
            if d.get('usage'): note(f"usage {d.get('model')} {d['usage']} cost={d.get('cost')}")
            for b in d['content']:
                if b['type'] == 'text': note('assistant: ' + b['text'][:300].replace('\n', ' '))
                if b['type'] == 'tool-call': note(f"call {b['name']} {b['arguments'][:160]}")
    passed = sum(1 for c in checks if c[1])
    note(f'SUMMARY {passed}/{len(checks)} checks passed')
    for c in checks:
        if not c[1]: note(f'FAILED: {c[0]} {c[2]}')
    open(f'{OUT}/report.txt', 'w').write('\n'.join(report))

if __name__ == '__main__':
    try: main()
    except SystemExit: finish(); raise
    except Exception as e:
        note(f'!! exception {e}'); finish(); sys.exit(1)
