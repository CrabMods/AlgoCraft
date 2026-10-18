"""Verify the promo delivery: links, media properties, full decode, loudness and hashes.
Writes build/promo/delivery-receipt.json. ffmpeg/ffprobe run without windows."""
from pathlib import Path
import hashlib, json, os, re, subprocess, sys
from urllib.parse import unquote
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
MEDIA = ROOT / 'docs' / 'media'
RAW = 'https://raw.githubusercontent.com/CrabMods/AlgoCraft/1.21.1/docs/media/'
NOWIN = 0x08000000 if os.name == 'nt' else 0
checks = []
media_only = '--media-only' in sys.argv
def check(ok, label):
    if not ok: raise AssertionError(label)
    checks.append(label)
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def run(*a): return subprocess.run(list(a), capture_output=True, text=True, encoding='utf-8', errors='replace', creationflags=NOWIN)

docs = [ROOT / 'README.md', ROOT / 'docs/README.zh-CN.md', ROOT / 'docs/PROMO_MEDIA.md', ROOT / 'docs/PLAYER_GUIDE.md', *sorted((ROOT / 'docs').glob('CURSEFORGE.*.md'))]
for doc in docs:
    text = doc.read_text(encoding='utf-8')
    check('1.20.1' in text and '1.21.1' in text and 'NeoForge' in text
          and re.search(r'(?<!Neo)Forge\b', text) is not None,
          f'{doc.name}: both supported builds are documented')
    for target in re.findall(r'\]\(([^)\s]+)\)', text):
        if target.startswith(RAW):
            check((MEDIA / target[len(RAW):]).is_file(), f'{doc.name}: {target} exists locally')
        elif target.startswith(('http://', 'https://', '#')):
            continue
        else:
            path = (doc.parent / unquote(target.split('#')[0])).resolve()
            check(path.exists(), f'{doc.name}: {target} resolves')
    for stale in ['algocraft-trailer-zh', 'synthesize_promo_voice', 'hero-en.png', 'Huihui', 'SAPI']:
        check(stale not in text, f'{doc.name}: no stale reference to {stale}')

