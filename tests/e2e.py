#!/usr/bin/env python3
"""End-to-end scenario on an Android emulator: write, lasso, ask DeepSeek, check canvas tools.
Outputs screenshots + session log + logcat into e2e-out/."""
import os, re, subprocess, sys, time, json
import xml.etree.ElementTree as ET

OUT = 'e2e-out'; os.makedirs(OUT, exist_ok=True)
PKG = 'com.inkagent'
KEY = os.environ.get('DS_KEY', '')
SCEN = os.environ.get('SCENARIO', 'full')
shots = 0
report = []

def sh(cmd, check=False, timeout=60):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    if check and r.returncode != 0: raise RuntimeError(cmd + '\n' + r.stderr)
    return r.stdout

def adb(a, **k): return sh('adb ' + a, **k)

def note(msg):
    print(msg, flush=True); report.append(msg)

def shot(name):
    global shots
    shots += 1
    fn = f'{OUT}/{shots:02d}_{name}.png'
    subprocess.run(f'adb exec-out screencap -p > {fn}', shell=True)
    note(f'screenshot {fn}')

def dump():
    adb('shell uiautomator dump /sdcard/ui.xml', timeout=30)
    adb(f'pull /sdcard/ui.xml {OUT}/ui.xml')
    try: return ET.parse(f'{OUT}/ui.xml').getroot()
    except Exception as e: note(f'ui dump failed {e}'); return None

def find(pred):
    root = dump()
    if root is None: return None
    for n in root.iter('node'):
        if pred(n):
            b = [int(v) for v in re.findall(r'\d+', n.get('bounds'))]
            return ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2, b)
    return None

def by_text(s): return find(lambda n: s in (n.get('text') or '') or s in (n.get('content-desc') or ''))

def tap(x, y): adb(f'shell input tap {x} {y}')

def tap_text(s, required=True):
    p = by_text(s)
    if not p:
        note(f'!! element not found: {s}')
        if required: raise SystemExit(1)
        return False
    tap(p[0], p[1]); return True

def stroke(pts, dur=0.0):
    x, y = pts[0]
    adb(f'shell input motionevent DOWN {x} {y}')
    for (x, y) in pts[1:]: adb(f'shell input motionevent MOVE {x} {y}')
    adb(f'shell input motionevent UP {x} {y}')

def line(x1, y1, x2, y2, n=8):
    return [(int(x1 + (x2 - x1) * i / n), int(y1 + (y2 - y1) * i / n)) for i in range(n + 1)]

def session_text():
    return adb(f"shell run-as {PKG} sh -c 'cat files/sessions/*.jsonl'", timeout=30)

def wait_turn_end(n_expected, limit=240, every=15, tag='wait'):
    t0 = time.time(); k = 0
    while time.time() - t0 < limit:
        time.sleep(5)
        txt = session_text()
        ends = txt.count('"type":"turn/end"')
        if ends >= n_expected: return txt
        if time.time() - t0 > (k + 1) * every:
            k += 1; shot(f'{tag}_{k}')
    note('!! turn did not finish in time'); return session_text()

def launch(extra=''):
    adb(f'shell am start -W -n {PKG}/.MainActivity {extra}', timeout=60)

