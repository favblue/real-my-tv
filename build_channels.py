import urllib.request
import urllib.error
import concurrent.futures
import time
import ssl
import os

os.environ['http_proxy'] = ''
os.environ['https_proxy'] = ''

def check_url(url):
    try:
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        req = urllib.request.Request(url, headers={'User-Agent': 'VLC/3.0.9 LibVLC/3.0.9'})
        with urllib.request.urlopen(req, timeout=4, context=ctx) as resp:
            return resp.status == 200
    except Exception:
        return False

def main():
    source_url = "https://raw.githubusercontent.com/wlhacker/iptv/main/iptv.txt"
    try:
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        req = urllib.request.Request(source_url)
        data = urllib.request.urlopen(req, context=ctx).read().decode('utf-8').splitlines()
    except Exception as e:
        print("Failed to download source:", e)
        return

    output_lines = []
    current_group = ""
    
    channels_by_group = {}
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
                # Split multiple URLs separated by '#'
                urls = url.split('#')
                if name not in channels_by_group[current_group]:
                    channels_by_group[current_group][name] = []
                channels_by_group[current_group][name].extend(urls)

    tested_channels = {}
    executor = concurrent.futures.ThreadPoolExecutor(max_workers=50)
    future_to_url = {}

    for group, channels in channels_by_group.items():
        for name, urls in channels.items():
            for url in urls:
                future = executor.submit(check_url, url)
                future_to_url[future] = (group, name, url)

    for future in concurrent.futures.as_completed(future_to_url):
        group, name, url = future_to_url[future]
        try:
            is_valid = future.result()
        except Exception:
            is_valid = False
            
        if is_valid:
            if group not in tested_channels:
                tested_channels[group] = {}
            if name not in tested_channels[group]:
                tested_channels[group][name] = []
            tested_channels[group][name].append(url)

    out = []
    # Preserve order of groups and channels
    for group, channels in channels_by_group.items():
        if group in tested_channels and tested_channels[group]:
            out.append(f"{group},#genre#")
            for name in channels:
                if name in tested_channels[group] and tested_channels[group][name]:
                    urls = list(dict.fromkeys(tested_channels[group][name]))
                    out.append(f"{name},{'#'.join(urls)}")

    with open("app/src/main/res/raw/channels.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(out))
        f.write("\n")

    print(f"Generated channels.txt with {sum(len(c) for c in tested_channels.values())} channels.")

if __name__ == '__main__':
    main()