mp4 = MEDIA / 'algocraft-trailer-en-1080p.mp4'
info = json.loads(run('ffprobe', '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(mp4)).stdout)
v = next(s for s in info['streams'] if s['codec_type'] == 'video')
a = next(s for s in info['streams'] if s['codec_type'] == 'audio')
dur = float(info['format']['duration'])
check(v['codec_name'] == 'h264' and v['width'] == 1920 and v['height'] == 1080 and v['r_frame_rate'] == '30/1' and v['pix_fmt'] == 'yuv420p', 'video is 1080p30 H.264 yuv420p')
check(a['codec_name'] == 'aac' and int(a['sample_rate']) == 48000 and a['channels'] == 2, 'audio is 48 kHz stereo AAC')
check(120 <= dur <= 135, f'duration {dur:.2f}s')
check(mp4.stat().st_size < 100_000_000, 'video fits below the 100 MB repository file limit')
bank = [json.loads(p.read_text(encoding='utf-8')) for p in (ROOT / 'question_bank/official').glob('p*.json')]
counts = {d: sum(p['difficulty'].upper() == d for p in bank) for d in ('EASY', 'MEDIUM', 'HARD')}
check(len(bank) == 500 and counts == {'EASY': 172, 'MEDIUM': 205, 'HARD': 123}, 'advertised official bank counts match repository')
capture = json.loads((ROOT / 'build/promo/evidence/capture-receipt.json').read_text(encoding='utf-8'))
check(capture['headless'] and capture['muted'] and not capture['systemClipboardUsed'] and not capture['errors'], 'Web capture was headless, muted and clipboard isolated')
check(not capture['twoSumPassedBefore'] and '7/7' in capture['modal'] and capture['runOutput'].count('Result: PASS') == 2, 'recording contains real Run PASS x2 and Submit 7/7')
render_binding_path = ROOT / 'build/promo/evidence/trailer-render.json'
render_binding = json.loads(render_binding_path.read_text(encoding='utf-8'))
check(render_binding['status'] == 'source-bound-render-passed' and not render_binding['draft'], 'final render is bound to the accepted source')
check({p.relative_to(ROOT / 'promo/video/src').as_posix(): sha(p) for p in (ROOT / 'promo/video/src').rglob('*') if p.is_file()} == render_binding['expectedSourceHashes'], 'render source hashes match final video source')
titles = json.loads((ROOT / 'promo/video/public/data/bank.json').read_text(encoding='utf-8'))
check(len(titles['titles']) == 500 and all(re.fullmatch(r'[\x20-\x7e]+', p['t']) for p in titles['titles']), 'all title-wall assets use English')
zip_path = ROOT / 'build/distributions/algocraft-promo-0.1.0-beta.zip'
if not media_only:
    import zipfile
    with zipfile.ZipFile(zip_path) as z:
        check(z.testzip() is None, 'upload ZIP integrity')
        check(z.read('evidence/dual-version-render-binding.json') == render_binding_path.read_bytes(), 'ZIP includes the source binding for the dual-version render')
        for p in sorted(MEDIA.iterdir()):
            if p.is_file(): check(z.read('docs/media/' + p.name) == p.read_bytes(), f'ZIP includes final {p.name}')
        for name in ['README.md', 'docs/README.zh-CN.md', 'docs/PLAYER_GUIDE.md', 'docs/PROMO_MEDIA.md', 'docs/CURSEFORGE.en.md', 'docs/CURSEFORGE.en.html', 'docs/CURSEFORGE.zh-CN.md', 'docs/CURSEFORGE.zh-CN.html']:
            check(z.read(name) == (ROOT / name).read_bytes(), f'ZIP includes current {name}')
dec = run('ffmpeg', '-hide_banner', '-nostats', '-v', 'error', '-threads', '2', '-i', str(mp4), '-f', 'null', '-')
check(dec.returncode == 0 and not dec.stderr.strip(), 'full decode without errors')
loud = run('ffmpeg', '-hide_banner', '-nostats', '-threads', '2', '-i', str(mp4), '-vn', '-af', 'ebur128=peak=true', '-f', 'null', '-').stderr
I = float(re.findall(r'I:\s+(-?[\d.]+) LUFS', loud)[-1]); TP = float(re.findall(r'Peak:\s+(-?[\d.]+) dBFS', loud)[-1])
check(-17.0 <= I <= -15.0, f'integrated loudness {I} LUFS'); check(TP <= -0.9, f'true peak {TP} dBFS')

srt = (MEDIA / 'algocraft-trailer-en.srt').read_text(encoding='utf-8')
check('Forge 1.20.1' in srt and 'NeoForge 1.21.1' in srt,
      'English subtitles advertise both supported builds')
def secs(t): h, m, s = t.replace(',', '.').split(':'); return int(h) * 3600 + int(m) * 60 + float(s)
cues = re.findall(r'(\d\d:\d\d:\d\d,\d{3}) --> (\d\d:\d\d:\d\d,\d{3})', srt)
check(len(cues) >= 20, f'{len(cues)} subtitle cues')
prev = 0
for s, e in cues:
    check(secs(s) >= prev - 1e-3 and secs(e) > secs(s), f'cue {s} ordered'); prev = secs(e)
check(prev <= dur, 'subtitles end within the video')

images = {}
for p in sorted(MEDIA.iterdir()):
    if p.suffix.lower() in ('.jpg', '.png'):
        with Image.open(p) as im: images[p.name] = list(im.size)
check(images.get('hero.jpg') == [1920, 820] and images.get('trailer-poster.jpg') == [1920, 1080], 'hero and poster sizes')
receipt = {'checks': len(checks), 'video': {'duration': round(dur, 3), 'loudnessLUFS': I, 'truePeak': TP, 'bytes': mp4.stat().st_size},
           'bankCounts': counts, 'images': images, 'sha256': {p.name: sha(p) for p in sorted(MEDIA.iterdir()) if p.is_file()}}
if not media_only: receipt['zipSHA256'] = sha(zip_path)
out = ROOT / 'build' / 'promo' / ('media-receipt.json' if media_only else 'delivery-receipt.json')
out.write_text(json.dumps(receipt, indent=2), encoding='utf-8')
if not media_only: zip_path.with_suffix('.receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
print(json.dumps({k: receipt[k] for k in ('checks', 'video')}))
