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
    try: subprocess.run(f'adb exec-out screencap -p > {fn}', shell=True, timeout=60)
    except Exception as e: note(f'!! screencap failed {e}')
    note(f'screenshot {fn}')
    return fn

def raw_screen():
    """(w, h, bytes RGBA) from `screencap` raw output."""
    data = subprocess.run('adb exec-out screencap', shell=True, capture_output=True, timeout=60).stdout
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
    for _ in range(3):
        t = all_text() or all_text()  # a slow emulator sometimes fails one ui dump
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

def wait_ready(extra='', limit=180):
    """Wait until the note editor toolbar is up; ride out slow-emulator ANR dialogs (Wait) and relaunch if killed."""
    t0 = time.time()
    while time.time() - t0 < limit:
        f = focus()
        if 'Not Responding' in f or 'Application Error' in f or 'ImmersiveModeConfirmation' in f:
            note('ready-wait: dialog ' + f)
            if not (tap_label('Wait') or tap_label('等待') or tap_label('Got it') or tap_label('知道了')): adb('shell input keyevent 4')
            time.sleep(3); continue
        if not adb(f'shell pidof {PKG}').strip():
            note('ready-wait: app not running, relaunching'); launch(extra); time.sleep(10); continue
        if PKG not in f:
            note('ready-wait: not in front: ' + f); launch(extra); time.sleep(8); continue
        if '撤销' in all_text():
            note(f'ready after {int(time.time() - t0)}s'); return True
        time.sleep(5)
    note('!! editor never became ready'); return False

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
    wait_ready(ex)
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
    tap_label('Agent', exact=True); time.sleep(1)
    shot('settings_agent_overlay_toggle')
    check('settings: overlay toggle 「在其他应用上显示悬浮球」 present', '在其他应用上显示悬浮球' in all_text())
    tap_label('完成', exact=True); time.sleep(1.5)
    shot('final')
    safe(run_overlay, W, H)
    safe(run_editor, W, H)
    return finish()

def ostate(tag=''):
    """Overlay + panel state via the overlay service's debug receiver (logcat tag InkAgentOverlay)."""
    def grab(): return [l for l in adb('logcat -d -s InkAgentOverlay:I', timeout=30).splitlines() if '{' in l]
    n0 = len(grab())
    adb(f'shell am broadcast -a com.inkagent.TEST_OVERLAY -p {PKG} --es op state')
    for _ in range(8):
        time.sleep(1)
        lines = grab()
        if len(lines) > n0:
            try:
                st = json.loads(lines[-1][lines[-1].index('{'):])
                note(f'ostate[{tag}]: {st}'); return st
            except Exception as e: note(f'ostate parse failed {e}: {lines[-1][:300]}')
    note(f'!! no InkAgentOverlay line [{tag}]'); return {}

def overlay_op(op, extra=''):
    adb(f"shell \"am broadcast -a com.inkagent.TEST_OVERLAY -p {PKG} --es op {op} {extra}\"")

def is_blue(scr, x, y):
    w, h, px = scr
    if not (0 <= x < w and 0 <= y < h): return False
    i = (y * w + x) * 4
    r, g, b = px[i], px[i + 1], px[i + 2]
    return b > 200 and r < 140 and g < 160

def web_pt(st, key):
    """Screen point (centre) of an agent.html element from its CSS rect + the WebView's screen origin."""
    r = (st.get('rects') or {}).get(key)
    if not r: return None
    d = st.get('density') or 1
    return (int(st.get('viewScreenX', 0) + (r[0] + r[2] / 2) * d), int(st.get('viewScreenY', 0) + (r[1] + r[3] / 2) * d))

def top_activity():
    out = adb('shell dumpsys activity activities', timeout=60)
    m = re.search(r'topResumedActivity=.*', out) or re.search(r'mResumedActivity: .*', out)
    return m.group(0) if m else '?'

