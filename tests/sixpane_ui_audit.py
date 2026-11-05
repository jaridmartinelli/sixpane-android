"""Exercise the shipped SIXPANE APK through Android UI controls on a disposable emulator."""
import hashlib, json, os, re, subprocess, sys, time, traceback, zipfile
from pathlib import Path
import xml.etree.ElementTree as ET
import uiautomator2 as u2

PKG = 'com.sixpane.app.preview.universal'
OUT = Path('ui-audit-results'); OUT.mkdir(exist_ok=True)
RESULTS = []
def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=45).decode(errors='replace')
assert adb('get-serialno').strip().startswith('emulator-'), 'Refusing to modify a physical device'
apk = next(Path('tested-apk').rglob('*.apk'))
with zipfile.ZipFile(apk) as z:
    dex_hash = hashlib.sha256(z.read('classes3.dex')).hexdigest()
assert dex_hash == '7eedcfb3edcca70f728a2387e49cba9343fd26988b6f72316b49f0afa97ab53a', 'Wrong application code'
adb('install', '-r', '-g', str(apk))
adb('shell', 'wm', 'size', '900x1200')
adb('shell', 'wm', 'density', '240')
d = u2.connect()
d.settings['wait_timeout'] = 8
d.set_input_ime(True)

def hierarchy():
    return ET.fromstring(d.dump_hierarchy())

def snapshot(name):
    try:
        d.screenshot(str(OUT / (name + '.png')))
        (OUT / (name + '.xml')).write_text(d.dump_hierarchy(), encoding='utf-8')
    except Exception as e:
        (OUT / (name + '-capture-error.txt')).write_text(str(e))

def fresh():
    # All clearing is confined to a brand-new emulator, never a user installation.
    d.app_stop(PKG); d.app_clear(PKG)
    adb('shell', 'pm', 'grant', PKG, 'android.permission.POST_NOTIFICATIONS')
    adb('shell', 'dumpsys', 'deviceidle', 'whitelist', '+' + PKG)
    d.app_start(PKG, 'com.sixpane.app.MainActivity', wait=True)
    if not d(description='Expand window 6').wait(timeout=30):
        snapshot('startup-blocked')
        raise RuntimeError('App did not expose six windows: ' + d.dump_hierarchy())

def label(text):
    q = d(textMatches='(?i)' + re.escape(text))
    if not q.wait(timeout=4):
        s = d(scrollable=True)
        if s.exists:
            s.scroll.to(textMatches='(?i)' + re.escape(text), max_swipes=4)
    q.click()
    time.sleep(.35)

def dots():
    d(text='⋮').click(); time.sleep(.3)

def pane_menu(n):
    d(descriptionStartsWith=f'Window {n} actions').click()

def edit(value):
    field = d(className='android.widget.EditText')
    field.click(); field.set_text(value)
    time.sleep(.35)

def pane_url(n, value):
    pane_menu(n); label('Enter URL'); edit(value); label('Open')
    time.sleep(.8)

def expand(n):
    d(description=f'Expand window {n}').click(); time.sleep(.4)

def restore():
    d(description='Restore six-window grid').click(); time.sleep(.4)

def color(n, value):
    pane_menu(n); label('Frame color'); label(value)

def set_filter(value):
    edit(value); d.hide_keyboard(); time.sleep(.5)

def visible_panes():
    values = []
    for n in hierarchy().iter('node'):
        m = re.match(r'Window (\d+) actions', n.get('content-desc', ''))
        b = list(map(int, re.findall(r'-?\d+', n.get('bounds', ''))))
        if m and len(b) == 4 and b[2] > 0 and b[0] < 900 and b[3] > 0 and b[1] < 1200:
            values.append(int(m.group(1)))
    return sorted(set(values))

def runtime_state():
    xml = adb('shell', 'run-as', PKG, 'cat', 'shared_prefs/sixpane-local-v2.xml')
    prefs = ET.fromstring(xml)
    manifest = json.loads(next(x.text for x in prefs if x.get('name') == 'manifest'))
    pane_colors = {x.get('name')[12:]: int(x.get('value')) for x in prefs if x.get('name', '').startswith('frame-color:')}
    return manifest, pane_colors

def current_panes():
    m, _ = runtime_state()
    return next(w['panes'] for w in m['spaces'] if w['id'] == m['current'])

def wait_for_url(n, predicate, timeout=12):
    deadline = time.time() + timeout
    value = ''
    while time.time() < deadline:
        value = current_panes()[n-1]['url']
        if predicate(value): return value
        time.sleep(.5)
    raise AssertionError('Observed URL: ' + value)

