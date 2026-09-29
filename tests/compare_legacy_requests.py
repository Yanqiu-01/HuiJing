#!/usr/bin/env python3
"""Compile archived clients unchanged and capture fixture-only HTTP requests."""
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import hashlib
import json
import os
import subprocess
import tempfile
import threading

ROOT = Path(__file__).resolve().parents[1]
JSON_JAR = Path(os.environ.get('TEST_JSON_JAR', '/workspace/test-deps/json-20240303.jar'))
ANDROID_JAR = ROOT / 'tools/android-35.jar'
VERSIONS = [('4.4.3', 'd0b421a'), ('4.4.9', '86155e5'), ('4.4.10', 'e4a9074'),
            ('4.4.11', '72f1966'), ('4.4.12', '8dc1267'), ('4.4.13', '94cc0d0')]
PACKAGE = 'app/src/main/java/app/inkbench/studio/'
PROBE = r'''import app.inkbench.studio.GatewayClient;
import java.lang.reflect.Method;
public final class LegacyImageRequestComparisonTest {
    public static void main(String[] args) throws Exception {
        String base = args[0];
        if (args[1].equals("success")) {
            for (String quality : new String[]{"standard", "high", "ultra"}) {
                for (String size : new String[]{"1024x1024", "1024x1536", "1536x1024"}) {
                    GatewayClient client = new GatewayClient(base, "fixture-only-key", 30);
                    if (client.generate("A red apple on a table", size, quality).size() != 1)
                        throw new AssertionError("missing fixture result");
                }
            }
        } else if (args[1].equals("retry")) {
            new GatewayClient(base, "fixture-only-key", 30)
                    .generate("A red apple on a table", "1024x1024", "standard");
        } else if (args[1].equals("classified-502")) {
            Method shouldRetry = GatewayClient.class.getDeclaredMethod("shouldRetry", GatewayClient.ApiException.class);
            shouldRetry.setAccessible(true);
            String[] types = {"upstream_error", "unsupported_response_format", "upstream_error"};
            String[] messages = {"upstream request failed", "upstream returned URL, not b64_json", "upstream returned no image resource"};
            for (int i = 0; i < types.length; i++) {
                boolean retry = (Boolean)shouldRetry.invoke(null, new GatewayClient.ApiException(502, types[i], messages[i]));
                System.out.println(types[i] + "|" + messages[i] + "|retry=" + retry);
            }
        }
    }
}
'''

records = []
fixture = {'mode': 'success', 'attempt': 0}
class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass
    def respond(self, status, value):
        data = json.dumps(value).encode() if isinstance(value, dict) else value
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)
    def do_POST(self):
        raw = self.rfile.read(int(self.headers.get('Content-Length', '0')))
        assert self.headers.get('Authorization') == 'Bearer fixture-only-key'
        records.append({'path': self.path, 'method': 'POST', 'body': json.loads(raw),
                        'serialized_body': raw.decode(),
                        'headers': {key: self.headers.get(key) for key in ['Accept', 'Content-Type', 'User-Agent']}})
        fixture['attempt'] += 1
        if fixture['mode'] == 'retry' and fixture['attempt'] == 1:
            self.respond(502, {'error': {'type': 'upstream_error', 'message': 'Bad Gateway'}})
        else:
            self.respond(200, {'data': [{'url': base + '/picture'}]})
    def do_GET(self):
        assert self.path == '/picture'
        self.respond(200, b'fixture-image-bytes')

def run(args, **kwargs):
    return subprocess.run(args, check=True, text=True, capture_output=True, cwd=ROOT, **kwargs).stdout

def diff(a, b):
    result = {}
    for section in ['path', 'method', 'headers']:
        if a[section] != b[section]:
            result[section] = {'old': a[section], 'new': b[section]}
    for key in sorted(set(a['body']) | set(b['body'])):
        x, y = a['body'].get(key), b['body'].get(key)
        if x != y:
            if key == 'prompt':
                result[key] = {'old_length': len(x), 'new_length': len(y),
                               'old_sha256': hashlib.sha256(x.encode()).hexdigest(),
                               'new_sha256': hashlib.sha256(y.encode()).hexdigest()}
            else:
                result[key] = {'old': x, 'new': y}
    return result

