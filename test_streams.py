import urllib.request
import urllib.error
import time
import os
import socket

# Disable proxy
os.environ['http_proxy'] = ''
os.environ['https_proxy'] = ''
os.environ['HTTP_PROXY'] = ''
os.environ['HTTPS_PROXY'] = ''
os.environ['ALL_PROXY'] = ''
os.environ['all_proxy'] = ''

def test_url(url, timeout=5):
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'VLC/3.0.9 LibVLC/3.0.9'})
        with urllib.request.urlopen(req, timeout=timeout) as response:
            if response.status == 200:
                return True
    except Exception as e:
        pass
    return False

# Common streams (simplified for example)
# To find good ones, we should use a known good public iptv source.
# Let's download a popular txt/m3u file from github.
source_url = "https://raw.githubusercontent.com/wlhacker/iptv/main/iptv3.txt"
# If this source is not txt formatted, we will parse it. Wait, the user mentioned ipv4 or ipv6. Let's find one that has domestic IP streams.
# iptv3.txt might not exist, let's try a known source or just use a few known urls.

