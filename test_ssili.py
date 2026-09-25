import urllib.request
import os
import ssl
os.environ['http_proxy'] = ''
os.environ['https_proxy'] = ''
url = 'http://101.66.194.125:9901/tsfile/live/0001_1.m3u8?key=txiptv&playlive=0&authid=0'
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
req = urllib.request.Request(url, headers={'User-Agent': 'VLC/3.0.9 LibVLC/3.0.9'})
try:
    print(urllib.request.urlopen(req, context=ctx, timeout=3).getcode())
except Exception as e:
    print(e)
