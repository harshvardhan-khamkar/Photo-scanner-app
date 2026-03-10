import urllib.request, json

url = 'http://127.0.0.1:8000/api/v1/albums/unlock/SANIKA'
try:
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as response:
        data = json.loads(response.read().decode())
        print(f"Album: {data['album']['name']}")
        for f in data['album']['frames']:
            sid = f.get('id', '?')
            start = f.get('start_time_ms', 0)
            dur = f.get('duration_ms', 0)
            print(f"  Frame {sid}: start={start}ms, duration={dur}ms  (Plays from {start/1000}s to {(start+dur)/1000}s)")
except Exception as e:
    print(f"Error calling API: {e}")