def run_case(name, fn):
    start = time.time()
    try:
        fresh()
        detail = fn()
        result = {'case': name, 'status': 'PASS', 'detail': detail}
    except AssertionError as e:
        result = {'case': name, 'status': 'FAIL', 'detail': str(e)}
    except Exception as e:
        result = {'case': name, 'status': 'BLOCKED', 'detail': str(e), 'traceback': traceback.format_exc()}
    snapshot(name)
    result['seconds'] = round(time.time() - start, 1)
    RESULTS.append(result)
    (OUT / 'results.json').write_text(json.dumps(RESULTS, indent=2))
    print(json.dumps(result), flush=True)

def address_from_pane():
    pane_url(1, 'coffee near me')
    url = wait_for_url(1, lambda s: s.startswith('https://www.google.com/search?') and 'coffee' in s)
    assert all(p['url'] == 'about:blank' for p in current_panes()[1:]), 'Another window navigated'
    return {'input': 'coffee near me', 'destination': url, 'other_windows_unchanged': True}

def expanded_address():
    expand(1)
    observed = []
    for text, predicate in [('weather', lambda u: 'google.com/search?' in u and 'weather' in u),
                            ('coffee near me', lambda u: 'google.com/search?' in u and 'coffee' in u),
                            ('example.com', lambda u: u.startswith('https://example.com'))]:
        edit(text); label('Go')
        url = wait_for_url(1, predicate)
        observed.append({'input': text, 'url': url, 'bar': d(className='android.widget.EditText').get_text()})
    assert d(text='⋮').exists, 'Expanded menu is missing'
    return observed

def unlimited_colors():
    dots()
    q = d(textStartsWith='Filter colors (')
    if not q.wait(timeout=4): d(scrollable=True).scroll.to(textStartsWith='Filter colors (', max_swipes=4)
    q.click()
    names = ['Graphite','Blue','Teal','Amber','Purple','Rose','Silver','Green']
    for name in names: label(name)
    checked = [n.get('text') for n in hierarchy().iter('node') if n.get('checked') == 'true']
    snapshot('colors-all-eight-attempted')
    label('Apply')
    chosen = runtime_state()[0].get('selectedFrameColors', [])
    assert len(chosen) == 8, f'Attempted eight colors; checked={checked}; saved={chosen}'
    return {'selected': chosen}

