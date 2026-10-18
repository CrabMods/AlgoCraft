"""Generate CurseForge description copy and local preview pages from the READMEs. Does not publish.

Store descriptions use hosted images and one inline YouTube player.
"""
from pathlib import Path
import re
import markdown

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs'
PREVIEW = ROOT / 'build' / 'promo' / 'pages'
PREVIEW.mkdir(parents=True, exist_ok=True)
RAW = 'https://raw.githubusercontent.com/CrabMods/AlgoCraft/1.21.1/docs/media/'
BLOB = 'https://github.com/CrabMods/AlgoCraft/blob/1.21.1/'
TRAILER = 'https://www.youtube.com/watch?v=DsKH72bj73o'
EMBED = 'https://www.youtube.com/embed/DsKH72bj73o?rel=0'
STYLE = ('html{color-scheme:dark}body{margin:0;background:#0d1117;color:#e6edf3;font:16px/1.65 -apple-system,"Segoe UI",system-ui,"Microsoft YaHei",sans-serif}'
         'main{max-width:900px;margin:auto;padding:32px 24px 80px;overflow-wrap:anywhere}h1,h2{line-height:1.25}h2{margin-top:44px;padding-bottom:8px;border-bottom:1px solid #30363d}'
         'a{color:#58d6a8}img{display:block;max-width:100%;height:auto;border-radius:10px;margin:14px 0}table{border-collapse:collapse;display:block;max-width:100%;overflow:auto}'
         'td,th{padding:8px 14px;border:1px solid #30363d}code{background:#161b22;padding:2px 6px;border-radius:5px;font-size:90%}hr{border:0;border-top:1px solid #30363d;margin:36px 0}'
         'li{margin:6px 0}@media(max-width:600px){main{padding:16px 16px 40px}body{font-size:15px}}')

def page(title, html, lang, base):
    return (f'<!doctype html><html lang="{lang}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
            f'<base href="{base}"><title>{title}</title><style>{STYLE}</style></head><body><main>{html}</main></body></html>')

def store_copy(md, media_prefix, doc_prefix):
    md = md.split('\n---\n')[0]  # drop the repository footer
    md = re.sub(r'^▶ .*\n', '', md, flags=re.M)
    md = md.replace(f'({media_prefix}', f'({RAW}')
    md = re.sub(r'\]\((?!https?://)([^)]+\.md)\)', lambda m: f']({BLOB}{doc_prefix}{m.group(1)})', md)
    # GitHub retains its clickable poster; stores can embed the actual player.
    start = md.find('[![')
    end = md.find('\n## ', start)
    if start >= 0 and end > start:
        link_text = 'Watch on YouTube' if media_prefix == 'docs/media/' else '在 YouTube 上观看'
        player = (f'<iframe src="{EMBED}" width="100%" '
                  'style="aspect-ratio:1.777778;width:100%;border:0" '
                  'frameborder="0" allowfullscreen></iframe>\n\n'
                  f'[{link_text}]({TRAILER})\n')
        md = md[:start] + player + md[end:]
    credit = ('\n---\n\nTrailer music: “Voxel Revolution” by Kevin MacLeod (incompetech.com), CC BY 4.0. Sound effects by Kenney (CC0).\n'
              if 'Trailer music' in md or media_prefix == 'docs/media/' else
              '\n---\n\n预告片音乐：“Voxel Revolution”，Kevin MacLeod（incompetech.com），CC BY 4.0。音效来自 Kenney（CC0）。\n')
    return md.rstrip() + '\n' + credit

for lang, src, media_prefix, doc_prefix in [('en', ROOT / 'README.md', 'docs/media/', ''), ('zh-CN', DOCS / 'README.zh-CN.md', 'media/', 'docs/')]:
    md = src.read_text(encoding='utf-8')
    readme_html = markdown.markdown(md, extensions=['tables', 'fenced_code'])
    base = (ROOT if lang == 'en' else DOCS).as_uri() + '/'
    (PREVIEW / f'readme-{lang}.html').write_text(page(f'AlgoCraft README {lang}', readme_html, lang, base), encoding='utf-8')
    store = store_copy(md, media_prefix, doc_prefix)
    (DOCS / f'CURSEFORGE.{lang}.md').write_text(store, encoding='utf-8')
    modrinth = store.replace('style="aspect-ratio:1.777778;width:100%;border:0"', 'height="420"')
    (DOCS / f'MODRINTH.{lang}.md').write_text(modrinth, encoding='utf-8')
    store_html = markdown.markdown(store, extensions=['tables', 'fenced_code'])
    (DOCS / f'CURSEFORGE.{lang}.html').write_text('<article>\n' + store_html + '\n</article>\n', encoding='utf-8')
    # Local preview of the store copy: map the raw URLs back to the local files.
    local = store_html.replace(RAW, (DOCS / 'media').as_uri() + '/')
    (PREVIEW / f'curseforge-{lang}.html').write_text(page(f'AlgoCraft CurseForge {lang}', local, lang, base), encoding='utf-8')
print('pages', sorted(p.name for p in PREVIEW.glob('*.html')))
