import urllib.request
import urllib.error
import concurrent.futures
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
        with urllib.request.urlopen(req, timeout=5, context=ctx) as resp:
            if resp.status == 200:
                return True
    except Exception:
        pass
    return False

def main():
    urls = [
        "https://raw.githubusercontent.com/wlhacker/iptv/main/iptv.txt",
        "https://raw.githubusercontent.com/ssili126/tv/main/itvlist.txt"
    ]
    data_lines = []
    for source_url in urls:
        try:
            ctx = ssl.create_default_context()
            ctx.check_hostname = False
            ctx.verify_mode = ssl.CERT_NONE
            req = urllib.request.Request(source_url)
            text = urllib.request.urlopen(req, context=ctx).read().decode('utf-8')
            data_lines.extend(text.splitlines())
        except Exception as e:
            print("Failed to download", source_url, e)

    channels_by_group = {}
    current_group = ""
    for line in data_lines:
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
                # Filter out obvious local IPs
                urls_list = [u for u in url.split('#') if u.startswith('http') and not u.startswith('http://192.168.') and not u.startswith('http://10.')]
                if name not in channels_by_group[current_group]:
                    channels_by_group[current_group][name] = []
                channels_by_group[current_group][name].extend(urls_list)

    executor = concurrent.futures.ThreadPoolExecutor(max_workers=100)
    future_to_url = {}

    for group, channels in channels_by_group.items():
        if '央视' not in group and '卫视' not in group and 'CCTV' not in group:
            continue
        for name, urls_list in channels.items():
            for url in urls_list:
                future = executor.submit(check_url, url)
                future_to_url[future] = (group, name, url)

    tested_channels = {}
    success_count = 0
    for future in concurrent.futures.as_completed(future_to_url):
        group, name, url = future_to_url[future]
        try:
            is_valid = future.result()
        except Exception:
            is_valid = False
            
        if is_valid:
            success_count += 1
            if group not in tested_channels:
                tested_channels[group] = {}
            if name not in tested_channels[group]:
                tested_channels[group][name] = []
            tested_channels[group][name].append(url)
            print(f"Working: {name} -> {url}")

    print(f"Found {success_count} globally working channels.")

if __name__ == '__main__':
    main()