def run_overlay(W, H):
    """Cross-app overlay: ball over another app → overlay agent window (same session) → 框选截屏 → attachment."""
    note('---- cross-app overlay ----')
    adb(f'shell appops set {PKG} SYSTEM_ALERT_WINDOW allow')
    adb(f'shell appops set {PKG} PROJECT_MEDIA allow')
    note('appops: ' + adb(f'shell appops get {PKG}').replace('\n', ' | ')[:400])
    launch('--ez test_overlay true'); time.sleep(4)
    st = ostate('enabled_in_app')
    ov = st.get('overlay') or {}
    check('overlay: service running, no overlay ball while InkAgent is in front',
          ov.get('running') is True and ov.get('appForeground') is True and not (ov.get('ball') or {}).get('shown'), str(ov)[:300])
    msgs0 = count_events(session_text(), 'user/message')
    # leave the app: HOME, then open another app. (The system Settings app hides all third-party overlays by
    # design — HIDE_NON_SYSTEM_OVERLAY_WINDOWS — so it is checked separately; the main checks run over Files/launcher.)
    adb('shell input keyevent 3'); time.sleep(3)
    adb('shell am start -a android.settings.SETTINGS'); time.sleep(5)
    st = ostate('over_settings')
    shot('overlay_over_system_settings')
    note('over system Settings: ball shown flag=' + str(((st.get('overlay') or {}).get('ball') or {}).get('shown')) + ' (Settings hides non-system overlays)')
    adb('shell input keyevent 3'); time.sleep(2)
    other = None
    for cmd in ['am start -n com.google.android.documentsui/com.android.documentsui.files.FilesActivity',
                'am start -n com.android.documentsui/com.android.documentsui.files.FilesActivity',
                'am start -a android.intent.action.VIEW -d content://com.android.contacts/contacts']:
        adb('shell ' + cmd); time.sleep(5)
        f = top_activity(); note('candidate: ' + f)
        if PKG not in f and 'settings' not in f.lower() and 'launcher' not in f.lower() and f != '?':
            other = f; break
        adb('shell input keyevent 3'); time.sleep(2)
    f = top_activity(); note('top (other app): ' + f)
    st = ostate('ball_over_settings')
    ov = st.get('overlay') or {}
    b = ov.get('ball') or {}
    shot('overlay_ball_over_settings')
    scr = raw_screen()
    bx, by = b.get('x', -1), b.get('y', -1)
    blue = bx > 0 and any(is_blue(scr, bx + dx, by + dy) for dx, dy in [(-34, 0), (34, 0), (0, -34), (0, 34), (-24, -24), (24, 24)])
    wins = adb('shell dumpsys window windows', timeout=60)
    check('overlay: ball visible over another app', PKG not in f and b.get('shown') is True and blue
          and 'InkAgentOverlayBall' in wins, f"ball={b} blue={blue} focus={f}")
    check('overlay: ball snapped to a screen edge', bx > 0 and (bx < W * 0.1 or bx > W * 0.9), f'x={bx} W={W}')
    # drag the ball to the left: it should snap to the left edge
    if bx > 0:
        adb(f'shell input swipe {bx} {by} {int(W * 0.3)} {int(H * 0.45)} 900'); time.sleep(1.5)
        b2 = (ostate('ball_dragged').get('overlay') or {}).get('ball') or {}
        shot('overlay_ball_dragged')
        check('overlay: dragged ball snaps to the left edge', 0 < b2.get('x', -1) < W * 0.1, str(b2))
        bx, by = b2.get('x', bx), b2.get('y', by)
    # tap the ball → overlay agent window
    tap(bx, by); time.sleep(3)
    st = ostate('window')
    ov = st.get('overlay') or {}
    w = ov.get('window') or {}
    shot('overlay_window')
    f = top_activity(); note('top with overlay window: ' + f)
    check('overlay window: opens over the other app with the agent panel (same WebView)',
          w.get('open') is True and w.get('shown') is True and st.get('overlayActive') is True and st.get('panelVisible') is True
          and st.get('dataMode') == 'overlay' and (st.get('viewWidth') or 0) > 200 and not (ov.get('ball') or {}).get('shown'),
          f"window={w} panelVisible={st.get('panelVisible')} dataMode={st.get('dataMode')} view={st.get('viewWidth')}x{st.get('viewHeight')}")
    check('overlay window: same session (transcript present)', st.get('userCount', 0) >= 2, f"userCount={st.get('userCount')}")
    cap = (st.get('rects') or {}).get('capBtn')
    check('overlay window: 框选截屏 + 打开应用 buttons', cap and cap[4] and ((st.get('rects') or {}).get('modeBtn') or [0] * 5)[4], str(st.get('rects')))
    att0 = st.get('attachments', 0); user0 = st.get('userCount', 0)
    # composer focus → window becomes focusable (IME)
    ip = web_pt(st, 'input')
    if ip:
        tap(ip[0], ip[1]); time.sleep(2.5)
        st2 = ostate('composer_focus')
        w2 = (st2.get('overlay') or {}).get('window') or {}
        ims = adb('shell dumpsys input_method', timeout=40)
        m = re.search(r'mInputShown=(\w+)', ims)
        note(f"IME after composer tap: mInputShown={m.group(1) if m else '?'}")
        check('overlay window: IME shown for the composer', m is not None and m.group(1) == 'true', m.group(0) if m else '?')
        shot('overlay_composer_focused')
        check('overlay window: becomes focusable when the composer is tapped', w2.get('focusable') is True and w2.get('inputFocused') is True, str(w2))
        adb('shell input keyevent 4'); time.sleep(1)   # hide IME
        tap(int(W * 0.3), int(H * 0.3)); time.sleep(1.5)  # outside the window, on the other app → release focus
        w3 = (ostate('outside_tap').get('overlay') or {}).get('window') or {}
        check('overlay window: outside tap releases focus (back to the other app)', w3.get('focusable') is False and w3.get('open') is True, str(w3))
    # 框选截屏 (real tap on the header button); consent auto-granted via appops PROJECT_MEDIA
    st = ostate('before_capture')
    cp = web_pt(st, 'capBtn')
    if cp: tap(cp[0], cp[1])
    else: overlay_op('capture')
    time.sleep(4)
    st = ostate('capture')
    c = (st.get('overlay') or {}).get('capture') or {}
    if not c.get('shown'):
        note('capture selector not up after tap; retrying via debug broadcast'); overlay_op('capture'); time.sleep(4)
        st = ostate('capture_retry'); c = (st.get('overlay') or {}).get('capture') or {}
    shot('capture_selector')
    check('capture: grant obtained, fullscreen selector shown with lasso by default',
          c.get('shown') is True and c.get('projection') is True and c.get('mode') == 'lasso', str(c)[:400] + ' err=' + str((st.get('overlay') or {}).get('error')))
    check('capture: overlay hidden while capturing', not ((st.get('overlay') or {}).get('window') or {}).get('shown'), '')
    # long-press then drag → rectangle (debug broadcast drives the selector's touch path)
    x1, y1, x2, y2 = int(W * 0.12), int(H * 0.2), int(W * 0.48), int(H * 0.55)
    pts = ';'.join(f'{x},{y}' for x, y in line(x1, y1, x2, y2, 20))
    overlay_op('drag', f"--es pts '{x1},{y1};{pts}' --ei hold 800"); time.sleep(3)
    st = ostate('capture_rect')
    c = (st.get('overlay') or {}).get('capture') or {}
    shot('capture_rect_selected')
    sel = c.get('sel') or [0, 0, 0, 0]
    check('capture: long-press switches to rectangle, drag selects it', c.get('mode') == 'rect' and c.get('hasSelection') is True
          and abs(sel[0] - x1) < 25 and abs(sel[3] - y2) < 25, str(c)[:400])
    ok = c.get('okBtn')
    if ok: tap(ok[0], ok[1])
    else: overlay_op('confirm')
    time.sleep(3)
    st = ostate('after_confirm')
    ov = st.get('overlay') or {}
    c = ov.get('capture') or {}
    shot('overlay_attachment_added')
    lc = c.get('lastCrop') or [0, 0]
    check('capture: 确定 → crop added as attachment, window restored', not c.get('shown') and (ov.get('window') or {}).get('shown') is True
          and st.get('attachments', 0) == att0 + 1 and st.get('trayCount', 0) == att0 + 1, f"att {att0}->{st.get('attachments')} tray={st.get('trayCount')} crop={lc}")
    check('capture: crop matches the rectangle', abs(lc[0] - (x2 - x1)) < 30 and abs(lc[1] - (y2 - y1)) < 30, f'crop={lc} rect={x2 - x1}x{y2 - y1}')
    check('capture: not auto-sent', st.get('userCount', 0) == user0 and count_events(session_text(), 'user/message') == msgs0,
          f"userCount {user0}->{st.get('userCount')}")
    safe(run_overlay_editor, W, H)
    # second capture: grant reused (no new consent), default lasso again, 取消 adds nothing
    st = ostate('before_capture2')
    cp = web_pt(st, 'capBtn')
    if cp: tap(cp[0], cp[1])
    else: overlay_op('capture')
    time.sleep(3.5)
    lx, ly, r = int(W * 0.55), int(H * 0.45), int(H * 0.15)
    import math
    loop = [(int(lx + r * math.cos(t / 20 * 2 * math.pi)), int(ly + r * math.sin(t / 20 * 2 * math.pi))) for t in range(21)]
    overlay_op('drag', "--es pts '" + ';'.join(f'{x},{y}' for x, y in loop) + "'"); time.sleep(2.5)
    st = ostate('capture2_lasso')
    c = (st.get('overlay') or {}).get('capture') or {}
    shot('capture_lasso_selected')
    check('capture: grant cached (one consent), lasso selection by default', c.get('shown') is True and c.get('consentRequests') == 1
          and c.get('mode') == 'lasso' and c.get('hasSelection') is True, str(c)[:400])
    cb = c.get('cancelBtn')
    if cb: tap(cb[0], cb[1])
    else: overlay_op('cancel')
    time.sleep(2.5)
    st = ostate('after_cancel')
    ov = st.get('overlay') or {}
    check('capture: 取消 restores the window, nothing added', not (ov.get('capture') or {}).get('shown') and (ov.get('window') or {}).get('shown') is True
          and st.get('attachments', 0) == att0 + 1, f"attachments={st.get('attachments')}")
    # 打开应用: back in InkAgent with the same panel (attachment still in the tray)
    mp = web_pt(st, 'modeBtn')
    if mp: tap(mp[0], mp[1])
    time.sleep(4)
    f = top_activity(); note('top after 打开应用: ' + f)
    st = ostate('back_in_app')
    ov = st.get('overlay') or {}
    shot('back_in_app_after_overlay')
    check('打开应用: InkAgent in front, overlay gone, panel back in app with the attachment', PKG in f and ov.get('appForeground') is True
          and not (ov.get('ball') or {}).get('shown') and not (ov.get('window') or {}).get('open') and st.get('overlayActive') is False
          and st.get('panelVisible') is True and st.get('trayCount', 0) == att0 + 1 and st.get('userCount', 0) == user0,
          f"focus={f} mode={st.get('mode')} tray={st.get('trayCount')} panelVisible={st.get('panelVisible')}")

