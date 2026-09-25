import urllib.request
import os
import ssl

os.environ['http_proxy'] = ''
os.environ['https_proxy'] = ''
url = 'https://raw.githubusercontent.com/ssili126/tv/main/itvlist.txt'

try:
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    req = urllib.request.Request(url)
    data = urllib.request.urlopen(req, context=ctx).read().decode('utf-8').splitlines()
except Exception as e:
    print("Failed", e)
    exit(1)

channels_by_group = {}
current_group = ""

for line in data:
    line = line.strip()
    if not line: continue
    if '#genre#' in line:
        current_group = line.split(',')[0]
        if current_group not in channels_by_group:
            channels_by_group[current_group] = {}
    else:
        if not current_group: continue
        parts = line.split(',')
        if len(parts) == 2:
            name, url = parts[0], parts[1]
            if name not in channels_by_group[current_group]:
                channels_by_group[current_group][name] = []
            channels_by_group[current_group][name].append(url)

out = []
for group, channels in channels_by_group.items():
    if not channels: continue
    out.append(f"{group},#genre#")
    for name, urls in channels.items():
        # filter out anything not http or https or rtp (wait, user doesn't have rtp, let's filter out rtp)
        valid_urls = []
        for u in urls:
            for piece in u.split('#'):
                if piece.startswith('http'):
                    valid_urls.append(piece)
        if valid_urls:
            unique_urls = list(dict.fromkeys(valid_urls))
            out.append(f"{name},{'#'.join(unique_urls)}")

with open("app/src/main/res/raw/channels.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(out))
    f.write("\n")
print("Done formatting.")