def main():
    size = adb('shell wm size')
    W, H = [int(v) for v in re.findall(r'(\d+)x(\d+)', size)[-1]]
    if H > W: W, H = H, W
    note(f'screen {W}x{H}')
    adb('install -r -g app/build/outputs/apk/debug/app-debug.apk', check=True, timeout=180)
    adb('shell settings put system accelerometer_rotation 0')
    adb('shell settings put system user_rotation 1')
    adb('logcat -c')
    launch(f'--es test_key {KEY} --ez test_finger true --es test_mode compact --es test_effort high')
    time.sleep(6)
    shot('launch')

    # --- handwriting: "x^2 = 4" written with finger strokes on the canvas (left 60%) ---
    cw = int(W * 0.6)
    ox, oy, s = int(cw * 0.12), int(H * 0.25), int(H * 0.07)
    stroke(line(ox, oy, ox + s, oy + s))                      # x \
    stroke(line(ox + s, oy, ox, oy + s))                      # x /
    stroke([(ox + s + 10, oy - 5), (ox + s + 25, oy - 25), (ox + s + 40, oy - 5), (ox + s + 12, oy + 10), (ox + s + 45, oy + 10)])  # ^2
    stroke(line(ox + 2 * s, oy + s // 3, ox + 3 * s, oy + s // 3))   # =
    stroke(line(ox + 2 * s, oy + 2 * s // 3, ox + 3 * s, oy + 2 * s // 3))
    stroke(line(ox + 3 * s + 40, oy, ox + 3 * s + 10, oy + s * 2 // 3))   # 4
    stroke(line(ox + 3 * s + 10, oy + s * 2 // 3, ox + 3 * s + 60, oy + s * 2 // 3))
    stroke(line(ox + 3 * s + 45, oy + s // 4, ox + 3 * s + 45, oy + s + 10))
    time.sleep(1)
    shot('handwriting')

    # --- lasso → attachment tray (must NOT send) ---
    tap_text('圈选')
    m = 30
    loop = line(ox - m, oy - m - 30, ox + 4 * s + m, oy - m - 30) + line(ox + 4 * s + m, oy - m - 30, ox + 4 * s + m, oy + s + m) \
        + line(ox + 4 * s + m, oy + s + m, ox - m, oy + s + m) + line(ox - m, oy + s + m, ox - m, oy - m - 30)
    stroke(loop)
    time.sleep(2)
    shot('lasso_tray')
    txt = session_text()
    note('lasso auto-sent? ' + ('YES (BUG)' if '"user/message"' in txt else 'no'))

    if SCEN == 'ui-only':
        return finish()

    # --- type prompt and send ---
    p = find(lambda n: n.get('class') == 'android.widget.EditText')
    if p: tap(p[0], p[1])
    else: note('!! no EditText; tapping composer area'); tap(int(W * 0.8), H - 120)
    time.sleep(1)
    prompt = 'Solve the equation in the image. Write the solution on the canvas with canvas_write_latex, then call view_page to check how it looks, then reply briefly.'
    adb("shell input text '" + prompt.replace(' ', '%s').replace("'", '') + "'")
    time.sleep(1)
    adb('shell input keyevent 111')  # hide keyboard (ESC)
    time.sleep(1)
    shot('typed')
    if not tap_text('发送消息', required=False):
        p = find(lambda n: n.get('class') == 'android.widget.Button' and 'send' in (n.get('resource-id') or ''))
        if p: tap(p[0], p[1])
    time.sleep(4)
    shot('sent_running')
    txt = wait_turn_end(1, tag='turn1')
    time.sleep(2)
    shot('turn1_done')
    if tap_text('已完成', required=False):
        time.sleep(1); shot('turn1_expanded')
        # expand all step groups
        root = dump()
        n = 0
        if root is not None:
            for node in root.iter('node'):
                t = (node.get('text') or '')
                if t.startswith('已查看') or t.startswith('已写入') or t.startswith('已完成分析') or t.startswith('已搜索'):
                    b = [int(v) for v in re.findall(r'\d+', node.get('bounds'))]
                    tap((b[0] + b[2]) // 2, (b[1] + b[3]) // 2); n += 1
                    if n >= 3: break
        time.sleep(1); shot('turn1_groups')

    # --- second turn: SVG drawing ---
    p = find(lambda n: n.get('class') == 'android.widget.EditText')
    if p:
        tap(p[0], p[1]); time.sleep(1)
        adb("shell input text 'Now%sdraw%sthe%sgraph%sof%sy=x^2-4%swith%scanvas_draw_svg%sbelow%sthe%ssolution,%sincluding%saxes.'")
        adb('shell input keyevent 111'); time.sleep(1)
        tap_text('发送消息', required=False)
        time.sleep(3)
        txt = wait_turn_end(2, tag='turn2')
        time.sleep(2)
        shot('turn2_done')

    # --- presentation modes on the replayed session ---
    for mode in ['standard', 'verbose']:
        launch(f'--es test_mode {mode}')
        time.sleep(3)
        # force a replay by reopening the activity state via settings change path
        adb(f'shell am force-stop {PKG}'); launch(''); time.sleep(5)
        shot(f'mode_{mode}')
    launch('--es test_mode compact')
    return finish()

def finish():
    txt = session_text()
    open(f'{OUT}/session.jsonl', 'w').write(txt)
    adb(f"shell run-as {PKG} sh -c 'cat files/notebook/notebook.json'") and open(f'{OUT}/notebook.json', 'w').write(adb(f"shell run-as {PKG} sh -c 'cat files/notebook/notebook.json'"))
    os.makedirs(f'{OUT}/ai', exist_ok=True)
    for f in adb(f"shell run-as {PKG} ls files/notebook/ai").split():
        subprocess.run(f"adb exec-out run-as {PKG} cat files/notebook/ai/{f} > {OUT}/ai/{f}", shell=True)
    open(f'{OUT}/logcat.txt', 'w').write(adb('logcat -d -v time', timeout=60))
    # summary
    for line in txt.splitlines():
        try: e = json.loads(line)
        except Exception: continue
        t = e['type']; d = e['data']
        if t == 'tool/result':
            c = ' '.join(b.get('text', '[img]') if b['type'] == 'text' else '[img]' for b in d['content'])
            note(f"tool {d['name']} err={d.get('isError')} :: {c[:160]}")
        elif t == 'turn/end': note(f"turn/end {d}")
        elif t == 'assistant/message':
            for b in d['content']:
                if b['type'] == 'text': note('assistant: ' + b['text'][:300].replace('\n', ' '))
                if b['type'] == 'tool-call': note(f"call {b['name']} {b['arguments'][:160]}")
    open(f'{OUT}/report.txt', 'w').write('\n'.join(report))

if __name__ == '__main__':
    try: main()
    except SystemExit: finish(); raise
    except Exception as e:
        note(f'!! exception {e}'); finish(); sys.exit(1)