def expanded_color():
    expand(1)
    bounds = d(className='android.widget.EditText').info['bounds']
    pt = (bounds['left'] + 3, (bounds['top'] + bounds['bottom']) // 2)
    before = d.screenshot().convert('RGB').getpixel(pt)
    color(1, 'Blue'); time.sleep(.4)
    after = d.screenshot().convert('RGB').getpixel(pt)
    _, colors = runtime_state()
    assert current_panes()[0]['id'] in colors, 'Frame color did not save'
    assert before != after, f'Blue frame saved, but expanded URL field background stayed RGB {after}'
    return {'before': before, 'after': after}

def expanded_menu():
    expand(1); dots()
    menu = [{'text': n.get('text'), 'enabled': n.get('enabled')} for n in hierarchy().iter('node') if n.get('text')]
    direct_lock = any(x['text'] in ['Lock','Unlock','Lock window','Unlock window'] and x['enabled']=='true' for x in menu)
    direct_color = any(x['text'] in ['Frame color','Change frame color','Change color'] and x['enabled']=='true' for x in menu)
    assert direct_lock and direct_color, 'Expanded menu lacks direct window lock/color actions: ' + json.dumps(menu)
    return menu

def bookmarks():
    pane_url(1, 'https://example.com'); expand(1)
    d(description='Bookmark this page in this browser').click()
    assert d(text='★').exists, 'Star did not become saved'
    restore(); pane_url(2, 'https://example.com'); expand(2)
    assert d(text='☆').exists, 'Pane 1 bookmark leaked into pane 2 star'
    panes = current_panes()
    assert panes[0].get('paneBookmarks') and not panes[1].get('paneBookmarks'), 'Bookmark scope incorrect'
    d.app_stop(PKG); d.app_start(PKG, 'com.sixpane.app.MainActivity', wait=True)
    assert d(text='☆').wait(timeout=15), 'Pane 2 bookmark state changed after restart'
    restore(); expand(1)
    assert d(text='★').exists, 'Pane 1 bookmark did not survive restart'
    edit('https://www.google.com'); label('Go')
    wait_for_url(1, lambda u: 'google.com' in u)
    dots(); label('Bookmarks for this browser')
    saved = current_panes()[0]['paneBookmarks'][0]
    label(saved)
    url = wait_for_url(1, lambda u: u.startswith('https://example.com'))
    return {'saved_in_pane_1_only': True, 'survived_restart': True, 'reopened': url}

def domain_filter():
    inputs = ['https://www.google.com','https://example.com','https://notgoogle.com','https://example.com/?ref=google.com','https://docs.google.com']
    for n,url in enumerate(inputs,1): pane_url(n,url)
    before = [p['url'] for p in current_panes()]
    set_filter('google.com')
    actual = visible_panes()
    snapshot('domain-filter-active')
    assert actual == [1,5], f'Visible panes={actual}; URLs before filtering={before}'
    assert d(className='android.widget.EditText').get_text() == 'google.com', 'Filter text disappeared'
    d(text='×').click()
    assert visible_panes()==[1,2,3,4,5,6], 'Clear did not restore six windows'
    return {'visible_while_filtered': actual, 'restored_after_clear': True, 'urls': before}

def persistent_color_filter():
    color(1,'Blue'); color(2,'Rose'); color(3,'Blue')
    set_filter('Blue')
    assert visible_panes()==[1,3], 'Color filtering returned '+str(visible_panes())
    expand(1); assert d(text='⋮').exists; restore()
    assert visible_panes()==[1,3], 'Expansion/restoration lost color filter'
    d.app_stop(PKG); d.app_start(PKG,'com.sixpane.app.MainActivity',wait=True)
    assert d(description='Expand window 3').wait(timeout=20), 'Expected blue pane absent after restart'
    assert visible_panes()==[1,3], 'Restart lost color filter'
    assert d(className='android.widget.EditText').get_text()=='Blue', 'Filter text not restored'
    snapshot('color-filter-after-restart')
    set_filter('')
    assert visible_panes()==[1,2,3,4,5,6], 'Erasing filter did not restore windows'
    return {'visible':[1,3], 'survived_expand_restore':True, 'survived_restart':True, 'erasing_restores_all':True}

def batch_actions():
    dots(); label('Select multiple')
    d(description='Select browser')[0].click(); d(description='Select browser')[0].click()
    assert d(description='Window batch actions: 2 selected').exists, 'Selection count incorrect'
    dots(); label('Lock selected')
    locks=[p['locked'] for p in current_panes()]
    assert locks==[True,True,False,False,False,False], 'Wrong locked panes: '+str(locks)
    dots()
    close = d(text='Close selected')
    assert close.wait(timeout=4), 'Close row missing'
    ids_before=[p['id'] for p in current_panes()]
    close.click(); time.sleep(.5)
    assert not d(text='Close & Replace').exists, 'Locked selection opened close confirmation'
    assert [p['id'] for p in current_panes()]==ids_before, 'Locked panes were replaced'
    assert d(text='Unlock selected').exists, 'Disabled close did not retain menu'
    label('Unlock selected')
    dots(); label('Set selected frame color'); label('Blue')
    panes=current_panes();_, colors=runtime_state()
    changed=[i+1 for i,p in enumerate(panes) if p['id'] in colors]
    assert changed==[1,2], 'Batch color touched wrong windows: '+str(changed)
    assert all(not p['locked'] for p in panes), 'Batch unlock failed'
    snapshot('batch-two-colored')
    dots(); label('Exit selection')
    assert d(text='⋮').exists, 'Dots vanished after selection'
    return {'locked_then_unlocked':[1,2], 'colored':[1,2], 'others_unchanged':True, 'locked_close_disabled':True}

(OUT/'environment.txt').write_text(adb('shell','getprop','ro.build.fingerprint')+'\n'+adb('shell','dumpsys','webviewupdate')+'\nDEX '+dex_hash)
for name,fn in [('1a-pane-address-search',address_from_pane),('1b-expanded-address',expanded_address),
                ('2-unlimited-colors',unlimited_colors),('3a-expanded-bar-color',expanded_color),
                ('3b-expanded-menu',expanded_menu),('3c-bookmark-isolation',bookmarks),
                ('4-domain-filter',domain_filter),('5-persistent-color-filter',persistent_color_filter),
                ('6-batch-actions',batch_actions)]:
    run_case(name,fn)
(OUT/'logcat.txt').write_text(adb('logcat','-d','-t','1500'))
sys.exit(1 if any(r['status']!='PASS' for r in RESULTS) else 0)