def fresh_state(tag=''):
    """TEST_STATE, waiting for a line newer than the ones already in logcat."""
    def grab(): return [l for l in adb('logcat -d -s InkAgentState:I', timeout=30).splitlines() if '{' in l]
    n0 = len(grab())
    adb(f'shell am broadcast -a com.inkagent.TEST_STATE -p {PKG}')
    for _ in range(8):
        time.sleep(1)
        lines = grab()
        if len(lines) > n0:
            try:
                st = json.loads(lines[-1][lines[-1].index('{'):])
                short = {k: st.get(k) for k in ['attachments', 'trayCount', 'userCount', 'editor', 'attachInfo', 'clip', 'lastClipUri', 'lastSavedUri']}
                note(f'estate[{tag}]: {short}'); return st
            except Exception as e: note(f'estate parse failed {e}')
    note(f'!! no fresh InkAgentState line [{tag}]'); return {}

def thumb_pt(st, i):
    r = (st.get('trayRects') or [])
    if i < 0: i = len(r) + i
    if not (0 <= i < len(r)): return None
    d = st.get('density') or 1
    x, y, w, h = r[i]
    return (int(st.get('viewScreenX', 0) + (x + w / 2) * d), int(st.get('viewScreenY', 0) + (y + h / 2) * d))

