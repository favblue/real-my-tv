import urllib.request
import urllib.error
import ssl
import os

os.environ['http_proxy'] = ''
os.environ['https_proxy'] = ''

url = "http://112.246.3.26:8088/cntv/live1/CCTV-1/CCTV-1.m3u8" # usually a CCTV URL
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
req = urllib.request.Request(url, headers={'User-Agent': 'VLC/3.0.9 LibVLC/3.0.9'})
try:
    with urllib.request.urlopen(req, timeout=5, context=ctx) as resp:
        print("Status:", resp.status)
except Exception as e:
    print("Exception:", e)
