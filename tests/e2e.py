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
    tap_label(name, exact=True); time.sleep(1.0)
    for _ in range(2):
        t = all_text()
        popup = any(k in t for k in ['压感', '笔画宽度', '发光效果', '橡皮大小', '缩放比例'])
        if t and ('撤销' not in t or popup):  # tool options popup open (re-tap of the active tool)
            note(f'closing {name} options popup'); adb('shell input keyevent 4'); time.sleep(1.0)
        else: break

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

def swipe(x1, y1, x2, y2, ms=700):
    adb(f'shell input swipe {int(x1)} {int(y1)} {int(x2)} {int(y2)} {ms}')
    time.sleep(1.5)

def run_modes(W, H):
    st = state('split')
    shot('split_landscape')
    nw0, pw0 = st.get('notesWidth', 0), st.get('panelW', 0)
    check('split: notes area shrinks, agent docked right (not covering)',
          st.get('mode') == 'split' and 0 < nw0 < W * 0.85 and st.get('panelX', 0) >= nw0 - 2 and st.get('panelX', 0) + pw0 <= W + 2,
          f"notesWidth={nw0} panelX={st.get('panelX')} panelW={pw0} W={W}")
    dv = by_label('拖动调整分屏比例')
    check('split: divider present', dv is not None)
    if dv:
        swipe(dv[0], dv[1], dv[0] - 300, dv[1], 900)
        st2 = state('split_dragged')
        shot('split_divider_dragged')
        check('split: divider drag changes ratio (notes narrower, agent wider)',
              st2.get('notesWidth', 0) < nw0 - 120 and st2.get('splitLand', 0) > st.get('splitLand', 0) + 0.04,
              f"notesWidth {nw0}->{st2.get('notesWidth')} splitLand {st.get('splitLand')}->{st2.get('splitLand')}")
    # minimise to the ball
    ok = tap_label('最小化', exact=True); time.sleep(2)
    st = state('ball')
    shot('ball')
    check('ball: 最小化 → floating ball, notes full width', ok and '展开 Agent' in all_text() and st.get('panelVisible') is False
          and st.get('notesWidth', 0) >= W - 2, f"mode={st.get('mode')} notesWidth={st.get('notesWidth')}")
    p = by_label('展开 Agent')
    if p:
        swipe(p[0], p[1], int(W * 0.3), int(H * 0.45), 900); time.sleep(1)
        p2 = by_label('展开 Agent')
        shot('ball_dragged_snapped')
        check('ball: drag snaps to the left edge', p2 is not None and p2[0] < W * 0.1, f'ball at {p2[:2] if p2 else None}')
        p = p2 or p
    # ball → floating window
    if p: tap(p[0], p[1])
    time.sleep(2)
    st = state('window')
    shot('floating_window')
    check('window: ball tap opens floating window over full-width notes',
          st.get('mode') == 'window' and st.get('panelVisible') is True and 0 < st.get('panelW', 0) < W * 0.6 and st.get('notesWidth', 0) >= W - 2,
          f"mode={st.get('mode')} panel={st.get('panelX')},{st.get('panelY')} {st.get('panelW')}x{st.get('panelH')}")
    lay = st.get('layout') or {}
    check('window: transcript visible inside window', (lay.get('scrollClientHeight') or 0) > 200 and lay.get('lastVisible') is True, str(lay))
    w0, h0, x0 = st.get('panelW', 0), st.get('panelH', 0), st.get('panelX', 0)
    c = by_label('调整浮窗左下角')
    check('window: resize corner present', c is not None)
    if c:
        swipe(c[0], c[1], c[0] - 260, c[1] + 160, 900)
        st2 = state('window_resized')
        shot('window_resized')
        check('window: resize by corner changes size', st2.get('panelW', 0) > w0 + 150 and st2.get('panelH', 0) > h0 + 60,
              f"{w0}x{h0} -> {st2.get('panelW')}x{st2.get('panelH')}")
        st = st2
    g = by_label('拖动浮窗')
    if g:
        x1 = st.get('panelX', 0)
        swipe(g[0], g[1], g[0] - 500, g[1] + 40, 900)
        st3 = state('window_moved')
        shot('window_moved')
        check('window: drag by header moves it', st3.get('panelX', 0) < x1 - 300, f"panelX {x1} -> {st3.get('panelX')}")
    # 划分领土
    ok = tap_label('划分领土', exact=True); time.sleep(2.5)
    st = state('split_again')
    shot('split_from_window')
    check('split: 划分领土 docks right and shrinks notes', ok and st.get('mode') == 'split' and st.get('notesWidth', 0) < W * 0.85
          and st.get('panelX', 0) >= st.get('notesWidth', 0) - 2, f"notesWidth={st.get('notesWidth')} panelX={st.get('panelX')}")
    ok = tap_label('浮窗模式', exact=True); time.sleep(2.5)
    st = state('window_again')
    check('window: 浮窗模式 returns to the floating window (size remembered)', ok and st.get('mode') == 'window'
          and abs(st.get('panelW', 0) - (st2.get('panelW', 0) if c else w0)) < 30, f"panelW={st.get('panelW')}")
    ok = tap_label('最小化', exact=True); time.sleep(2)
    st = state('ball_again')
    check('ball: back to ball', ok and st.get('mode') == 'ball' and '展开 Agent' in all_text())
    shot('back_to_ball')
    tap_label('展开 Agent'); time.sleep(2)
    st = state('expanded')
    check('expand: panel visible again (TEST_STATE)', st.get('panelVisible') is True, str(st))

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
    ex = f'--es test_key {KEY} --ez test_finger true --es test_mode compact --es test_effort high --es test_agent_mode split'
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
    h = by_label('拖动调整分屏比例')
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
    lay = st.get('layout') or {}
    pg = st.get('page') or {}
    items = pg.get('items') or []
    imgs = [i for i in items if i.get('kind') == 'image']
    others = [i for i in items if i.get('kind') != 'image']
    pw, ph = pg.get('width') or 0, pg.get('height') or 0
    def inter(a, b, pad=0):
        return a['x'] < b['x'] + b['w'] - pad and b['x'] < a['x'] + a['w'] - pad and a['y'] < b['y'] + b['h'] - pad and b['y'] < a['y'] + a['h'] - pad
    inside = all(i['x'] >= 0 and i['x'] + i['w'] <= pw + 0.5 and i['y'] >= 0 and i['y'] + i['h'] <= ph + 0.5 for i in imgs)
    overl = [(round(a['y']), round(b['y'])) for a in imgs for b in others if inter(a, b, 1)]
    overl += [(round(a['y']), round(b['y'])) for ia, a in enumerate(imgs) for b in imgs[ia + 1:] if inter(a, b, 1)]
    note(f'page {pw}x{ph}: {len(imgs)} AI images ' + ', '.join(f"({int(i['x'])},{int(i['y'])} {int(i['w'])}x{int(i['h'])})" for i in imgs))
    check('placement: AI images inside page width/height', len(imgs) >= 2 and inside, f'{len(imgs)} images')
    check('placement: AI images overlap no existing content', len(imgs) >= 2 and not overl, f'overlaps={overl[:5]}')
    shot('transcript_visible')
    check('transcript: #scroll height > 200px', (lay.get('scrollClientHeight') or 0) > 200, str(lay))
    check('transcript: last message visible', lay.get('lastVisible') is True, str(lay))
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

    # ---------------- agent window modes: split ⇄ window ⇄ ball ----------------
    run_modes(W, H)

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