def ebtn(st, name):
    b = ((st.get('editor') or {}).get('buttons') or {}).get(name)
    return (b[0], b[1]) if b else None

def tap_ebtn(st, name):
    p = ebtn(st, name)
    if not p: note(f'!! editor button not found: {name}'); return False
    tap(p[0], p[1]); return True

def is_red(scr, x, y, r=6):
    w, h, px = scr
    for yy in range(max(0, y - r), min(h, y + r + 1)):
        for xx in range(max(0, x - r), min(w, x + r + 1), 2):
            i = (yy * w + xx) * 4
            if px[i] > 190 and px[i + 1] < 110 and px[i + 2] < 110: return True
    return False

def run_overlay_editor(W, H):
    """Overlay window: single tap on the tray thumbnail opens the editor over the other app; 取消 changes nothing."""
    st = ostate('ov_editor_before')
    att = st.get('attachments', 0)
    p = thumb_pt(st, -1)
    check('overlay editor: tray thumbnail present in the overlay window', p is not None and st.get('trayCount', 0) >= 1, str(st.get('trayRects')))
    if not p: return
    tap(p[0], p[1]); time.sleep(3)
    st = ostate('ov_editor_open')
    e = st.get('editor') or {}
    shot('overlay_image_editor')
    rb = ebtn(st, '替换原附件')
    if rb:
        w_, h_, px = raw_screen(); i = ((rb[1] - 30) * w_ + rb[0]) * 4
        rgb = (px[i], px[i + 1], px[i + 2])
        check('overlay editor: rendered at full brightness (primary button colour exact)', abs(rgb[0] - 47) < 8 and abs(rgb[1] - 123) < 8 and rgb[2] > 238, str(rgb))
    r = e.get('imgRect') or [0, 0, 0, 0]
    if e.get('open'):
        x1, x2, y = r[0] + (r[2] - r[0]) // 5, r[0] + (r[2] - r[0]) * 4 // 5, (r[1] + r[3]) // 2
        swipe(x1, y, x2, y + 10, 500)
        st = ostate('ov_editor_drawn')
        e2 = st.get('editor') or {}
        shot('overlay_image_editor_drawn')
        check('overlay editor: opens fullscreen over the other app, drawing works',
              e.get('host') == 'overlay' and e2.get('ops', 0) >= 1 and (e.get('root') or [0, 0, 0, 0])[2] >= W * 0.9, f"editor={str(e)[:300]} ops={e2.get('ops')}")
        tap_ebtn(st, '取消'); time.sleep(2)
    else:
        check('overlay editor: opens fullscreen over the other app, drawing works', False, str(st)[:300])
    st = ostate('ov_editor_closed')
    check('overlay editor: 取消 closes it, attachment unchanged, window back', not (st.get('editor') or {}).get('open')
          and st.get('attachments', 0) == att and ((st.get('overlay') or {}).get('window') or {}).get('shown') is True, f"attachments={st.get('attachments')}")

