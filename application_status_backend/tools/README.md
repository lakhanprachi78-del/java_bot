# Contract comparison

`contract_test.py` sends the same representative requests to the legacy Python
service and the Java service, then compares the status code and decoded JSON
response. It exits with code `1` when a case differs or either service cannot
be reached.

Run HTTP contract checks after starting both services:

```powershell
python tools\contract_test.py --mode http --python-url http://127.0.0.1:5000 --java-url http://127.0.0.1:8080
```

The Angular client currently uses WebSocket envelopes on `/ws`. To compare that
live protocol instead, install the optional client and run:

```powershell
python -m pip install websocket-client
python tools\contract_test.py --mode websocket --python-url http://127.0.0.1:5000 --java-url http://127.0.0.1:8080
```

The default run covers `session-chandan`, `session-prachi`, and `session-admin`.
Repeat `--session-id` to choose a different set. Use `--application-id` and
`--status` to select records that exist in both environments. The default
application ID is only a placeholder.