def main():
    global base
    assert JSON_JAR.is_file() and ANDROID_JAR.is_file(), 'test dependencies missing'
    build = ROOT / 'build'
    build.mkdir(exist_ok=True)
    out = Path(tempfile.mkdtemp(prefix='legacy-request-comparison-', dir=build))
    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    base = 'http://127.0.0.1:' + str(server.server_address[1])
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    captures = {}
    try:
        for label, rev in VERSIONS:
            src = out / rev / 'src'
            classes = out / rev / 'classes'
            src.mkdir(parents=True); classes.mkdir()
            java_files = []
            for name in ['GatewayClient.java', 'VisualPrompt.java', 'DiagnosticLog.java']:
                if not run(['git', 'ls-tree', '--name-only', rev, '--', PACKAGE + name]).strip():
                    continue
                archived = run(['git', 'show', rev + ':' + PACKAGE + name])
                path = src / name
                path.write_text(archived)
                java_files.append(str(path))
            probe = src / 'LegacyImageRequestComparisonTest.java'
            probe.write_text(PROBE)
            cp = os.pathsep.join([str(JSON_JAR), str(ANDROID_JAR)])
            run(['javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-cp', cp,
                 '-d', str(classes)] + java_files + [str(probe)])
            command = ['java', '-cp', str(classes) + os.pathsep + cp,
                       'LegacyImageRequestComparisonTest', base]
            records.clear(); fixture.update(mode='success', attempt=0)
            run(command + ['success'], timeout=45)
            assert len(records) == 9, (label, 'success request count')
            success = list(records)
            records.clear(); fixture.update(mode='retry', attempt=0)
            run(command + ['retry'], timeout=40)
            assert len(records) == 2, (label, 'ordinary 502 attempt count')
            assert records[0] == records[1], (label, 'retry changed request')
            for item in success + records:
                assert item['path'] == '/v1/images/generations'
                assert set(item['body']) == {'model', 'prompt', 'size', 'n', 'response_format'}
                assert item['body']['model'] == 'gpt-image-2'
                assert item['body']['response_format'] == 'b64_json'
                assert item['body']['n'] == 1
            classification = run(command + ['classified-502'], timeout=10).splitlines()
            captures[label] = {'commit': rev, 'success': success, 'retry': list(records),
                               'classified_502': classification}
            print(label + ': captured 9 initial requests + 2 ordinary-502 attempts', flush=True)
        summary = []
        current = captures['4.4.13']['success']
        for label, _ in VERSIONS[:-1]:
            for i, item in enumerate(captures[label]['success']):
                change = diff(item, current[i])
                if change:
                    summary.append({'old_version': label, 'quality': ['standard', 'high', 'ultra'][i // 3],
                                    'selected_size': ['1024x1024', '1024x1536', '1536x1024'][i % 3], 'differences': change})
        assert captures['4.4.12']['success'] == captures['4.4.13']['success'], '4.4.13 initial request regression'
        for i in range(3):
            assert captures['4.4.9']['success'][i] == current[i], 'standard first request changed since 4.4.9'
        for i in range(3, 9):
            assert set(diff(captures['4.4.9']['success'][i], current[i])) == {'size'}, 'unexpected 4.4.9 difference'
        report = {'scope': 'Archived unmodified clients, root gateway URL, default image model, fixture HTTP only; not deployed M365 or Android device.',
                  'captures': captures, 'differences_from_4_4_13': summary}
        (out / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
        print('PASS: 4.4.12 and 4.4.13 initial requests identical across all 9 settings')
        print('PASS: 4.4.9 and 4.4.13 standard requests identical; high/ultra differ only in size')
        print('PASS: ordinary 502 then success sends identical payload twice in all 6 versions')
        print('REPORT: ' + str(out / 'report.json'))
    finally:
        server.shutdown(); server.server_close(); thread.join()

if __name__ == '__main__':
    main()