def run_editor(W, H):
    """In-app attachment editor: tap thumbnail → draw (TEST_INPUT) → 替换原附件 / 添加为新附件 / 复制到剪贴板 / 保存到相册."""
    note('---- attachment image editor ----')
    if PKG not in top_activity(): launch(); time.sleep(5)
    st = fresh_state('editor_start')
    if st.get('trayCount', 0) < 1:
        adb(f'shell am broadcast -a com.inkagent.TEST_INPUT -p {PKG} --ez add_test_attachment true'); time.sleep(2)
        st = fresh_state('editor_start2')
    users0 = st.get('userCount', 0); msgs0 = count_events(session_text(), 'user/message')
    n = st.get('attachments', 0)
    info0 = (st.get('attachInfo') or [{}])[0]

    def open_editor(tag):
        s = fresh_state(tag + '_pre')
        p = thumb_pt(s, 0)
        if not p: note('!! no thumbnail'); return {}
        tap(p[0], p[1]); time.sleep(2.5)
        return fresh_state(tag)

    def draw_line_in(e, fy, tool='finger'):
        r = e.get('imgRect') or [0, 0, 0, 0]
        x1, x2 = r[0] + (r[2] - r[0]) // 6, r[0] + (r[2] - r[0]) * 5 // 6
        y = int(r[1] + (r[3] - r[1]) * fy)
        stroke(line(x1, y, x2, y - 20, 16), tool)
        return (x1 + x2) // 2, y - 10

    st = open_editor('editor_open')
    e = st.get('editor') or {}
    shot('editor_open')
    names = ['画笔', '荧光笔', '矩形框', '圈', '箭头', '文字', '马赛克', '撤销', '重做', '替换原附件', '添加为新附件', '复制到剪贴板', '更多操作', '取消', '红色', '黄色', '蓝色', '黑色', '细', '中', '粗']
    btns = e.get('buttons') or {}
    check('editor: single tap on thumbnail opens fullscreen editor (in app)', e.get('open') is True and e.get('host') == 'app'
          and (e.get('root') or [0, 0, 0, 0])[2] >= W * 0.95, str(e)[:300])
    check('editor: all tools / colours / widths / actions present', all(k in btns for k in names), 'missing=' + str([k for k in names if k not in btns]))
    # overflow / overlap of the bottom controls
    boxes = [(k, b[0] - b[2] / 2, b[1] - b[3] / 2, b[0] + b[2] / 2, b[1] + b[3] / 2) for k, b in btns.items() if k != '保存到相册']
    ovl = [(a[0], b[0]) for i, a in enumerate(boxes) for b in boxes[i + 1:] if a[1] < b[3] - 1 and b[1] < a[3] - 1 and a[2] < b[4] - 1 and b[2] < a[4] - 1]
    outside = [k for k, x1, y1, x2, y2 in boxes if x1 < 0 or x2 > W or y2 > H]
    check('editor: controls inside the screen, no overlaps', not ovl and not outside, f'overlaps={ovl} outside={outside}')
    # 画笔 (default, red) stroke + 矩形框 via TEST_INPUT
    mx, my = draw_line_in(e, 0.35)
    tap_ebtn(st, '矩形框'); time.sleep(1)
    r = e.get('imgRect') or [0, 0, 0, 0]
    rx1, ry1, rx2, ry2 = r[0] + (r[2] - r[0]) // 4, r[1] + (r[3] - r[1]) // 2, r[0] + (r[2] - r[0]) * 3 // 4, r[1] + (r[3] - r[1]) * 5 // 6
    stroke(line(rx1, ry1, rx2, ry2, 16), 'finger')
    time.sleep(1)
    st = fresh_state('editor_drawn')
    e = st.get('editor') or {}
    shot('editor_stroke_rect')
    scr = raw_screen()
    check('editor: stroke + rectangle drawn (2 ops, red ink on screen)', e.get('ops') == 2 and e.get('tool') == 'rect'
          and is_red(scr, mx, my, 10) and is_red(scr, rx1, (ry1 + ry2) // 2, 8), f"ops={e.get('ops')} tool={e.get('tool')}")
    tap_ebtn(st, '撤销'); time.sleep(0.8)
    s1 = fresh_state('editor_undo')
    tap_ebtn(st, '重做'); time.sleep(0.8)
    s2 = fresh_state('editor_redo')
    check('editor: 撤销 / 重做', (s1.get('editor') or {}).get('ops') == 1 and (s1.get('editor') or {}).get('redo') == 1
          and (s2.get('editor') or {}).get('ops') == 2, f"undo→{(s1.get('editor') or {}).get('ops')} redo→{(s2.get('editor') or {}).get('ops')}")
    # 替换原附件
    tap_ebtn(s2, '替换原附件'); time.sleep(2.5)
    st = fresh_state('after_replace')
    info1 = (st.get('attachInfo') or [{}])[0]
    shot('after_replace_tray')
    check('editor: 替换原附件 → attachment changed, count unchanged, editor closed', not (st.get('editor') or {}).get('open')
          and st.get('attachments') == n and info1.get('md5') and info1.get('md5') != info0.get('md5'),
          f"n {n}->{st.get('attachments')} md5 {info0.get('md5')}->{info1.get('md5')} size {info0.get('size')}->{info1.get('size')}")
    # 添加为新附件 (箭头 + 马赛克)
    st = open_editor('editor_open2')
    e = st.get('editor') or {}
    tap_ebtn(st, '箭头'); time.sleep(0.8)
    r = e.get('imgRect') or [0, 0, 0, 0]
    stroke(line(r[0] + (r[2] - r[0]) // 5, r[1] + (r[3] - r[1]) * 4 // 5, r[0] + (r[2] - r[0]) // 2, r[1] + (r[3] - r[1]) // 3, 12), 'finger')
    tap_ebtn(st, '马赛克'); time.sleep(0.8)
    stroke(line(r[0] + (r[2] - r[0]) * 3 // 5, r[1] + (r[3] - r[1]) // 4, r[0] + (r[2] - r[0]) * 9 // 10, r[1] + (r[3] - r[1]) // 4, 14), 'finger')
    time.sleep(1)
    st = fresh_state('editor_arrow_mosaic')
    shot('editor_arrow_mosaic')
    tap_ebtn(st, '添加为新附件'); time.sleep(2.5)
    st = fresh_state('after_add')
    shot('after_add_tray')
    check('editor: 添加为新附件 → count +1', not (st.get('editor') or {}).get('open') and st.get('attachments') == n + 1
          and st.get('trayCount') == n + 1, f"attachments={st.get('attachments')} tray={st.get('trayCount')}")
    # 复制到剪贴板 (荧光笔 stroke)
    st = open_editor('editor_open3')
    e = st.get('editor') or {}
    tap_ebtn(st, '荧光笔'); time.sleep(0.8)
    draw_line_in(e, 0.6)
    time.sleep(0.8)
    tap_ebtn(st, '复制到剪贴板'); time.sleep(2)
    st = fresh_state('after_copy')
    clip = st.get('clip') or {}
    check('editor: 复制到剪贴板 → image content URI on the clipboard', (st.get('editor') or {}).get('open') is True
          and str(clip.get('uri', '')).startswith('content://com.inkagent.fileprovider/') and clip.get('mime') == 'image/png'
          and clip.get('uri') == st.get('lastClipUri'), f"clip={clip} last={st.get('lastClipUri')}")
    note('dumpsys clipboard: ' + adb('shell dumpsys clipboard', timeout=30).replace('\n', ' | ')[:300])
    # ⋯ → 保存到相册
    tap_ebtn(st, '更多操作'); time.sleep(1)
    st = fresh_state('more_menu')
    shot('editor_more_menu')
    check('editor: ⋯ menu shows 保存到相册', (st.get('editor') or {}).get('moreMenu') is True and ebtn(st, '保存到相册') is not None)
    tap_ebtn(st, '保存到相册'); time.sleep(2.5)
    st = fresh_state('after_save')
    q = adb('shell content query --uri content://media/external/images/media --projection _display_name:relative_path:_size', timeout=40)
    rows = [l for l in q.splitlines() if 'InkAgent_' in l]
    note('MediaStore rows: ' + ' | '.join(rows)[:400])
    check('editor: 保存到相册 → file in MediaStore Pictures/InkAgent', st.get('lastSavedUri', '').startswith('content://media/')
          and any('Pictures/InkAgent' in l for l in rows), f"uri={st.get('lastSavedUri')} rows={len(rows)}")
    shot('editor_after_save')
    tap_ebtn(st, '取消'); time.sleep(2)
    st = fresh_state('editor_cancel')
    shot('editor_closed')
    check('editor: 取消 closes, nothing changed, never auto-sent', not (st.get('editor') or {}).get('open') and st.get('attachments') == n + 1
          and st.get('userCount', 0) == users0 and count_events(session_text(), 'user/message') == msgs0,
          f"attachments={st.get('attachments')} userCount {users0}->{st.get('userCount')}")

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
